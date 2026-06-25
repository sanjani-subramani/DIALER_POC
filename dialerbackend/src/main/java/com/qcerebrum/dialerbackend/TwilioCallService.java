package com.qcerebrum.dialerbackend;

import com.twilio.base.ResourceSet;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Recording;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Twiml;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TwilioCallService {

    private final CallLogRepository callLogRepo;
    private final AgentRepository agentRepo;

    @Value("${twilio.from-number}")
    private String fromNumber;

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

        String twimlXml = "<Response><Say>Connecting you to the customer now.</Say>" +
            "<Dial record=\"record-from-answer\">" + customerNumber + "</Dial></Response>";

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
            return callLogRepo.save(log);
        }

        return log;
    }
}
