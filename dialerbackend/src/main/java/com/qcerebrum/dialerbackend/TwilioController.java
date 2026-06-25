package com.qcerebrum.dialerbackend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

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
}
