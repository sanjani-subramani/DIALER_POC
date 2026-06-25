package com.qcerebrum.dialerbackend;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.logging.Logger;

@Component
public class CallStatusPoller {

    private static final Logger log = Logger.getLogger(CallStatusPoller.class.getName());

    private final TwilioCallService twilioCallService;
    private final CallLogRepository callLogRepo;

    public CallStatusPoller(TwilioCallService twilioCallService, CallLogRepository callLogRepo) {
        this.twilioCallService = twilioCallService;
        this.callLogRepo = callLogRepo;
    }

    @Scheduled(fixedDelay = 4000)
    public void pollActiveCalls() {
        List<CallLog> activeCalls = callLogRepo.findActiveCalls();
        for (CallLog callLog : activeCalls) {
            try {
                twilioCallService.refreshCallStatus(callLog.getId());
            } catch (Exception e) {
                log.warning("Failed to refresh status for CallLog " + callLog.getId() + ": " + e.getMessage());
            }
        }
    }
}
