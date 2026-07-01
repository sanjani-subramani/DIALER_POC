package com.qcerebrum.dialerbackend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
public class TwilioController {

    private final TwilioCallService twilioCallService;
    private final CallLogRepository callLogRepo;

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    public TwilioController(TwilioCallService twilioCallService, CallLogRepository callLogRepo) {
        this.twilioCallService = twilioCallService;
        this.callLogRepo = callLogRepo;
    }

    @PostMapping("/api/telephony/twilio-call")
    public CallLog placeCall(@RequestBody Map<String, String> body) {
        return twilioCallService.placeRecordedCall(body.get("toNumber"));
    }

    @PostMapping("/api/telephony/bridge-call")
    public ResponseEntity<Object> bridgeCall(@RequestBody Map<String, String> body) {
        CallLog log = twilioCallService.placeBridgeCall(body.get("agentId"), body.get("customerNumber"));
        if (log == null) {
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Agent not found or has no phone number registered")
            );
        }
        return ResponseEntity.ok(log);
    }

    @GetMapping("/api/telephony/check-recording")
    public CallLog checkRecording(@RequestParam Long callLogId) {
        return twilioCallService.checkAndAttachRecording(callLogId);
    }

    @GetMapping("/api/recordings/{callLogId}")
    public ResponseEntity<byte[]> streamRecording(@PathVariable Long callLogId) {
        CallLog log = callLogRepo.findById(callLogId)
            .orElseThrow(() -> new RuntimeException("CallLog not found: " + callLogId));

        String recordingSid = log.getTwilioRecordingSid();
        if (recordingSid == null) {
            return ResponseEntity.notFound().build();
        }

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid
            + "/Recordings/" + recordingSid + ".mp3";

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(accountSid, authToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .body(response.getBody());
    }

    // ADDITIVE: serves the locally saved .mp3 copy from disk, if one exists.
    // Does not touch the existing /api/recordings/{id} endpoint above.
    @GetMapping("/api/recordings/local/{callLogId}")
    public ResponseEntity<byte[]> streamLocalRecording(@PathVariable Long callLogId) {
        Optional<CallLog> logOpt = callLogRepo.findById(callLogId);
        if (logOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body(("No call log with id " + callLogId).getBytes(StandardCharsets.UTF_8));
        }

        String localFilePath = logOpt.get().getLocalFilePath();
        if (localFilePath == null || localFilePath.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body(("No local recording file found for callLogId " + callLogId).getBytes(StandardCharsets.UTF_8));
        }

        Path path = Paths.get(localFilePath);
        if (!Files.exists(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body(("Local recording file not found on disk for callLogId " + callLogId).getBytes(StandardCharsets.UTF_8));
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body(("Failed to read local recording file: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
