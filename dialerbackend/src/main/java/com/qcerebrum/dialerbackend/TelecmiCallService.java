package com.qcerebrum.dialerbackend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * TeleCMI TelephonyProvider implementation using the CHUB Click-To-Call Admin API
 * (POST /webrtc/click2call) — this account is a CHUB business-phone-system account,
 * not a PIOPIY developer account, so the earlier PCMO/make_call implementation does not apply.
 *
 * TODO before this provider is fully functional:
 *   - placeRecordedCall (non-bridge outbound call)
 *   - Webhook handling for callback routing (call status/events pushed by TeleCMI)
 */
public class TelecmiCallService implements TelephonyProvider {

    private static final Logger log = Logger.getLogger(TelecmiCallService.class.getName());

    private static final String NOT_CONFIGURED_MESSAGE =
        "TeleCMI provider not yet configured — no account/API credentials available.";

    private final CallLogRepository callLogRepo;
    private final AgentRepository agentRepo;

    @Value("${telecmi.appid}")
    private String appId;

    @Value("${telecmi.user-id}")
    private String userId;

    @Value("${telecmi.secret}")
    private String secret;

    @Value("${telecmi.from-number}")
    private String fromNumber;

    @Value("${telecmi.base-url}")
    private String baseUrl;

    @Value("${recording.storage.path:C:/Users/sanja/dialer-recordings/}")
    private String recordingStoragePath;

    public TelecmiCallService(CallLogRepository callLogRepo, AgentRepository agentRepo) {
        this.callLogRepo = callLogRepo;
        this.agentRepo = agentRepo;
    }

    @Override
    public CallLog placeRecordedCall(String toNumber) {
        log.warning(NOT_CONFIGURED_MESSAGE);
        throw new UnsupportedOperationException(NOT_CONFIGURED_MESSAGE);
    }

    @Override
    public CallLog placeBridgeCall(String agentId, String customerNumber) {
        // Agent lookup is kept for CallLog record-keeping only. In CHUB's click2call flow the
        // agent leg is actually rung via telecmi.user-id (the TeleCMI user tied to a registered
        // phone), not via the agent's stored phone number — POC limitation: one TeleCMI user
        // covers all agents until CHUB user IDs are mapped per-agent.
        Agent agent = agentRepo.findById(agentId).orElse(null);
        if (agent == null || agent.getAgentPhoneNumber() == null || agent.getAgentPhoneNumber().isBlank()) {
            return null;
        }

        CallLog callLog = new CallLog();
        callLog.setAgentId(agentId);
        callLog.setCustomerNumber(customerNumber);
        callLog.setStatus("DIALING");
        callLog.setStartTime(LocalDateTime.now());
        callLogRepo.save(callLog);

        String url = baseUrl + "/webrtc/click2call";

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("user_id", userId);
            requestBody.put("secret", secret);
            requestBody.put("to", Long.parseLong(normalizeNumber(customerNumber)));
            requestBody.put("webrtc", false);
            requestBody.put("followme", true);
            requestBody.put("callerid", Long.parseLong(normalizeNumber(fromNumber)));

            Map<String, Object> loggableBody = new LinkedHashMap<>(requestBody);
            loggableBody.put("secret", maskSecret(secret));
            log.info("TeleCMI click2call request -> URL=" + url + ", body=" + loggableBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response;
            try {
                response = restTemplate.postForEntity(url, entity, Map.class);
            } catch (HttpStatusCodeException httpEx) {
                log.log(Level.SEVERE, "TeleCMI click2call HTTP error for agentId=" + agentId
                    + ", customerNumber=" + customerNumber
                    + ": status=" + httpEx.getStatusCode()
                    + ", body=" + httpEx.getResponseBodyAsString(), httpEx);
                callLog.setStatus("FAILED");
                return callLogRepo.save(callLog);
            }

            Map<?, ?> responseBody = response.getBody();
            log.info("TeleCMI click2call response <- status=" + response.getStatusCode()
                + ", body=" + responseBody);

            // Confirmed response shape (verified against TeleCMI support's working Postman call):
            // { "code": 200, "msg": "Call initiated", "request_id": "..." }
            Object codeObj = responseBody != null ? responseBody.get("code") : null;
            boolean codeIndicatesSuccess = codeObj != null && "200".equals(String.valueOf(codeObj));

            if (!response.getStatusCode().is2xxSuccessful() || !codeIndicatesSuccess) {
                log.severe("TeleCMI click2call reported failure for agentId=" + agentId
                    + ", customerNumber=" + customerNumber
                    + " — status=" + response.getStatusCode() + ", body=" + responseBody);
                callLog.setStatus("FAILED");
                return callLogRepo.save(callLog);
            }

            Object requestIdObj = responseBody.get("request_id");
            String requestId = requestIdObj != null ? requestIdObj.toString() : null;

            callLog.setProviderCallSid(requestId);
            return callLogRepo.save(callLog);
        } catch (Exception e) {
            log.log(Level.SEVERE, "TeleCMI placeBridgeCall failed for agentId=" + agentId
                + ", customerNumber=" + customerNumber + ": " + e, e);
            callLog.setStatus("FAILED");
            return callLogRepo.save(callLog);
        }
    }

