package com.qcerebrum.dialerbackend;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Agent {

    @Id
    private String agentId;      // e.g. "agent1"

    private String agentName;    // e.g. "Sanjani"
    private String deviceId;     // e.g. "device1" (from the diagram's Agent_Device map)
    private String fcmToken;          // the phone's push token (filled in later)
    private String agentPhoneNumber;  // the agent's real phone number for bridge calls

    // ---- Getters and setters (Spring/JPA needs these) ----

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public String getAgentPhoneNumber() { return agentPhoneNumber; }
    public void setAgentPhoneNumber(String agentPhoneNumber) { this.agentPhoneNumber = agentPhoneNumber; }
}