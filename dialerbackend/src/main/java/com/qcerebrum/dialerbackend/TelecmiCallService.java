package com.qcerebrum.dialerbackend;

import java.util.logging.Logger;

/*
 * TeleCMI TelephonyProvider implementation — PLACEHOLDER, not yet functional.
 *
 * TODO before this can be activated:
 *   - API base URL for TeleCMI's REST endpoints
 *   - API key / auth mechanism (headers, token refresh, etc.)
 *   - Click-to-call endpoint (equivalent of Twilio's Call.creator bridge flow)
 *   - Number-masking config (agent/customer number privacy)
 *   - Recording-fetch endpoint (equivalent of Twilio's Recording.reader)
 *   - Webhook handling for callback routing (call status/events pushed by TeleCMI)
 */
public class TelecmiCallService implements TelephonyProvider {

    private static final Logger log = Logger.getLogger(TelecmiCallService.class.getName());

    private static final String NOT_CONFIGURED_MESSAGE =
        "TeleCMI provider not yet configured — no account/API credentials available.";

    @Override
    public CallLog placeRecordedCall(String toNumber) {
        log.warning(NOT_CONFIGURED_MESSAGE);
        throw new UnsupportedOperationException(NOT_CONFIGURED_MESSAGE);
    }

    @Override
    public CallLog placeBridgeCall(String agentId, String customerNumber) {
        log.warning(NOT_CONFIGURED_MESSAGE);
        throw new UnsupportedOperationException(NOT_CONFIGURED_MESSAGE);
    }

    @Override
    public CallLog refreshCallStatus(Long callLogId) {
        log.warning(NOT_CONFIGURED_MESSAGE);
        throw new UnsupportedOperationException(NOT_CONFIGURED_MESSAGE);
    }

    @Override
    public CallLog checkAndAttachRecording(Long callLogId) {
        log.warning(NOT_CONFIGURED_MESSAGE);
        throw new UnsupportedOperationException(NOT_CONFIGURED_MESSAGE);
    }
}
