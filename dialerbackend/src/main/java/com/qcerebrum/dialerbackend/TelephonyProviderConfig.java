package com.qcerebrum.dialerbackend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelephonyProviderConfig {

    @Value("${telephony.provider:twilio}")
    private String telephonyProvider;

    @Bean
    public TelephonyProvider telephonyProvider(CallLogRepository callLogRepo, AgentRepository agentRepo,
                                                FirebaseStorageService firebaseStorageService) {
        if ("telecmi".equalsIgnoreCase(telephonyProvider)) {
            return new TelecmiCallService(callLogRepo, agentRepo, firebaseStorageService);
        }
        return new TwilioCallService(callLogRepo, agentRepo);
    }
}
