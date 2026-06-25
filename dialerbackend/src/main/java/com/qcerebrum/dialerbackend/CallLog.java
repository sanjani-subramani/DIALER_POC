package com.qcerebrum.dialerbackend;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class CallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                 // auto-numbered: 1, 2, 3...

    private String agentId;          // who made the call
    private String customerNumber;   // the number dialed
    private String status;           // DIALING / CONNECTED / COMPLETED
    private String recordingUrl;     // link to the recording (from the telephony webhook)
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String providerCallSid;
    private String twilioRecordingSid;

    // ---- Getters and setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getProviderCallSid() { return providerCallSid; }
    public void setProviderCallSid(String providerCallSid) { this.providerCallSid = providerCallSid; }

    public String getTwilioRecordingSid() { return twilioRecordingSid; }
    public void setTwilioRecordingSid(String twilioRecordingSid) { this.twilioRecordingSid = twilioRecordingSid; }
}