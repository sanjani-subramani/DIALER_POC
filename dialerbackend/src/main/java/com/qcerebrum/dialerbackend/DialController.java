package com.qcerebrum.dialerbackend;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // lets the web portal (different port) call this during the POC
public class DialController {

    private final AgentRepository agentRepo;
    private final CallLogRepository callLogRepo;
    private final FcmService fcmService;

    public DialController(AgentRepository agentRepo, CallLogRepository callLogRepo, FcmService fcmService) {
        this.agentRepo = agentRepo;
        this.callLogRepo = callLogRepo;
        this.fcmService = fcmService;
    }

    // --- Endpoint 1: the web portal triggers a dial for a given agent ---
    // Called as: POST /api/dial   with JSON { "agentId": "agent1", "customerNumber": "9677736075" }
    @PostMapping("/dial")
    public Map<String, Object> dial(@RequestBody Map<String, String> body) {
        String agentId = body.get("agentId");
        String customerNumber = body.get("customerNumber");

        Optional<Agent> agentOpt = agentRepo.findById(agentId);
        if (agentOpt.isEmpty()) {
            return Map.of("success", false, "message", "Unknown agent: " + agentId);
        }
        Agent agent = agentOpt.get();

        CallLog log = new CallLog();
        log.setAgentId(agentId);
        log.setCustomerNumber(customerNumber);
        log.setStatus("DIALING");
        log.setStartTime(LocalDateTime.now());
        callLogRepo.save(log);

        fcmService.sendDialPush(agent.getFcmToken(), customerNumber, log.getId());

        return Map.of(
                "success", true,
                "message", "Dial triggered",
                "callLogId", log.getId(),
                "agentId", agentId,
                "deviceId", agent.getDeviceId(),
                "customerNumber", customerNumber
        );
    }

    // --- Endpoint 2: the web portal reads the call logs (newest first) ---
    @GetMapping("/calls")
    public List<CallLog> getCalls() {
        return callLogRepo.findAllByOrderByIdDesc();
    }

    // --- Register/update an agent's FCM token ---
    // POST /api/register-token  with JSON { "agentId": "agent1", "fcmToken": "..." }
    @PostMapping("/register-token")
    public Object registerToken(@RequestBody Map<String, String> body) {
        String agentId = body.get("agentId");
        String fcmToken = body.get("fcmToken");
        Optional<Agent> agentOpt = agentRepo.findById(agentId);
        if (agentOpt.isEmpty()) {
            return Map.of("success", false, "message", "Unknown agent: " + agentId);
        }
        Agent agent = agentOpt.get();
        agent.setFcmToken(fcmToken);
        return agentRepo.save(agent);
    }

    // --- Helper to register/seed an agent (used to create the Agent_Device rows) ---
    // POST /api/agents  with JSON { "agentId":"agent1","agentName":"Sanjani","deviceId":"device1" }
    @PostMapping("/agents")
    public Agent addAgent(@RequestBody Agent agent) {
        return agentRepo.save(agent);
    }

    // GET /api/agents  → list all agents (to verify the mapping)
    @GetMapping("/agents")
    public List<Agent> getAgents() {
        return agentRepo.findAll();
    }
}
