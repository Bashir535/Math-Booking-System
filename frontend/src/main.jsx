import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";

let csrf = "";
async function api(path, options = {}) {
  const response = await fetch("/api" + path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": csrf,
      ...options.headers,
    },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.detail || "Something went wrong. Please try again.");
  }
  return response.status === 204 ||
    response.headers.get("content-length") === "0"
    ? null
    : response.text().then((t) => (t ? JSON.parse(t) : null));
}
const dateTime = (value) =>
  new Intl.DateTimeFormat("en-US", {
    timeZone: "America/Los_Angeles",
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
function App() {
  const [catalog, setCatalog] = useState({ providers: [], services: [] }),
    [user, setUser] = useState(null),
    [view, setView] = useState("browse");
  const [slots, setSlots] = useState([]),
    [appointments, setAppointments] = useState([]),
    [filters, setFilters] = useState({
      providerId: "",
      serviceId: "",
      date: "",
    }),
    [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null),
    [notice, setNotice] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [refresh, setRefresh] = useState(0);
  const provider = user?.role === "PROVIDER";
  useEffect(() => {
    Promise.all([api("/home"), api("/auth/session")])
      .then(([home, session]) => {
        setCatalog(home);
        csrf = session.csrf;
        setUser(session.user.id ? session.user : null);
      })
      .catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    let current = true;
    setLoading(true);
    const params = new URLSearchParams({
      page,
      size: 6,
      ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v)),
    });
    api("/slots?" + params)
      .then((data) => {
        if (current) setSlots(data);
      })
      .catch((e) => {
        if (current) setError(e.message);
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [filters, page, refresh]);
  useEffect(() => {
    let current = true;
    if (!user) {
      setAppointments([]);
      return;
    }
    api(`/${provider ? "provider" : "customer"}/appointments`)
      .then((data) => {
        if (current) setAppointments(data);
      })
      .catch((e) => {
        if (current) setError(e.message);
      });
    return () => {
      current = false;
    };
  }, [user, provider, refresh]);
  async function action(work) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await work();
      setRefresh((v) => v + 1);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function login(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    await action(async () => {
      const account = await api("/auth/login", {
        method: "POST",
        body: JSON.stringify(Object.fromEntries(data)),
      });
      setUser(account);
      setView(selected && account.role === "CUSTOMER" ? "booking" : "browse");
      setNotice("Welcome, " + account.fullName + ".");
    });
  }
  async function logout() {
    await action(async () => {
      await api("/auth/logout", { method: "POST" });
      setUser(null);
      setSelected(null);
      setView("browse");
      const session = await api("/auth/session");
      csrf = session.csrf;
    });
  }
  async function book(event) {
    event.preventDefault();
    await action(async () => {
      const result = await api("/customer/appointments", {
        method: "POST",
        body: JSON.stringify({
          slotId: selected.id,
          serviceId: selected.serviceId,
        }),
      });
      setNotice(
        `Appointment ${result.id} confirmed. Your session is saved in My appointments.`,
      );
      setSelected(null);
      setView("appointments");
    });
  }
  async function create(event) {
    event.preventDefault();
    const form = event.currentTarget,
      data = new FormData(form);
    await action(async () => {
      await api("/provider/slots", {
        method: "POST",
        body: JSON.stringify({
          serviceId: Number(data.get("serviceId")),
          startsAt: new Date(data.get("startsAt")).toISOString(),
        }),
      });
      setNotice("Your availability has been added.");
      form.reset();
    });
  }
  const ownProvider = catalog.providers.find((p) => p.id === user?.providerId);
  return (
    <>
      <header>
        <a className="brand" href="#" onClick={() => setView("browse")}>
          <span className="mark">m</span> Math Booking <small>SYSTEM</small>
        </a>
        <nav aria-label="Main navigation">
          <button
            className={view === "browse" ? "active" : ""}
            onClick={() => setView("browse")}
          >
            Find a session
          </button>
          {user && (
            <button
              className={view === "appointments" ? "active" : ""}
              onClick={() => setView("appointments")}
            >
              {provider ? "Student appointments" : "My appointments"}
            </button>
          )}
          {provider && (
            <button
              onClick={() => {
                setFilters({
                  providerId: String(user.providerId),
                  serviceId: "",
                  date: "",
                });
                setPage(0);
                setView("availability");
              }}
            >
              Manage availability
            </button>
          )}
          {user ? (
            <button onClick={logout} disabled={busy}>
              Sign out
            </button>
          ) : (
            <button className="primary" onClick={() => setView("login")}>
              Sign in
            </button>
          )}
        </nav>
      </header>
      <main>
        <div aria-live="polite">
          {notice && <p className="notice">{notice}</p>}
        </div>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {view === "browse" && (
          <>
            <section className="hero">
              <div>
                <p className="eyebrow">A LITTLE GUIDANCE. A LOT OF PROGRESS.</p>
                <h1>
                  Make room for
                  <br />
                  <em>your next breakthrough.</em>
                </h1>
                <p>
                  Find the right tutor and a time that works for you.
                  <br />
                  Algebra, geometry, and calculus. One session at a time.
                </p>
                <div className="tags">
                  <span>Personal attention</span>
                  <span>60 minute sessions</span>
                  <span>Clear pricing</span>
                </div>
              </div>
              <aside className="equation" aria-hidden="true">
                <span>LET’S WORK IT OUT</span>
                <strong>f(x) = progress</strong>
                <div>understand + practice = confidence</div>
                <i>∫</i>
              </aside>
            </section>
            <section>
              <div className="sectionTitle">
                <div>
                  <p className="eyebrow">YOUR NEXT STEP</p>
                  <h2>Available sessions</h2>
                </div>
                <p>All times shown in Pacific Time</p>
              </div>
              <div className="filters">
                <label>
                  Tutor
                  <select
                    value={filters.providerId}
                    onChange={(e) => {
                      setFilters({ ...filters, providerId: e.target.value });
                      setPage(0);
                    }}
                  >
                    <option value="">All tutors</option>
                    {catalog.providers.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.displayName}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Subject
                  <select
                    value={filters.serviceId}
                    onChange={(e) => {
                      setFilters({ ...filters, serviceId: e.target.value });
                      setPage(0);
                    }}
                  >
                    <option value="">All subjects</option>
                    {catalog.services.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Date
                  <input
                    type="date"
                    value={filters.date}
                    onChange={(e) => {
                      setFilters({ ...filters, date: e.target.value });
                      setPage(0);
                    }}
                  />
                </label>
                <button
                  onClick={() => {
                    setFilters({ providerId: "", serviceId: "", date: "" });
                    setPage(0);
                  }}
                >
                  Clear filters
                </button>
              </div>
              {loading ? (
                <p>Loading sessions…</p>
              ) : (
                <div className="grid">
                  {slots.map((slot) => (
                    <article className="card" key={slot.id}>
                      <div className="cardTop">
                        <span className="pill">{slot.serviceName}</span>
                        <strong>
                          ${slot.price}
                          <small> / session</small>
                        </strong>
                      </div>
                      <h3>{slot.providerName}</h3>
                      <p>{dateTime(slot.startsAt)}</p>
                      <p className="muted">60 minutes · Individual tutoring</p>
                      {!provider && (
                        <button
                          className="primary full"
                          onClick={() => {
                            setSelected(slot);
                            setView(user ? "booking" : "login");
                          }}
                        >
                          Choose session <span>↗</span>
                        </button>
                      )}
                    </article>
                  ))}
                </div>
              )}
              {!loading && !slots.length && (
                <div className="empty">
                  No sessions match your filters. Try another date or tutor.
                </div>
              )}
              <div className="pagination">
                <button
                  disabled={page === 0 || loading}
                  onClick={() => setPage((p) => p - 1)}
                >
                  Previous
                </button>
                <span>Page {page + 1}</span>
                <button
                  disabled={slots.length < 6 || loading}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </button>
              </div>
            </section>
          </>
        )}
        {view === "login" && (
          <section className="panel">
            <p className="eyebrow">WELCOME BACK</p>
            <h1>Sign in</h1>
            <p>Use your student or tutor account to continue.</p>
            <form onSubmit={login}>
              <label>
                Username
                <input
                  name="username"
                  autoComplete="username"
                  required
                  maxLength="80"
                />
              </label>
              <label>
                Password
                <input
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  maxLength="72"
                />
              </label>
              <button className="primary" disabled={busy}>
                {busy ? "Signing in…" : "Sign in"}
              </button>
            </form>
          </section>
        )}
        {view === "booking" && selected && (
          <section className="panel">
            <p className="eyebrow">ONE LAST LOOK</p>
            <h1>Confirm your session</h1>
            <h2>
              {selected.serviceName} with {selected.providerName}
            </h2>
            <p>{dateTime(selected.startsAt)} Pacific Time</p>
            <p>60 minutes · ${selected.price}</p>
            <form onSubmit={book}>
              <label>
                <input type="checkbox" required /> I have reviewed the tutor,
                subject, and time.
              </label>
              <button className="primary" disabled={busy}>
                {busy ? "Booking…" : "Confirm appointment"}
              </button>
              <button
                type="button"
                onClick={() => {
                  setView("browse");
                  setSelected(null);
                }}
              >
                Go back
              </button>
            </form>
          </section>
        )}
        {view === "appointments" && (
          <section>
            <p className="eyebrow">YOUR SCHEDULE</p>
            <h1>{provider ? "Student appointments" : "My appointments"}</h1>
            {["Upcoming", "History"].map((group) => {
              const list = appointments.filter(
                (a) =>
                  (a.status === "BOOKED" && new Date(a.endsAt) > new Date()) ===
                  (group === "Upcoming"),
              );
              return (
                <div key={group}>
                  <h2>{group}</h2>
                  {!list.length && (
                    <p className="empty">No appointments here yet.</p>
                  )}
                  {list.map((a) => (
                    <article className="appointment" key={a.id}>
                      <div>
                        <span className="pill">{a.status}</span>
                        <h3>
                          {a.serviceName} with{" "}
                          {provider ? a.customerName : a.providerName}
                        </h3>
                        <p>{dateTime(a.startsAt)} Pacific Time</p>
                      </div>
                      {!provider &&
                        a.status === "BOOKED" &&
                        new Date(a.startsAt) > new Date() && (
                          <button
                            disabled={busy}
                            onClick={() => {
                              if (window.confirm("Cancel this appointment?"))
                                action(async () => {
                                  await api("/customer/appointments/" + a.id, {
                                    method: "DELETE",
                                  });
                                  setNotice("Appointment cancelled.");
                                });
                            }}
                          >
                            Cancel appointment
                          </button>
                        )}
                    </article>
                  ))}
                </div>
              );
            })}
          </section>
        )}
        {view === "availability" && provider && (
          <section>
            <p className="eyebrow">TUTOR WORKSPACE</p>
            <h1>Manage availability</h1>
            <div className="panel">
              <h2>Add a session</h2>
              <form onSubmit={create}>
                <label>
                  Subject
                  <select name="serviceId" required>
                    <option value="">Choose a subject</option>
                    {catalog.services.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Start time in your device’s timezone
                  <input name="startsAt" type="datetime-local" required />
                </label>
                <p>
                  Sessions last 60 minutes. Existing bookings cannot be removed.
                </p>
                <button className="primary" disabled={busy}>
                  Add availability
                </button>
              </form>
            </div>
            <h2>Remove an available session</h2>
            <p>Use the tutor filter to see your available sessions.</p>
            <button
              onClick={() => {
                setFilters({
                  providerId: String(ownProvider?.id || ""),
                  serviceId: "",
                  date: "",
                });
                setPage(0);
              }}
            >
              Show my sessions
            </button>
            {slots
              .filter((s) => s.providerId === ownProvider?.id)
              .map((s) => (
                <article className="appointment" key={s.id}>
                  <div>
                    <h3>{s.serviceName}</h3>
                    <p>{dateTime(s.startsAt)} Pacific Time</p>
                  </div>
                  <button
                    disabled={busy}
                    onClick={() => {
                      if (window.confirm("Remove this availability?"))
                        action(async () => {
                          await api("/provider/slots/" + s.id, {
                            method: "DELETE",
                          });
                          setNotice("Availability removed.");
                        });
                    }}
                  >
                    Remove
                  </button>
                </article>
              ))}
            <div className="pagination">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </button>
              <span>Page {page + 1}</span>
              <button
                disabled={slots.length < 6}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </section>
        )}
      </main>
      <footer>
        <strong>Math Booking System</strong>
        <span>A little practice goes a long way.</span>
      </footer>
    </>
  );
}
createRoot(document.getElementById("root")).render(<App />);
