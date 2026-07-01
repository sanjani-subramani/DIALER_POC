package com.qcerebrum.dialerbackend;

import com.twilio.base.ResourceSet;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Recording;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Twiml;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Service
public class TwilioCallService {

    private static final Logger log = Logger.getLogger(TwilioCallService.class.getName());

    private final CallLogRepository callLogRepo;
    private final AgentRepository agentRepo;

    @Value("${twilio.from-number}")
    private String fromNumber;

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${recording.storage.path:C:/Users/sanja/dialer-recordings/}")
    private String recordingStoragePath;

    public TwilioCallService(CallLogRepository callLogRepo, AgentRepository agentRepo) {
        this.callLogRepo = callLogRepo;
        this.agentRepo = agentRepo;
    }

    public CallLog placeRecordedCall(String toNumber) {
        CallLog log = new CallLog();
        log.setAgentId("agent1");
        log.setCustomerNumber(toNumber);
        log.setStatus("DIALING");
        log.setStartTime(LocalDateTime.now());
        callLogRepo.save(log);

        String twimlXml = "<Response>" +
            "<Say>This is a live test call from the Q Cerebrum dialer proof of concept. " +
            "This call is being recorded.</Say>" +
            "<Pause length=\"20\"/>" +
            "<Say>Thank you, goodbye.</Say>" +
            "</Response>";

        Call call = Call.creator(
            new PhoneNumber(toNumber),
            new PhoneNumber(fromNumber),
            new Twiml(twimlXml)
        ).setRecord(true).create();

        log.setProviderCallSid(call.getSid());
        return callLogRepo.save(log);
    }

    public CallLog placeBridgeCall(String agentId, String customerNumber) {
        Agent agent = agentRepo.findById(agentId).orElse(null);
        if (agent == null || agent.getAgentPhoneNumber() == null || agent.getAgentPhoneNumber().isBlank()) {
            return null;
        }

        String agentPhone = agent.getAgentPhoneNumber();
        String callerId = (agentPhone != null && !agentPhone.isBlank()) ? agentPhone : fromNumber;
        log.info("placeBridgeCall: using callerId=" + callerId + " for customer leg to " + customerNumber);

        String twimlXml = "<Response><Say>Connecting you to the customer now.</Say>" +
                "<Dial record=\"record-from-answer\" callerId=\"" + fromNumber + "\">" + customerNumber + "</Dial></Response>";

        CallLog log = new CallLog();
        log.setAgentId(agentId);
        log.setCustomerNumber(customerNumber);
        log.setStatus("DIALING");
        log.setStartTime(LocalDateTime.now());
        callLogRepo.save(log);

        Call call = Call.creator(
            new PhoneNumber(agent.getAgentPhoneNumber()),
            new PhoneNumber(fromNumber),
            new Twiml(twimlXml)
        ).create();

        log.setProviderCallSid(call.getSid());
        return callLogRepo.save(log);
    }

    public CallLog refreshCallStatus(Long callLogId) {
        CallLog callLog = callLogRepo.findById(callLogId)
            .orElseThrow(() -> new RuntimeException("CallLog not found: " + callLogId));

        if (callLog.getProviderCallSid() == null) {
            return callLog;
        }

        // Fetch the agent-leg (parent call)
        Call agentLeg = Call.fetcher(callLog.getProviderCallSid()).fetch();

        // Fetch the customer (child) leg — the leg dialed by the <Dial> verb
        Call customerLeg = null;
        ResourceSet<Call> childCalls = Call.reader()
            .setParentCallSid(callLog.getProviderCallSid())
            .read();
        for (Call c : childCalls) {
            customerLeg = c;
            break;
        }

        // Prefer customer leg status since the goal is to show whether the customer picked up
        String rawStatus = customerLeg != null
            ? customerLeg.getStatus().toString().toLowerCase()
            : agentLeg.getStatus().toString().toLowerCase();

        String friendlyStatus = toFriendlyStatus(rawStatus);
        callLog.setStatus(friendlyStatus);

        if (isFinalStatus(friendlyStatus) && callLog.getEndTime() == null) {
            callLog.setEndTime(LocalDateTime.now());
        }

        return callLogRepo.save(callLog);
    }

    private String toFriendlyStatus(String twilioStatus) {
        return switch (twilioStatus) {
            case "queued", "initiated" -> "DIALING";
            case "ringing" -> "RINGING";
            case "in-progress" -> "IN_PROGRESS";
            case "completed" -> "COMPLETED";
            case "no-answer" -> "NO_ANSWER";
            case "busy" -> "BUSY";
            case "failed", "canceled" -> "FAILED";
            default -> "DIALING";
        };
    }

    static boolean isFinalStatus(String friendlyStatus) {
        return switch (friendlyStatus) {
            case "COMPLETED", "NO_ANSWER", "BUSY", "FAILED" -> true;
            default -> false;
        };
    }

    public CallLog checkAndAttachRecording(Long callLogId) {
        CallLog log = callLogRepo.findById(callLogId)
            .orElseThrow(() -> new RuntimeException("CallLog not found: " + callLogId));

        if (log.getProviderCallSid() == null) {
            return log;
        }

        ResourceSet<Recording> recordings = Recording.reader()
            .setCallSid(log.getProviderCallSid())
            .read();

        for (Recording rec : recordings) {
            log.setTwilioRecordingSid(rec.getSid());
            log.setStatus("COMPLETED");
            log.setEndTime(LocalDateTime.now());
            log.setRecordingUrl("http://localhost:8080/api/recordings/" + callLogId);

            // ADDITIVE: also save a local .mp3 copy. Failure here must never break the
            // existing recordingUrl flow above, so it's fully self-contained and swallows errors.
            downloadAndSaveRecordingLocally(log, rec.getSid());

            return callLogRepo.save(log);
        }

        return log;
    }

    private void downloadAndSaveRecordingLocally(CallLog callLog, String recordingSid) {
        try {
            String twilioMp3Url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid
                + "/Recordings/" + recordingSid + ".mp3";

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(accountSid, authToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.exchange(twilioMp3Url, HttpMethod.GET, entity, byte[].class);

            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("Empty response body when downloading recording from Twilio");
            }

            Path dir = Paths.get(recordingStoragePath);
            Files.createDirectories(dir);
            Path filePath = dir.resolve("call_" + callLog.getId() + ".mp3");
            Files.write(filePath, bytes);

            callLog.setLocalFilePath(filePath.toAbsolutePath().toString());
        } catch (Exception e) {
            TwilioCallService.log.warning("Local recording save failed for callLogId=" + callLog.getId()
                + ", recordingSid=" + recordingSid + ": " + e.getMessage());
        }
    }
}
