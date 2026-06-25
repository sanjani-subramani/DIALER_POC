package com.qcerebrum.dialerbackend;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    public void sendDialPush(String fcmToken, String customerNumber, Long callLogId) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM token is null/empty — skipping push for callLogId={}", callLogId);
            return;
        }
        try {
            Message message = Message.builder()
                    .putData("customerNumber", customerNumber)
                    .putData("callLogId", String.valueOf(callLogId))
                    .setToken(fcmToken)
                    .build();
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM push sent: messageId={} callLogId={}", response, callLogId);
        } catch (Exception e) {
            log.error("FCM push failed for callLogId={}: {}", callLogId, e.getMessage());
        }
    }
}
