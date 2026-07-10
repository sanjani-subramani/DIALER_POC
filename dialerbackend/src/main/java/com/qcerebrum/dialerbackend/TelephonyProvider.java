package com.qcerebrum.dialerbackend;

public interface TelephonyProvider {

    CallLog placeRecordedCall(String toNumber);

    CallLog placeBridgeCall(String agentId, String customerNumber);

    CallLog refreshCallStatus(Long callLogId);

    CallLog checkAndAttachRecording(Long callLogId);

    default void syncIncomingCalls() {
    }
}