    @Override
    public CallLog refreshCallStatus(Long callLogId) {
        CallLog callLog = callLogRepo.findById(callLogId)
            .orElseThrow(() -> new RuntimeException("CallLog not found: " + callLogId));

        String status = callLog.getStatus();
        boolean inProgressStatus = "DIALING".equals(status) || "RINGING".equals(status) || "IN_PROGRESS".equals(status);

        if (!inProgressStatus || callLog.getStartTime() == null) {
            return callLog;
        }

        boolean olderThanTwoMinutes = callLog.getStartTime().isBefore(LocalDateTime.now().minusMinutes(2));
        if (!olderThanTwoMinutes) {
            return callLog;
        }

        // A recording appearing in TeleCMI's CDR is our only signal that the call finished —
        // checkAndAttachRecording moves status to COMPLETED on a match and otherwise leaves it as-is.
        return checkAndAttachRecording(callLogId);
    }

    @Override
    public CallLog checkAndAttachRecording(Long callLogId) {
        CallLog callLog = callLogRepo.findById(callLogId)
            .orElseThrow(() -> new RuntimeException("CallLog not found: " + callLogId));

        if (callLog.getLocalFilePath() != null && !callLog.getLocalFilePath().isBlank()) {
            return callLog;
        }

        if (callLog.getStartTime() == null || callLog.getCustomerNumber() == null) {
            return callLog;
        }

        long startEpochMillis = callLog.getStartTime().minusMinutes(10).toInstant(ZoneOffset.UTC).toEpochMilli();
        long endEpochMillis = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        String targetNumber = normalizeNumber(callLog.getCustomerNumber());
        long targetStartMillis = callLog.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();

        // Follow-me click2call legs get classified by TeleCMI as an INCOMING answered call (the
        // agent's phone "picks up" first), so out_answered alone often returns count=0. Try the
        // outbound CDR endpoint first, then fall back to the incoming/answered endpoint.
        Map<?, ?> bestMatch = null;
        String matchedEndpoint = null;

        Map<?, ?> outAnsweredBody = queryCdrEndpoint("/out_answered", startEpochMillis, endEpochMillis, callLogId);
        if (outAnsweredBody != null && outAnsweredBody.get("cdr") instanceof List<?> outCdrList) {
            bestMatch = selectBestCdrMatch(outCdrList, targetNumber, targetStartMillis);
            if (bestMatch != null) {
                matchedEndpoint = "out_answered";
            }
        }

        if (bestMatch == null) {
            Map<?, ?> answeredBody = queryCdrEndpoint("/answered", startEpochMillis, endEpochMillis, callLogId);
            if (answeredBody != null && answeredBody.get("cdr") instanceof List<?> inCdrList) {
                bestMatch = selectBestCdrMatch(inCdrList, targetNumber, targetStartMillis);
                if (bestMatch != null) {
                    matchedEndpoint = "answered";
                }
            }
        }

        if (bestMatch == null) {
            log.info("TeleCMI: no CDR record matched customerNumber=" + callLog.getCustomerNumber()
                + " for callLogId=" + callLogId + " on /out_answered or /answered — recording may not be ready yet.");
            return callLog;
        }

        log.info("TeleCMI: matched CDR record for callLogId=" + callLogId + " via /" + matchedEndpoint + " endpoint.");

        Object filenameObj = bestMatch.get("filename");
        Object cmiuidObj = bestMatch.get("cmiuid");
        String filename = filenameObj != null ? filenameObj.toString() : null;
        String cmiuid = cmiuidObj != null ? cmiuidObj.toString() : null;

        if (filename == null || filename.isBlank()) {
            log.info("TeleCMI: matched CDR record has no filename for callLogId=" + callLogId);
            return callLog;
        }

        callLog.setTwilioRecordingSid(cmiuid);
        callLog.setStatus("COMPLETED");
        if (callLog.getEndTime() == null) {
            callLog.setEndTime(computeEndTimeFromCdr(bestMatch));
        }
        callLog.setRecordingUrl("/api/recordings/local/" + callLogId);

        // ADDITIVE: also save a local .mp3 copy, same pattern as TwilioCallService. Failure here
        // must never break the status/recordingUrl update above, so it's fully self-contained
        // and swallows errors.
        downloadAndSaveRecordingLocally(callLog, filename);

        return callLogRepo.save(callLog);
    }

