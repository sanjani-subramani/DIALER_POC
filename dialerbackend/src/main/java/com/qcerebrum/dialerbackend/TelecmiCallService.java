package com.qcerebrum.dialerbackend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final FirebaseStorageService firebaseStorageService;

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

    public TelecmiCallService(CallLogRepository callLogRepo, AgentRepository agentRepo,
                               FirebaseStorageService firebaseStorageService) {
        this.callLogRepo = callLogRepo;
        this.agentRepo = agentRepo;
        this.firebaseStorageService = firebaseStorageService;
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
        callLog.setDirection("OUTGOING");
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

        // With the CDR time-gate and cmiuid dedup in checkAndAttachRecording, it's safe to check
        // on every poll (poller runs every ~4s) instead of waiting 2 minutes before the first
        // check. A recording (or a missed-call CDR) appearing in TeleCMI is our only signal that
        // the call finished — checkAndAttachRecording moves status to COMPLETED/NO_ANSWER on a
        // match and otherwise leaves it as-is.
        callLog = checkAndAttachRecording(callLogId);

        String updatedStatus = callLog.getStatus();
        boolean stillUnresolved = "DIALING".equals(updatedStatus) || "RINGING".equals(updatedStatus) || "IN_PROGRESS".equals(updatedStatus);
        boolean olderThanFiveMinutes = callLog.getStartTime().isBefore(LocalDateTime.now().minusMinutes(5));

        if (stillUnresolved && olderThanFiveMinutes) {
            // Safety net: never leave a call stuck in DIALING/RINGING/IN_PROGRESS forever if both
            // CDR APIs miss it for whatever reason.
            log.info("TeleCMI: callLogId=" + callLogId + " still unresolved after 5 minutes — forcing NO_ANSWER as a safety net.");
            callLog.setStatus("NO_ANSWER");
            if (callLog.getEndTime() == null) {
                callLog.setEndTime(LocalDateTime.now());
            }
            callLog = callLogRepo.save(callLog);
        }

        return callLog;
    }

    @Override
    public CallLog checkAndAttachRecording(Long callLogId) {
        CallLog callLog = callLogRepo.findById(callLogId)
            .orElseThrow(() -> new RuntimeException("CallLog not found: " + callLogId));

        // Final states must never be reprocessed or overwritten, even via the manual
        // check-recording endpoint — e.g. an /answered CDR from a later, unrelated inbound call
        // must not clobber a CallLog that's already settled as NO_ANSWER or FAILED.
        String existingStatus = callLog.getStatus();
        if ("NO_ANSWER".equals(existingStatus) || "FAILED".equals(existingStatus)) {
            return callLog;
        }

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

        // Time gate: a CDR from before this call started (beyond a little clock-skew tolerance)
        // can never belong to it — without this, a stale CDR from an earlier call to the same
        // number gets re-attached to every subsequent CallLog.
        long minAllowedCdrTimeMillis = callLog.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 60_000L;
        // Dedup: a CDR already claimed by another CallLog (via its cmiuid) must never match again,
        // except the CDR this CallLog already claimed itself (idempotent re-check).
        String ownCmiuid = callLog.getTwilioRecordingSid();

        // /answered is inbound-only (see syncIncomingCalls) and must never be consulted here —
        // matching an unrelated inbound CDR onto an outbound CallLog is exactly the bug this
        // sequence used to have. Outbound resolution is out_answered, then out_missed only.
        Map<?, ?> bestMatch = null;
        String matchedEndpoint = null;

        Map<?, ?> outAnsweredBody = queryCdrEndpoint("/out_answered", startEpochMillis, endEpochMillis, callLogId);
        if (outAnsweredBody != null && outAnsweredBody.get("cdr") instanceof List<?> outCdrList) {
            bestMatch = selectBestCdrMatch(outCdrList, targetNumber, targetStartMillis, minAllowedCdrTimeMillis, ownCmiuid);
            if (bestMatch != null) {
                matchedEndpoint = "out_answered";
            }
        }

        if (bestMatch == null) {
            Map<?, ?> missedBody = queryCdrEndpoint("/out_missed", startEpochMillis, endEpochMillis, callLogId);
            if (missedBody != null && missedBody.get("cdr") instanceof List<?> missedCdrList) {
                Map<?, ?> missedMatch = selectBestMissedMatch(missedCdrList, targetNumber, targetStartMillis, minAllowedCdrTimeMillis, ownCmiuid);
                if (missedMatch != null) {
                    log.info("TeleCMI: matched out_missed CDR for callLogId=" + callLogId + ", marking NO_ANSWER");
                    Object missedCmiuidObj = missedMatch.get("cmiuid");
                    callLog.setTwilioRecordingSid(missedCmiuidObj != null ? missedCmiuidObj.toString() : null);
                    callLog.setStatus("NO_ANSWER");
                    if (callLog.getEndTime() == null) {
                        callLog.setEndTime(LocalDateTime.now());
                    }
                    return callLogRepo.save(callLog);
                }
            }
        }

        if (bestMatch == null) {
            log.info("TeleCMI: no CDR record matched customerNumber=" + callLog.getCustomerNumber()
                + " for callLogId=" + callLogId + " on /out_answered or /out_missed — recording may not be ready yet.");
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

        if (callLog.getLocalFilePath() != null && !callLog.getLocalFilePath().isBlank()) {
            File localFile = new File(callLog.getLocalFilePath());
            String fbUrl = firebaseStorageService.uploadRecording(callLog.getId(), localFile);
            if (fbUrl != null) {
                callLog.setFirebaseUrl(fbUrl);
            }
        }

        return callLogRepo.save(callLog);
    }

    @Override
    public void syncIncomingCalls() {
        long startEpochMillis = LocalDateTime.now().minusMinutes(15).toInstant(ZoneOffset.UTC).toEpochMilli();
        long endEpochMillis = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();

        Map<?, ?> answeredBody = queryCdrEndpoint("/answered", startEpochMillis, endEpochMillis, null);
        if (answeredBody == null || !(answeredBody.get("cdr") instanceof List<?> cdrList)) {
            return;
        }

        for (Object item : cdrList) {
            if (!(item instanceof Map<?, ?> cdr)) {
                continue;
            }

            Object cmiuidObj = cdr.get("cmiuid");
            if (cmiuidObj == null) {
                continue;
            }
            String cmiuid = cmiuidObj.toString();

            if (callLogRepo.existsByTwilioRecordingSid(cmiuid)) {
                continue; // already known — either a prior sync pass or matched elsewhere
            }

            Object fromObj = cdr.get("from");
            Object agentObj = cdr.get("agent");
            long cdrTimeMillis = parseLongSafely(cdr.get("time"), 0L);
            long billedsec = parseLongSafely(cdr.get("billedsec"), 0L);

            CallLog callLog = new CallLog();
            callLog.setDirection("INCOMING");
            callLog.setCustomerNumber(fromObj != null ? normalizeNumber(fromObj.toString()) : null);
            callLog.setAgentId(agentObj != null ? agentObj.toString() : null);
            callLog.setStatus("COMPLETED");
            callLog.setTwilioRecordingSid(cmiuid);
            callLog.setProviderCallSid(cmiuid);
            callLog.setStartTime(cdrTimeMillis > 0 ? epochMillisToLocalDateTime(cdrTimeMillis) : LocalDateTime.now());
            callLog.setEndTime(epochMillisToLocalDateTime(cdrTimeMillis + billedsec * 1000L));
            callLogRepo.save(callLog);

            log.info("TeleCMI inbound sync: created INCOMING CallLog id=" + callLog.getId()
                + " from=" + callLog.getCustomerNumber());

            Object filenameObj = cdr.get("filename");
            String filename = filenameObj != null ? filenameObj.toString() : null;
            if (filename != null && !filename.isBlank()) {
                downloadAndSaveRecordingLocally(callLog, filename);
                callLog.setRecordingUrl("/api/recordings/local/" + callLog.getId());
                callLogRepo.save(callLog);
            }
        }
    }

    private LocalDateTime epochMillisToLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
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

    private Map<?, ?> selectBestCdrMatch(List<?> cdrList, String targetNumber, long targetStartMillis,
                                          long minAllowedCdrTimeMillis, String ownCmiuid) {
        Map<?, ?> best = null;
        boolean bestAgentMatch = false;
        long bestDiff = Long.MAX_VALUE;

        for (Object item : cdrList) {
            if (!(item instanceof Map<?, ?> cdr)) {
                continue;
            }

            long cdrTime = parseLongSafely(cdr.get("time"), Long.MAX_VALUE);
            if (cdrTime < minAllowedCdrTimeMillis) {
                continue; // predates this call (beyond clock-skew tolerance) — can't belong to it
            }

            Object fromObj = cdr.get("from");
            Object toObj = cdr.get("to");
            boolean fromMatches = fromObj != null && targetNumber.equals(normalizeNumber(fromObj.toString()));
            boolean toMatches = toObj != null && targetNumber.equals(normalizeNumber(toObj.toString()));
            if (!fromMatches && !toMatches) {
                continue;
            }

            if (!isCdrAvailable(cdr, ownCmiuid)) {
                continue;
            }

            Object agentObj = cdr.get("agent");
            boolean agentMatches = agentObj != null && userId.equals(agentObj.toString());

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

    private Map<?, ?> selectBestMissedMatch(List<?> cdrList, String targetNumber, long targetStartMillis,
                                             long minAllowedCdrTimeMillis, String ownCmiuid) {
        // In out_missed CDRs "from" is our virtual number, not the customer — match on "to" only.
        Map<?, ?> best = null;
        long bestDiff = Long.MAX_VALUE;

        for (Object item : cdrList) {
            if (!(item instanceof Map<?, ?> cdr)) {
                continue;
            }
            long cdrTime = parseLongSafely(cdr.get("time"), Long.MAX_VALUE);
            if (cdrTime < minAllowedCdrTimeMillis) {
                continue;
            }
            Object toObj = cdr.get("to");
            if (toObj == null || !targetNumber.equals(normalizeNumber(toObj.toString()))) {
                continue;
            }
            if (!isCdrAvailable(cdr, ownCmiuid)) {
                continue;
            }
            long diff = Math.abs(cdrTime - targetStartMillis);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = cdr;
            }
        }

        return best;
    }

    private boolean isCdrAvailable(Map<?, ?> cdr, String ownCmiuid) {
        Object cmiuidObj = cdr.get("cmiuid");
        if (cmiuidObj == null) {
            return true;
        }
        String cmiuid = cmiuidObj.toString();
        if (cmiuid.equals(ownCmiuid)) {
            return true;
        }
        return !callLogRepo.existsByTwilioRecordingSid(cmiuid);
    }

    private LocalDateTime computeEndTimeFromCdr(Map<?, ?> cdr) {
        Object timeObj = cdr.get("time");
        if (timeObj == null) {
            return LocalDateTime.now();
        }
        long cdrTimeMillis = parseLongSafely(timeObj, 0L);
        long billedsec = parseLongSafely(cdr.get("billedsec"), 0L);
        return epochMillisToLocalDateTime(cdrTimeMillis + billedsec * 1000L);
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
