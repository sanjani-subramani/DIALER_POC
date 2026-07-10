package com.qcerebrum.dialerbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/*
 * Phase 2 of live call status: wire TeleCMI webhook events (started/answered/hangup) to CallLog
 * status updates and instant recording fetch. Confirmed payload fields: request_id (matches
 * CallLog.providerCallSid), status, direction ("outbound"/"inbound"), to, leg ("a"=agent,
 * "b"=customer), cmiuuid, time (epoch ms).
 */
@RestController
@RequestMapping("/api/telephony/telecmi/webhook")
public class TelecmiWebhookController {

    private static final Logger log = Logger.getLogger(TelecmiWebhookController.class.getName());

    private final CallLogRepository callLogRepo;
    private final TelephonyProvider telephonyProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telecmi.user-id}")
    private String telecmiUserId;

    // Injected as the TelephonyProvider interface rather than the concrete TelecmiCallService:
    // TelephonyProviderConfig's @Bean factory method declares its return type as the interface,
    // so Spring registers/resolves the bean by that type — autowiring the concrete subclass
    // directly is unreliable and would also break app startup outright if telephony.provider is
    // ever switched back to "twilio" (no TelecmiCallService bean would exist at all). The
    // recording fetch this file needs, checkAndAttachRecording(Long), is already part of the
    // TelephonyProvider contract, so the interface is all that's required here.
    public TelecmiWebhookController(CallLogRepository callLogRepo, TelephonyProvider telephonyProvider) {
        this.callLogRepo = callLogRepo;
        this.telephonyProvider = telephonyProvider;
    }

    @PostMapping
    public Map<String, String> receiveWebhook(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        try {
            log.info("TeleCMI webhook POST received: " + rawBody);
            log.info("TeleCMI webhook headers: " + headersToString(request));
        } catch (Exception e) {
            log.warning("TeleCMI webhook logging failed: " + e.getMessage());
        }

        try {
            handlePayload(rawBody);
        } catch (Exception e) {
            log.warning("TeleCMI webhook processing failed: " + e.getMessage());
        }

        return Collections.singletonMap("status", "ok");
    }

    @GetMapping
    public Map<String, String> pingWebhook(HttpServletRequest request) {
        try {
            log.info("TeleCMI webhook GET ping received");
            log.info("TeleCMI webhook headers: " + headersToString(request));
        } catch (Exception e) {
            log.warning("TeleCMI webhook logging failed: " + e.getMessage());
        }
        return Collections.singletonMap("status", "ok");
    }

    private void handlePayload(String rawBody) throws Exception {
        if (rawBody == null || rawBody.isBlank()) {
            return;
        }

        Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);

        String status = asString(payload, "status");
        String requestId = asString(payload, "request_id");
        String direction = asString(payload, "direction");
        String to = asString(payload, "to");
        String leg = asString(payload, "leg");
        String cmiuuid = asString(payload, "cmiuuid");

        if (status == null) {
            log.info("TeleCMI webhook: payload had no status field, nothing to do.");
            return;
        }

        switch (status) {
            case "answered" -> handleAnswered(leg, requestId, to, direction, cmiuuid);
            case "hangup" -> handleHangup(requestId);
            case "started" -> log.info("TeleCMI webhook: status=started request_id=" + requestId + " direction=" + direction);
            default -> log.info("TeleCMI webhook: unhandled status=" + status + " request_id=" + requestId);
        }
    }

    private void handleAnswered(String leg, String requestId, String to, String direction, String cmiuuid) {
        if (!"b".equals(leg)) {
            log.info("TeleCMI webhook: status=answered leg=" + leg + " request_id=" + requestId + " (not the customer leg, ignoring)");
            return;
        }

        Optional<CallLog> existing = callLogRepo.findByProviderCallSid(requestId);
        if (existing.isPresent()) {
            CallLog callLog = existing.get();
            String currentStatus = callLog.getStatus();
            boolean alreadyFinal = "COMPLETED".equals(currentStatus) || "NO_ANSWER".equals(currentStatus) || "FAILED".equals(currentStatus);
            if (alreadyFinal) {
                log.info("TeleCMI webhook: callLogId=" + callLog.getId() + " already " + currentStatus + ", ignoring answered event.");
                return;
            }
            callLog.setStatus("IN_PROGRESS");
            callLogRepo.save(callLog);
            log.info("TeleCMI webhook: callLogId=" + callLog.getId() + " marked IN_PROGRESS (customer answered)");
            return;
        }

        // No matching outbound CallLog — this is an inbound customer->agent call.
        CallLog callLog = new CallLog();
        callLog.setDirection("INCOMING");
        callLog.setCustomerNumber(to);
        callLog.setAgentId(telecmiUserId);
        callLog.setStatus("IN_PROGRESS");
        callLog.setStartTime(LocalDateTime.now());
        callLog.setProviderCallSid(requestId);
        callLogRepo.save(callLog);
        log.info("TeleCMI webhook: created INCOMING CallLog id=" + callLog.getId() + " IN_PROGRESS");
    }

    private void handleHangup(String requestId) {
        Optional<CallLog> existing = callLogRepo.findByProviderCallSid(requestId);
        if (existing.isEmpty()) {
            log.info("TeleCMI webhook: status=hangup but no CallLog found for request_id=" + requestId + ", ignoring.");
            return;
        }

        CallLog callLog = existing.get();
        String currentStatus = callLog.getStatus();
        boolean alreadyFinal = "COMPLETED".equals(currentStatus) || "NO_ANSWER".equals(currentStatus) || "FAILED".equals(currentStatus);
        if (alreadyFinal) {
            log.info("TeleCMI webhook: callLogId=" + callLog.getId() + " already " + currentStatus + ", ignoring hangup event.");
            return;
        }

        if (!"IN_PROGRESS".equals(currentStatus) && !"DIALING".equals(currentStatus)) {
            log.info("TeleCMI webhook: callLogId=" + callLog.getId() + " has unexpected status=" + currentStatus + ", ignoring hangup event.");
            return;
        }

        callLog.setStatus("COMPLETED");
        if (callLog.getEndTime() == null) {
            callLog.setEndTime(LocalDateTime.now());
        }
        callLogRepo.save(callLog);

        Long callLogId = callLog.getId();
        new Thread(() -> {
            try {
                telephonyProvider.checkAndAttachRecording(callLogId);
            } catch (Exception e) {
                log.warning("TeleCMI webhook: async recording fetch failed for callLogId=" + callLogId + ": " + e.getMessage());
            }
        }).start();

        log.info("TeleCMI webhook: callLogId=" + callLogId + " marked COMPLETED, recording fetch triggered");
    }

    private String asString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private String headersToString(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        boolean first = true;
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!first) {
                sb.append(", ");
            }
            sb.append(name).append("=").append(request.getHeader(name));
            first = false;
        }
        return sb.toString();
    }
}