    private Map<?, ?> queryCdrEndpoint(String path, long startEpochMillis, long endEpochMillis, Long callLogId) {
        String url = baseUrl + path;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("appid", Long.parseLong(appId));
        requestBody.put("secret", secret);
        requestBody.put("from", startEpochMillis);
        requestBody.put("to", endEpochMillis);
        requestBody.put("page", 1);
        requestBody.put("limit", 20);

        Map<String, Object> loggableBody = new LinkedHashMap<>(requestBody);
        loggableBody.put("secret", maskSecret(secret));
        log.info("TeleCMI " + path + " request -> URL=" + url + ", body=" + loggableBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(url, entity, Map.class);
        } catch (HttpStatusCodeException httpEx) {
            log.log(Level.SEVERE, "TeleCMI " + path + " HTTP error for callLogId=" + callLogId
                + ": status=" + httpEx.getStatusCode() + ", body=" + httpEx.getResponseBodyAsString(), httpEx);
            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "TeleCMI " + path + " failed for callLogId=" + callLogId + ": " + e, e);
            return null;
        }

        Map<?, ?> responseBody = response.getBody();
        log.info("TeleCMI " + path + " response <- status=" + response.getStatusCode() + ", body=" + responseBody);

        Object codeObj = responseBody != null ? responseBody.get("code") : null;
        boolean codeIndicatesSuccess = codeObj != null && "200".equals(String.valueOf(codeObj));
        if (!response.getStatusCode().is2xxSuccessful() || !codeIndicatesSuccess) {
            log.info("TeleCMI " + path + " did not return success for callLogId=" + callLogId
                + " — body=" + responseBody);
            return null;
        }

        return responseBody;
    }

    private Map<?, ?> selectBestCdrMatch(List<?> cdrList, String targetNumber, long targetStartMillis) {
        Map<?, ?> best = null;
        boolean bestAgentMatch = false;
        long bestDiff = Long.MAX_VALUE;

        for (Object item : cdrList) {
            if (!(item instanceof Map<?, ?> cdr)) {
                continue;
            }

            Object fromObj = cdr.get("from");
            Object toObj = cdr.get("to");
            boolean fromMatches = fromObj != null && targetNumber.equals(normalizeNumber(fromObj.toString()));
            boolean toMatches = toObj != null && targetNumber.equals(normalizeNumber(toObj.toString()));
            if (!fromMatches && !toMatches) {
                continue;
            }

            Object agentObj = cdr.get("agent");
            boolean agentMatches = agentObj != null && userId.equals(agentObj.toString());

            Object timeObj = cdr.get("time");
            long cdrTime = timeObj != null ? Long.parseLong(timeObj.toString()) : Long.MAX_VALUE;
            long diff = Math.abs(cdrTime - targetStartMillis);

            boolean better = best == null
                || (agentMatches && !bestAgentMatch)
                || (agentMatches == bestAgentMatch && diff < bestDiff);

            if (better) {
                best = cdr;
                bestAgentMatch = agentMatches;
                bestDiff = diff;
            }
        }

        return best;
    }

    private LocalDateTime computeEndTimeFromCdr(Map<?, ?> cdr) {
        Object timeObj = cdr.get("time");
        if (timeObj == null) {
            return LocalDateTime.now();
        }
        long cdrTimeMillis = parseLongSafely(timeObj, 0L);
        long billedsec = parseLongSafely(cdr.get("billedsec"), 0L);
        long endMillis = cdrTimeMillis + billedsec * 1000L;
        return Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    // TeleCMI's CDR endpoints return some numeric fields (e.g. billedsec, rate) as strings and
    // others (e.g. duration, time) as JSON numbers — tolerate either shape via String.valueOf.
    private long parseLongSafely(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void downloadAndSaveRecordingLocally(CallLog callLog, String filename) {
        String actualPlayUrl = baseUrl + "/play?appid=" + appId + "&secret=" + secret + "&file=" + filename;
        String loggablePlayUrl = baseUrl + "/play?appid=" + appId + "&secret=" + maskSecret(secret) + "&file=" + filename;

        try {
            log.info("TeleCMI play request -> URL=" + loggablePlayUrl);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(actualPlayUrl, byte[].class);

            byte[] bytes = response.getBody();
            log.info("TeleCMI play response <- status=" + response.getStatusCode()
                + ", bytes=" + (bytes != null ? bytes.length : 0));

            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("Empty response body when downloading recording from TeleCMI");
            }

            Path dir = Paths.get(recordingStoragePath);
            Files.createDirectories(dir);
            Path filePath = dir.resolve("telecmi_" + callLog.getId() + ".mp3");
            Files.write(filePath, bytes);

            callLog.setLocalFilePath(filePath.toAbsolutePath().toString());
        } catch (Exception e) {
            log.warning("TeleCMI local recording save failed for callLogId=" + callLog.getId()
                + ", filename=" + filename + ": " + e.getMessage());
        }
    }

    private String normalizeNumber(String phoneNumber) {
        return phoneNumber == null ? null : phoneNumber.replaceAll("[^0-9]", "");
    }

    private String maskSecret(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }
}
