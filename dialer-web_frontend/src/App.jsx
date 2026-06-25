import { useState, useEffect } from "react";
import "./App.css";

const API = "http://localhost:8080/api";

function App() {
  const [agentId, setAgentId] = useState("agent1");
  const [customerNumber, setCustomerNumber] = useState("");
  const [calls, setCalls] = useState([]);
  const [agents, setAgents] = useState([]);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [checkingId, setCheckingId] = useState(null);

  // Add Agent form state
  const [newAgentId, setNewAgentId] = useState("");
  const [newAgentName, setNewAgentName] = useState("");
  const [newAgentPhone, setNewAgentPhone] = useState("");
  const [addingAgent, setAddingAgent] = useState(false);

  const loadCalls = async () => {
    try {
      const res = await fetch(`${API}/calls`);
      const data = await res.json();
      setCalls(data);
    } catch (e) {
      setMessage("Could not reach backend. Is the server running on 8080?");
    }
  };

  const loadAgents = async () => {
    try {
      const res = await fetch(`${API}/agents`);
      const data = await res.json();
      setAgents(data);
      // If the currently selected agent no longer exists, default to the first one
      if (data.length > 0 && !data.some((a) => a.agentId === agentId)) {
        setAgentId(data[0].agentId);
      }
    } catch (e) {
      setMessage("Could not load agents from backend.");
    }
  };

  useEffect(() => {
    loadAgents();
    loadCalls();
    const t = setInterval(loadCalls, 4000);
    return () => clearInterval(t);
  }, []);

  // Auto-format an Indian 10-digit number to +91XXXXXXXXXX if no "+" was typed
  const formatNumber = (raw) => {
    const trimmed = raw.trim();
    if (trimmed.startsWith("+")) return trimmed;
    const digitsOnly = trimmed.replace(/\D/g, "");
    return `+91${digitsOnly}`;
  };

  // Real Twilio bridge call — rings the agent first, then connects the customer
  const handleDial = async () => {
    if (!customerNumber.trim()) {
      setMessage("Enter a customer number first.");
      return;
    }
    setLoading(true);
    setMessage("");
    const toNumber = formatNumber(customerNumber);
    try {
      const res = await fetch(`${API}/telephony/bridge-call`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ agentId, customerNumber: toNumber }),
      });
      const data = await res.json();
      if (data.id) {
        setMessage(`Bridge call #${data.id}: ${agentId}'s phone will ring first, then ${toNumber} will be connected.`);
        setCustomerNumber("");
        loadCalls();
      } else {
        setMessage(data.message || "Call failed.");
      }
    } catch (e) {
      setMessage("Could not reach backend.");
    }
    setLoading(false);
  };

  // Add a new agent through the UI
  const handleAddAgent = async () => {
    if (!newAgentId.trim() || !newAgentName.trim() || !newAgentPhone.trim()) {
      setMessage("Fill in Agent ID, Name, and Phone Number to add an agent.");
      return;
    }
    setAddingAgent(true);
    setMessage("");
    const phone = formatNumber(newAgentPhone);
    const id = newAgentId.trim();
    try {
      const res = await fetch(`${API}/agents`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          agentId: id,
          agentName: newAgentName.trim(),
          deviceId: id, // device mapping kept simple for the POC
          agentPhoneNumber: phone,
        }),
      });
      const data = await res.json();
      if (data.agentId) {
        setMessage(`Agent "${data.agentId}" saved (${data.agentPhoneNumber}).`);
        setNewAgentId("");
        setNewAgentName("");
        setNewAgentPhone("");
        await loadAgents();
        setAgentId(data.agentId); // auto-select the agent you just added
      } else {
        setMessage("Could not add agent.");
      }
    } catch (e) {
      setMessage("Could not reach backend.");
    }
    setAddingAgent(false);
  };

  // Ask the backend to check Twilio for the finished recording
  const checkRecording = async (id) => {
    setCheckingId(id);
    try {
      await fetch(`${API}/telephony/check-recording?callLogId=${id}`);
      await loadCalls();
    } catch (e) {
      setMessage("Could not check recording.");
    }
    setCheckingId(null);
  };

  return (
    <div className="page">
      <header className="header">
        <h1>QCerebrum Dialer</h1>
        <p>Click-to-Call & Call Recording — Live via Twilio</p>
      </header>

      <section className="card">
        <h2>Dial Customer</h2>
        <div className="row">
          <label>Agent</label>
          <select value={agentId} onChange={(e) => setAgentId(e.target.value)}>
            {agents.length === 0 && <option value="">No agents yet</option>}
            {agents.map((a) => (
              <option key={a.agentId} value={a.agentId}>
                {a.agentId} ({a.agentName}) — {a.agentPhoneNumber}
              </option>
            ))}
          </select>
        </div>
        <div className="row">
          <label>Customer Number</label>
          <input
            type="tel"
            placeholder="e.g. 9677736075 (or +91...)"
            value={customerNumber}
            onChange={(e) => setCustomerNumber(e.target.value)}
          />
        </div>
        <button className="dial-btn" onClick={handleDial} disabled={loading}>
          {loading ? "Dialing..." : "Dial Customer"}
        </button>
        {message && <p className="message">{message}</p>}
      </section>

      <section className="card">
        <h2>Add Agent</h2>
        <div className="row">
          <label>Agent ID</label>
          <input
            type="text"
            placeholder="e.g. agent2"
            value={newAgentId}
            onChange={(e) => setNewAgentId(e.target.value)}
          />
        </div>
        <div className="row">
          <label>Name</label>
          <input
            type="text"
            placeholder="e.g. Priya"
            value={newAgentName}
            onChange={(e) => setNewAgentName(e.target.value)}
          />
        </div>
        <div className="row">
          <label>Phone Number</label>
          <input
            type="tel"
            placeholder="e.g. 9677006726 (or +91...)"
            value={newAgentPhone}
            onChange={(e) => setNewAgentPhone(e.target.value)}
          />
        </div>
        <button className="dial-btn" onClick={handleAddAgent} disabled={addingAgent}>
          {addingAgent ? "Adding..." : "Add Agent"}
        </button>
        <p className="note">
          New agents are saved to the backend and appear in the dropdown above immediately.
          (Note: the in-memory database resets when the backend restarts.)
        </p>
      </section>

      <section className="card">
        <h2>Call Logs</h2>
        <table className="logs">
          <thead>
            <tr>
              <th>#</th>
              <th>Agent</th>
              <th>Customer</th>
              <th>Status</th>
              <th>Started</th>
              <th>Recording</th>
            </tr>
          </thead>
          <tbody>
            {calls.length === 0 && (
              <tr><td colSpan="6" className="empty">No calls yet</td></tr>
            )}
            {calls.map((c) => (
              <tr key={c.id}>
                <td>{c.id}</td>
                <td>{c.agentId}</td>
                <td>{c.customerNumber}</td>
                <td><span className={`status ${c.status}`}>{c.status}</span></td>
                <td>{c.startTime ? c.startTime.replace("T", " ").slice(0, 19) : "-"}</td>
                <td>
                  {c.recordingUrl ? (
                    <audio controls src={c.recordingUrl} style={{ height: 32 }} />
                  ) : (
                    <button
                      className="sim-btn"
                      onClick={() => checkRecording(c.id)}
                      disabled={checkingId === c.id}
                    >
                      {checkingId === c.id ? "Checking..." : "Check recording"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="note">
          Recordings are real, captured server-side by Twilio — not simulated. If the recording isn't ready yet, click "Check recording" again after a few seconds.
        </p>
      </section>
    </div>
  );
}

export default App;
