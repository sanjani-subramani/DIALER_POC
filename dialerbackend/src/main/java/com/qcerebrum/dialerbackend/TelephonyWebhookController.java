package com.qcerebrum.dialerbackend;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/telephony")
@CrossOrigin(origins = "*")
public class TelephonyWebhookController {

    private final CallLogRepository callLogRepo;

    public TelephonyWebhookController(CallLogRepository callLogRepo) {
        this.callLogRepo = callLogRepo;
    }

    // --- The provider (Exotel/Twilio) calls THIS when a call ends ---
    // Real providers POST call result + recording URL here.
    // POST /api/telephony/callback
    //   JSON: { "callLogId": 1, "status": "COMPLETED", "recordingUrl": "https://.../rec.mp3" }
    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody Map<String, Object> body) {
        Long callLogId = Long.valueOf(body.get("callLogId").toString());
        String status = body.getOrDefault("status", "COMPLETED").toString();
        String recordingUrl = body.getOrDefault("recordingUrl", "").toString();

        Optional<CallLog> logOpt = callLogRepo.findById(callLogId);
        if (logOpt.isEmpty()) {
            return Map.of("success", false, "message", "No call log with id " + callLogId);
        }

        CallLog log = logOpt.get();
        log.setStatus(status);
        log.setRecordingUrl(recordingUrl);
        log.setEndTime(LocalDateTime.now());
        callLogRepo.save(log);

        return Map.of(
                "success", true,
                "message", "Call log updated from telephony webhook",
                "callLogId", callLogId,
                "status", status,
                "recordingUrl", recordingUrl
        );
    }

    // --- POC-only helper: simulate the provider webhook with a sample recording ---
    // Lets us demo the full pipeline for free, before a paid provider account exists.
    // POST /api/telephony/simulate?callLogId=1
    @PostMapping("/simulate")
    public Map<String, Object> simulate(@RequestParam Long callLogId) {
        Optional<CallLog> logOpt = callLogRepo.findById(callLogId);
        if (logOpt.isEmpty()) {
            return Map.of("success", false, "message", "No call log with id " + callLogId);
        }
        CallLog log = logOpt.get();
        log.setStatus("COMPLETED");
        // SIMULATED recording — stands in for the provider's real recording URL.
        log.setRecordingUrl("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3");
        log.setEndTime(LocalDateTime.now());
        callLogRepo.save(log);

        return Map.of(
                "success", true,
                "message", "SIMULATED telephony webhook (sample recording attached)",
                "callLogId", callLogId,
                "recordingUrl", log.getRecordingUrl()
        );
    }
}