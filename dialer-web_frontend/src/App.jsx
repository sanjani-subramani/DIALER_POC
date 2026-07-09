import { useState, useEffect } from "react";

const API = "http://localhost:8080/api";
const BACKEND_ORIGIN = API.replace(/\/api$/, "");

const resolveRecordingUrl = (url) =>
  !url ? url : (url.startsWith("http") ? url : `${BACKEND_ORIGIN}${url}`);

/* ------------------------------------------------------------------ */
/*  Role definitions                                                   */
/* ------------------------------------------------------------------ */
const ROLES = {
  supervisor: { label: "Supervisor", canAddAgent: true },
  manager: { label: "Manager", canAddAgent: true },
  agent: { label: "Agent", canAddAgent: false },
};

/* ------------------------------------------------------------------ */
/*  Uniform blue theme (same for all roles)                           */
/* ------------------------------------------------------------------ */
const BLUE_THEME = {
  accentBg:       "bg-blue-600 hover:bg-blue-700",
  accentText:     "text-blue-700",
  accentSoftBg:   "bg-blue-50",
  accentRing:     "ring-blue-200",
  navActive:      "bg-blue-50 text-blue-700",
  bannerGradient: "from-blue-600 to-blue-500",
  iconBadge:      "bg-blue-600",
  focusClasses:   "focus:border-blue-500 focus:ring-2 focus:ring-blue-100",
  msgBox:         "bg-blue-50 text-blue-900 ring-1 ring-blue-200",
  checkBtn:       "text-blue-700 border border-blue-200 hover:bg-blue-50",
  bannerBtnText:  "text-blue-700",
};

const THEMES = {
  agent:      BLUE_THEME,
  supervisor: BLUE_THEME,
  manager:    BLUE_THEME,
};

/* ------------------------------------------------------------------ */
/*  Small inline icons                                                 */
/* ------------------------------------------------------------------ */
const Icon = {
  phone: (c = "") => (
    <svg className={c} viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2A19.8 19.8 0 0 1 3 5.18 2 2 0 0 1 5 3h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.9.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z" /></svg>
  ),
  home: (c = "") => (
    <svg className={c} viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M3 9.5 12 3l9 6.5" /><path d="M5 10v10h14V10" /></svg>
  ),
  userPlus: (c = "") => (
    <svg className={c} viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><line x1="19" y1="8" x2="19" y2="14" /><line x1="22" y1="11" x2="16" y2="11" /></svg>
  ),
  list: (c = "") => (
    <svg className={c} viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><line x1="8" y1="6" x2="21" y2="6" /><line x1="8" y1="12" x2="21" y2="12" /><line x1="8" y1="18" x2="21" y2="18" /><line x1="3" y1="6" x2="3.01" y2="6" /><line x1="3" y1="12" x2="3.01" y2="12" /><line x1="3" y1="18" x2="3.01" y2="18" /></svg>
  ),
  lock: (c = "") => (
    <svg className={c} viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></svg>
  ),
  logout: (c = "") => (
    <svg className={c} viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" /></svg>
  ),
};

/* ------------------------------------------------------------------ */
/*  Status badge colours                                               */
/* ------------------------------------------------------------------ */
const statusStyle = (s) => {
  const map = {
    DIALING: "bg-amber-50 text-amber-700 ring-amber-200",
    RINGING: "bg-amber-50 text-amber-700 ring-amber-200",
    IN_PROGRESS: "bg-blue-50 text-blue-700 ring-blue-200",
    COMPLETED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
    NO_ANSWER: "bg-orange-50 text-orange-700 ring-orange-200",
    BUSY: "bg-orange-50 text-orange-700 ring-orange-200",
    FAILED: "bg-red-50 text-red-700 ring-red-200",
  };
  return map[s] || "bg-slate-100 text-slate-600 ring-slate-200";
};

/* ================================================================== */
/*  Single role login card                                             */
/* ================================================================== */
function RoleCard({ role, creds, onLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = () => {
    if (username === creds.username && password === creds.password) {
      onLogin(role.id);
    } else {
      setError("Invalid username or password");
    }
  };

  const inputCls =
    "w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder-slate-400 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition";

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition overflow-hidden">
      <div className={`h-1.5 ${role.bar}`} />
      <div className="p-6">
        <div className={`w-11 h-11 rounded-full ${role.iconWrap} grid place-items-center mb-4`}>
          {role.icon()}
        </div>
        <h2 className="text-lg font-bold text-slate-900">{role.title}</h2>
        <p className="text-sm text-slate-500 mt-0.5 mb-5">{role.subtitle}</p>
        <div className="space-y-3">
          <input
            type="text"
            placeholder="Username"
            value={username}
            onChange={(e) => { setUsername(e.target.value); setError(""); }}
            className={inputCls}
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => { setPassword(e.target.value); setError(""); }}
            onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
            className={inputCls}
          />
        </div>
        <button
          onClick={handleSubmit}
          className={`mt-4 w-full rounded-lg ${role.btn} text-white font-semibold px-4 py-2.5 text-sm transition shadow-sm`}
        >
          Sign In
        </button>
        {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
      </div>
    </div>
  );
}

/* ================================================================== */
/*  Login screen                                                       */
/* ================================================================== */
function LoginScreen({ onLogin }) {
  const CREDS = {
    agent:      { username: "agent",      password: "agent123"   },
    supervisor: { username: "supervisor", password: "super123"   },
    manager:    { username: "manager",    password: "manager123" },
  };

  const roles = [
    {
      id: "agent",
      title: "Agent Login",
      subtitle: "Dial customers and view call logs",
      bar: "bg-blue-600",
      btn: "bg-blue-600 hover:bg-blue-700",
      iconWrap: "bg-blue-50 text-blue-600",
      icon: Icon.phone,
    },
    {
      id: "supervisor",
      title: "Supervisor Login",
      subtitle: "Full access including agent management",
      bar: "bg-blue-600",
      btn: "bg-blue-600 hover:bg-blue-700",
      iconWrap: "bg-blue-50 text-blue-600",
      icon: Icon.userPlus,
    },
    {
      id: "manager",
      title: "Manager Login",
      subtitle: "Full access including agent management",
      bar: "bg-blue-600",
      btn: "bg-blue-600 hover:bg-blue-700",
      iconWrap: "bg-blue-50 text-blue-600",
      icon: Icon.lock,
    },
  ];

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-linear-to-br from-slate-100 via-slate-50 to-blue-50 px-4 py-12">
      <div className="w-full max-w-5xl">
        <div className="text-center mb-10">
          <div className="mx-auto mb-4 w-12 h-12 rounded-xl bg-blue-600 text-white grid place-items-center shadow-lg shadow-blue-600/20">
            {Icon.phone()}
          </div>
          <h1 className="text-3xl font-bold text-slate-900 tracking-tight">Kodachadri Chit Fund</h1>
          <p className="text-slate-500 mt-1.5">Click-to-Call &amp; Call Recording Portal</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {roles.map((r) => (
            <RoleCard key={r.id} role={r} creds={CREDS[r.id]} onLogin={onLogin} />
          ))}
        </div>

        <p className="text-center text-xs text-slate-400 mt-8">
          Demo portal — credentials are for demonstration only and are not secured.
        </p>
      </div>
    </div>
  );
}

/* ================================================================== */
/*  Main App                                                           */
/* ================================================================== */
function App() {
  const [role, setRole] = useState(null); // null = logged out
  const [page, setPage] = useState("home");

  const [agentId, setAgentId] = useState("agent1");
  const [customerNumber, setCustomerNumber] = useState("");
  const [calls, setCalls] = useState([]);
  const [agents, setAgents] = useState([]);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [checkingId, setCheckingId] = useState(null);

  const [newAgentId, setNewAgentId] = useState("");
  const [newAgentName, setNewAgentName] = useState("");
  const [newAgentPhone, setNewAgentPhone] = useState("");
  const [addingAgent, setAddingAgent] = useState(false);

  const canAddAgent = role ? ROLES[role].canAddAgent : false;

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
      if (data.length > 0 && !data.some((a) => a.agentId === agentId)) {
        setAgentId(data[0].agentId);
      }
    } catch (e) {
      setMessage("Could not load agents from backend.");
    }
  };

  useEffect(() => {
    if (!role) return;
    loadAgents();
    loadCalls();
    const t = setInterval(loadCalls, 4000);
    return () => clearInterval(t);
  }, [role]);

  const formatNumber = (raw) => {
    const trimmed = raw.trim();
    if (trimmed.startsWith("+")) return trimmed;
    const digitsOnly = trimmed.replace(/\D/g, "");
    return `+91${digitsOnly}`;
  };

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

  const handleAddAgent = async () => {
    if (!canAddAgent) return; // role guard (UI-level)
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
        body: JSON.stringify({ agentId: id, agentName: newAgentName.trim(), deviceId: id, agentPhoneNumber: phone }),
      });
      const data = await res.json();
      if (data.agentId) {
        setMessage(`Agent "${data.agentId}" saved (${data.agentPhoneNumber}).`);
        setNewAgentId("");
        setNewAgentName("");
        setNewAgentPhone("");
        await loadAgents();
        setAgentId(data.agentId);
      } else {
        setMessage("Could not add agent.");
      }
    } catch (e) {
      setMessage("Could not reach backend.");
    }
    setAddingAgent(false);
  };

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

  /* ---------- Not logged in ---------- */
  if (!role) {
    return <LoginScreen onLogin={(r) => { setRole(r); setPage("home"); setMessage(""); }} />;
  }

  const th = THEMES[role];
  const livePreview = customerNumber.trim() ? formatNumber(customerNumber) : "";
  const completedCount = calls.filter((c) => c.status === "COMPLETED").length;
  const recordedCount = calls.filter((c) => c.recordingUrl).length;

  const nav = [
    { id: "home", label: "Dashboard", icon: Icon.home },
    { id: "dial", label: "Dial Customer", icon: Icon.phone },
    { id: "add", label: "Add Agent", icon: Icon.userPlus },
    { id: "logs", label: "Call Logs", icon: Icon.list },
  ];

  /* ---------- Reusable bits ---------- */
  const label = "block text-xs font-medium uppercase tracking-wide text-slate-500 mb-1.5";
  const input = `w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-slate-900 placeholder-slate-400 outline-none ${th.focusClasses} transition`;
  const card = "bg-white rounded-2xl border border-slate-200 shadow-sm";

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800 flex">
      {/* ---------------- Sidebar ---------------- */}
      <aside className="w-64 shrink-0 bg-white border-r border-slate-200 flex flex-col p-4 sticky top-0 h-screen">
        <div className="flex items-center gap-3 px-2 py-3 mb-4">
          <div className={`w-10 h-10 rounded-xl ${th.iconBadge} text-white grid place-items-center shadow-md`}>
            {Icon.phone()}
          </div>
          <div className="leading-tight">
            <div className="font-bold text-slate-900">Kodachadri</div>
            <div className="text-[11px] uppercase tracking-widest text-slate-400">Chit Fund</div>
          </div>
        </div>

        <nav className="flex flex-col gap-1">
          {nav.map((n) => (
            <button
              key={n.id}
              onClick={() => setPage(n.id)}
              className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
                page === n.id
                  ? th.navActive
                  : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"
              }`}
            >
              {n.icon("shrink-0")}
              <span>{n.label}</span>
              {n.id === "add" && !canAddAgent && <span className="ml-auto text-slate-300">{Icon.lock()}</span>}
            </button>
          ))}
        </nav>

        <div className="mt-auto">
          <div className="rounded-xl bg-slate-50 border border-slate-200 p-3 mb-3">
            <div className="text-[11px] uppercase tracking-wide text-slate-400">Signed in as</div>
            <div className="font-semibold text-slate-900">{ROLES[role].label}</div>
          </div>
          <button
            onClick={() => { setRole(null); setPage("home"); }}
            className="w-full flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50 transition"
          >
            {Icon.logout()} Sign out
          </button>
          <div className="flex items-center gap-2 justify-center mt-3 text-xs text-slate-400">
            <span className="w-2 h-2 rounded-full bg-emerald-500" /> Twilio · Live
          </div>
        </div>
      </aside>

      {/* ---------------- Main ---------------- */}
      <main className="flex-1 min-w-0 px-8 py-8 max-w-6xl">
        {/* ===== DASHBOARD / WELCOME ===== */}
        {page === "home" && (
          <div>
            <div className={`rounded-2xl bg-linear-to-r ${th.bannerGradient} text-white p-8 shadow-lg mb-6`}>
              <p className="text-white/70 text-sm font-medium">Welcome back, {ROLES[role].label}</p>
              <h1 className="text-3xl font-bold mt-1">Kodachadri Chit Fund</h1>
              <p className="text-white/80 mt-2 max-w-xl">
                Your click-to-call &amp; call-recording control center. Place bridged calls to customers, track live call status, and review recordings — all in one place.
              </p>
              <div className="flex gap-3 mt-5">
                <button onClick={() => setPage("dial")} className={`rounded-lg bg-white ${th.bannerBtnText} font-semibold px-4 py-2.5 text-sm hover:bg-slate-50 transition`}>Dial a customer</button>
                <button onClick={() => setPage("logs")} className="rounded-lg bg-white/20 text-white font-semibold px-4 py-2.5 text-sm hover:bg-white/30 transition ring-1 ring-white/30">View call logs</button>
              </div>
            </div>

            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {[
                { label: "Agents", value: agents.length },
                { label: "Total Calls", value: calls.length },
                { label: "Completed", value: completedCount },
                { label: "Recorded", value: recordedCount },
              ].map((s) => (
                <div key={s.label} className={`${card} p-5`}>
                  <div className={`text-3xl font-bold ${th.accentText}`}>{s.value}</div>
                  <div className="text-sm text-slate-500 mt-1">{s.label}</div>
                </div>
              ))}
            </div>

            <div className={`${card} p-6 mt-6`}>
              <h2 className="font-semibold text-slate-900 mb-2">How it works</h2>
              <ol className="text-sm text-slate-600 space-y-1.5 list-decimal list-inside">
                <li>Pick an agent and enter a customer number on the <button onClick={() => setPage("dial")} className={`${th.accentText} hover:underline`}>Dial Customer</button> page.</li>
                <li>The agent's phone rings first, then the customer is connected automatically.</li>
                <li>The call is recorded in the cloud and appears under <button onClick={() => setPage("logs")} className={`${th.accentText} hover:underline`}>Call Logs</button> with live status and playback.</li>
              </ol>
            </div>
          </div>
        )}

        {/* ===== DIAL CUSTOMER ===== */}
        {page === "dial" && (
          <div className="max-w-xl mx-auto">
            <h1 className="text-2xl font-bold text-slate-900 mb-1 text-center">Dial Customer</h1>
            <p className="text-slate-500 mb-6 text-center">Place a recorded bridge call to a customer.</p>

            <div className={`${card} p-6`}>
              <div className="mb-4">
                <label className={label}>Calling as</label>
                <select value={agentId} onChange={(e) => setAgentId(e.target.value)} className={input}>
                  {agents.length === 0 && <option value="">No agents yet</option>}
                  {agents.map((a) => (
                    <option key={a.agentId} value={a.agentId}>{a.agentId} · {a.agentName} · {a.agentPhoneNumber}</option>
                  ))}
                </select>
              </div>

              <div className="mb-2">
                <label className={label}>Customer number</label>
                <input type="tel" placeholder="9677736075" value={customerNumber} onChange={(e) => setCustomerNumber(e.target.value)} className={`${input} font-mono text-lg tracking-wide`} />
              </div>
              <p className={`text-xs font-mono ${th.accentText} mb-5 h-4`}>{livePreview}</p>

              <button onClick={handleDial} disabled={loading} className={`w-full flex items-center justify-center gap-2 rounded-lg ${th.accentBg} disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold px-4 py-3 transition shadow-md`}>
                {Icon.phone("")} {loading ? "Connecting…" : "Dial Customer"}
              </button>

              {message && <p className={`mt-4 rounded-lg ${th.msgBox} text-sm px-4 py-3`}>{message}</p>}
            </div>
          </div>
        )}

        {/* ===== ADD AGENT ===== */}
        {page === "add" && (
          <div className="max-w-xl mx-auto">
            <h1 className="text-2xl font-bold text-slate-900 mb-1 text-center">Add Agent</h1>
            <p className="text-slate-500 mb-6 text-center">Register a new agent who can place calls.</p>

            {!canAddAgent && (
              <div className="mb-5 rounded-xl bg-amber-50 ring-1 ring-amber-200 text-amber-800 px-4 py-3 flex items-start gap-3">
                <span className="mt-0.5">{Icon.lock("")}</span>
                <p className="text-sm">Your role (<b>{ROLES[role].label}</b>) can view this page but does not have permission to add agents. Only a Supervisor or Manager can add agents.</p>
              </div>
            )}

            <div className={`${card} p-6 ${!canAddAgent ? "opacity-70" : ""}`}>
              <div className="mb-4">
                <label className={label}>Agent ID</label>
                <input type="text" placeholder="agent2" value={newAgentId} onChange={(e) => setNewAgentId(e.target.value)} disabled={!canAddAgent} className={`${input} disabled:bg-slate-100 disabled:cursor-not-allowed`} />
              </div>
              <div className="mb-4">
                <label className={label}>Name</label>
                <input type="text" placeholder="Priya" value={newAgentName} onChange={(e) => setNewAgentName(e.target.value)} disabled={!canAddAgent} className={`${input} disabled:bg-slate-100 disabled:cursor-not-allowed`} />
              </div>
              <div className="mb-5">
                <label className={label}>Phone number</label>
                <input type="tel" placeholder="9677006726" value={newAgentPhone} onChange={(e) => setNewAgentPhone(e.target.value)} disabled={!canAddAgent} className={`${input} disabled:bg-slate-100 disabled:cursor-not-allowed`} />
              </div>

              <button onClick={handleAddAgent} disabled={!canAddAgent || addingAgent} className={`w-full rounded-lg ${th.accentBg} disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold px-4 py-3 transition shadow-md`}>
                {addingAgent ? "Adding…" : canAddAgent ? "Add Agent" : "Add Agent (no permission)"}
              </button>

              {message && <p className={`mt-4 rounded-lg ${th.msgBox} text-sm px-4 py-3`}>{message}</p>}
            </div>
          </div>
        )}

        {/* ===== CALL LOGS ===== */}
        {page === "logs" && (
          <div>
            <div className="flex items-baseline justify-between mb-6">
              <div>
                <h1 className="text-2xl font-bold text-slate-900 mb-1">Call Logs</h1>
                <p className="text-slate-500">Live call status and recordings.</p>
              </div>
              <span className="text-sm text-slate-400">{calls.length} {calls.length === 1 ? "call" : "calls"}</span>
            </div>

            {calls.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 p-12 text-center text-slate-400">
                No calls yet. Place your first call from the Dial Customer page.
              </div>
            ) : (
              <div className="grid gap-3">
                {calls.map((c) => (
                  <div key={c.id} className={`${card} p-4`}>
                    <div className="flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <div className="font-mono text-slate-900 font-medium">{c.customerNumber}</div>
                        <div className="text-xs text-slate-500 mt-0.5">#{c.id} · {c.agentId} · {c.startTime ? c.startTime.replace("T", " ").slice(0, 19) : "-"}</div>
                      </div>
                      <span className={`text-[11px] font-semibold uppercase tracking-wide px-2.5 py-1 rounded-full ring-1 ${statusStyle(c.status)}`}>{c.status}</span>
                    </div>
                    <div className="mt-3">
                      {c.recordingUrl ? (
                        <audio controls src={resolveRecordingUrl(c.recordingUrl)} className="w-full h-9" />
                      ) : (
                        <button onClick={() => checkRecording(c.id)} disabled={checkingId === c.id} className={`text-sm font-medium ${th.checkBtn} rounded-lg px-3 py-1.5 transition disabled:opacity-50`}>
                          {checkingId === c.id ? "Checking…" : "Check recording"}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
