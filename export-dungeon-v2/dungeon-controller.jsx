// dungeon-controller.jsx — Player + Admin Dungeon Controller GUI.
// Single interactive prototype; the "ui" tweak flips between the two screens.
// Both screens share the same shell (header + tab bar + body + footer).

const { useState, useEffect, useMemo, useCallback, useRef } = React;

// =========================================================================
//                          STATIC FIXTURE DATA
// =========================================================================

const SAMPLE_LOBBIES = [
  { id: 'lob_steve',  owner: 'Steve',   tier: 'Hard',   members: 3, max: 4, vis: 'PUBLIC',  hc: false, status: 'WAITING' },
  { id: 'lob_alex',   owner: 'Alex',    tier: 'Normal', members: 1, max: 4, vis: 'PUBLIC',  hc: false, status: 'WAITING' },
  { id: 'lob_bob',    owner: 'Bob',     tier: 'Hell',   members: 4, max: 4, vis: 'FRIENDS', hc: true,  status: 'READY' },
  { id: 'lob_maria',  owner: 'Maria',   tier: 'Normal', members: 2, max: 4, vis: 'PRIVATE', hc: false, status: 'WAITING' },
  { id: 'lob_zed',    owner: 'Zed',     tier: 'Hell',   members: 4, max: 4, vis: 'PUBLIC',  hc: true,  status: 'IN_RUN' },
  { id: 'lob_kira',   owner: 'Kira',    tier: 'Hard',   members: 2, max: 4, vis: 'PUBLIC',  hc: false, status: 'WAITING' },
];

const SAMPLE_INVITES = [
  { id: 'i1', inviter: 'Bob',   owner: 'Bob',   tier: 'Hell',   expires: 38 },
  { id: 'i2', inviter: 'Maria', owner: 'Maria', tier: 'Normal', expires: 114 },
];

const SAMPLE_INSTANCES = [
  { id: 1, pos: 'X 120 · Z -64',  status: 'RUNNING',  party: 'Steve party',  cd: 0,    tier: 'Hard' },
  { id: 2, pos: 'X 184 · Z -64',  status: 'COOLDOWN', party: null,           cd: 215,  tier: 'Normal' },
  { id: 3, pos: 'X 248 · Z -64',  status: 'IDLE',     party: null,           cd: 0,    tier: null },
  { id: 4, pos: 'X 312 · Z -64',  status: 'PENDING',  party: 'Bob party',    cd: 0,    tier: 'Hell' },
  { id: 5, pos: 'X 376 · Z -64',  status: 'IDLE',     party: null,           cd: 0,    tier: null },
  { id: 6, pos: 'X 440 · Z -64',  status: 'RUNNING',  party: 'Kira party',   cd: 0,    tier: 'Hard' },
];

const SAMPLE_LEADERBOARD = {
  normal: [
    { rank: 1, name: 'Steve',    time: '04:12' },
    { rank: 2, name: 'Alex',     time: '05:33' },
    { rank: 3, name: 'Maria',    time: '06:01' },
    { rank: 4, name: 'Bob',      time: '07:22' },
    { rank: 5, name: 'Kira',     time: '08:14' },
    { rank: 6, name: 'Zed',      time: '09:01' },
  ],
  hard: [
    { rank: 1, name: 'Maria',    time: '06:55' },
    { rank: 2, name: 'Steve',    time: '07:18' },
    { rank: 3, name: 'Alex',     time: '08:44' },
    { rank: 4, name: 'Bob',      time: '09:10' },
    { rank: 5, name: 'Kira',     time: '10:24' },
  ],
  hell: [
    { rank: 1, name: 'Alex',     time: '09:30' },
    { rank: 2, name: 'Steve',    time: '10:02' },
    { rank: 3, name: 'Maria',    time: '11:15' },
    { rank: 4, name: 'Zed',      time: '12:40' },
  ],
};

const TIER_C = {
  Normal: { c: SLATE.info,    label: 'Normal' },
  Hard:   { c: SLATE.warn,    label: 'Hard' },
  Hell:   { c: SLATE.danger,  label: 'Hell' },
};

const VIS_C = {
  PUBLIC:  { c: SLATE.good,  label: 'Public',  icon: '◐' },
  FRIENDS: { c: SLATE.info,  label: 'Friends', icon: '◑' },
  PRIVATE: { c: SLATE.warn,  label: 'Private', icon: '●' },
};

const STATUS_C = {
  WAITING:  { c: SLATE.info,   label: 'Waiting' },
  READY:    { c: SLATE.good,   label: 'Ready'   },
  IN_RUN:   { c: SLATE.warn,   label: 'In Run'  },
};

const INST_C = {
  RUNNING:  { c: SLATE.good,   label: 'Running'  },
  COOLDOWN: { c: SLATE.warn,   label: 'Cooldown' },
  PENDING:  { c: SLATE.warn,   label: 'Pending Removal' },
  IDLE:     { c: SLATE.inkMid, label: 'Idle' },
};

// =========================================================================
//                              UTILITIES
// =========================================================================

const fmtTime = (s) => `${String(Math.floor(s/60)).padStart(1, '0')}:${String(s%60).padStart(2, '0')}`;

// =========================================================================
//                            SHARED CHROME
// =========================================================================

function ControllerHeader({ accent, title, isAdmin, pos, status, onClose }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 14,
      padding: '12px 18px',
      borderBottom: `1px solid ${SLATE.hairline}`,
      background: SLATE.panel2,
    }}>
      {/* Brand mark — different glyph for admin */}
      <div style={{
        width: 32, height: 32, flex: '0 0 auto',
        background: isAdmin ? SLATE.warn : accent.mid,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: 'inset 0 0 0 2px #1a1208',
      }}>
        <div style={{
          fontFamily: slateFonts.pixel, fontSize: 10,
          color: isAdmin ? '#1a1208' : accent.ink,
        }}>{isAdmin ? '⚙' : '◆'}</div>
      </div>
      <div style={{ flex: '1 1 auto', minWidth: 0 }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2,
        }}>
          <span style={{ fontSize: 15, fontWeight: 700, letterSpacing: 0.5, color: SLATE.ink }}>
            {title}
          </span>
          {isAdmin && <SlateTag color={SLATE.warn} size="sm">OP</SlateTag>}
          {status && !isAdmin && (
            <SlateTag color={status.c} size="sm">{status.label}</SlateTag>
          )}
        </div>
        <div style={{
          fontSize: 11, color: SLATE.inkDim, fontFamily: slateFonts.mono,
          display: 'flex', gap: 14, alignItems: 'center',
        }}>
          <span>POS · <span style={{ color: SLATE.inkMid }}>{pos}</span></span>
          {/* tiny live ping dot to suggest live updates */}
          <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <span style={{
              width: 6, height: 6, background: SLATE.good,
              borderRadius: '50%',
              animation: 'sl-pulse 1.4s ease-in-out infinite',
            }} />
            LIVE
          </span>
        </div>
      </div>
      <SlateBtn tone="ghost" icon size="md" accent={accent} onClick={onClose} title="Close">✕</SlateBtn>
    </div>
  );
}

function Footer({ accent, left, right, error }) {
  return (
    <div style={{
      padding: '10px 18px',
      borderTop: `1px solid ${SLATE.hairline}`,
      background: SLATE.panel2,
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      gap: 12,
      minHeight: 50,
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        {error ? (
          <div style={{
            fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.danger,
            letterSpacing: 0.5,
            display: 'flex', alignItems: 'center', gap: 6,
          }}>
            <span style={{ fontSize: 14 }}>⚠</span> {error}
          </div>
        ) : left}
      </div>
      <div style={{ display: 'flex', gap: 6 }}>
        {right}
      </div>
    </div>
  );
}

// =========================================================================
//                           PLAYER CONTROLLER
// =========================================================================

// ----- Lobbies tab ---------------------------------------------------------

function LobbyRow({ lobby, accent, currentName, myLobbyId, busy, onJoin, onRequest }) {
  const tier = TIER_C[lobby.tier];
  const vis = VIS_C[lobby.vis];
  const status = STATUS_C[lobby.status];
  const isMine = lobby.id === myLobbyId;
  const full = lobby.members >= lobby.max;
  const inRun = lobby.status === 'IN_RUN';

  const canJoin = lobby.vis === 'PUBLIC' && !full && !inRun && !isMine && !busy;
  const canRequest = (lobby.vis === 'FRIENDS' || lobby.vis === 'PRIVATE') && !full && !inRun && !isMine && !busy;

  let actionLabel = null, actionTone = null, actionDisabled = false, actionFn = null;
  if (isMine) { actionLabel = 'Your Lobby'; actionTone = 'ghost'; actionDisabled = true; }
  else if (inRun) { actionLabel = 'Running'; actionTone = 'ghost'; actionDisabled = true; }
  else if (full)  { actionLabel = 'Full';    actionTone = 'ghost'; actionDisabled = true; }
  else if (canJoin) { actionLabel = 'Join'; actionTone = 'primary'; actionFn = () => onJoin(lobby); }
  else if (canRequest) { actionLabel = 'Request'; actionTone = 'accent'; actionFn = () => onRequest(lobby); }

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr 80px 110px 70px 110px 130px',
      gap: 12,
      padding: '10px 18px',
      alignItems: 'center',
      background: SLATE.rowBg,
      borderLeft: isMine ? `2px solid ${accent.mid}` : `2px solid transparent`,
      borderBottom: `1px solid ${SLATE.panel2}`,
      opacity: inRun ? 0.55 : 1,
    }}>
      {/* Owner */}
      <div style={{ minWidth: 0 }}>
        <div style={{
          fontSize: 14, fontWeight: 600, color: SLATE.ink,
          display: 'flex', alignItems: 'center', gap: 6,
        }}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {lobby.owner}
          </span>
          {isMine && <SlateTag color={accent.mid} size="sm">You</SlateTag>}
          {lobby.hc && <SlateTag color={SLATE.danger} size="sm">HC</SlateTag>}
        </div>
        <div style={{
          fontSize: 10, color: SLATE.inkDim, fontFamily: slateFonts.mono,
          letterSpacing: 1, textTransform: 'uppercase', marginTop: 2,
        }}>{lobby.id}</div>
      </div>
      {/* Members */}
      <div style={{
        textAlign: 'right', fontFamily: slateFonts.mono,
        fontSize: 14, color: SLATE.ink, fontWeight: 600,
      }}>
        {lobby.members}<span style={{ color: SLATE.inkDim }}>/{lobby.max}</span>
      </div>
      {/* Tier */}
      <div><SlateTag color={tier.c}>{tier.label}</SlateTag></div>
      {/* HC marker — already on owner line; here we leave blank or show empty cell */}
      <div style={{
        fontFamily: slateFonts.mono, fontSize: 10,
        color: SLATE.inkDim, letterSpacing: 1, textTransform: 'uppercase',
      }}>
        <span style={{ color: vis.c }}>{vis.icon}</span> {vis.label}
      </div>
      {/* Status */}
      <div><SlateTag color={status.c}>{status.label}</SlateTag></div>
      {/* Action */}
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        {actionLabel && (
          <SlateBtn
            tone={actionTone}
            size="sm"
            accent={accent}
            disabled={actionDisabled}
            onClick={actionFn}
            style={{ minWidth: 90 }}
          >{actionLabel}</SlateBtn>
        )}
      </div>
    </div>
  );
}

function LobbyColumns() {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr 80px 110px 70px 110px 130px',
      gap: 12,
      padding: '8px 18px',
      fontFamily: slateFonts.mono, fontSize: 10,
      color: SLATE.inkDim, letterSpacing: 1.5, textTransform: 'uppercase',
      borderBottom: `1px solid ${SLATE.hairline}`,
      alignItems: 'center',
      background: SLATE.panel2,
    }}>
      <span>Owner</span>
      <span style={{ textAlign: 'right' }}>Members</span>
      <span>Tier</span>
      <span></span>
      <span>Status</span>
      <span></span>
    </div>
  );
}

function InviteRow({ inv, accent, busy, onAccept, onDecline }) {
  const [seconds, setSeconds] = useState(inv.expires);
  useEffect(() => {
    if (seconds <= 0) return;
    const t = setTimeout(() => setSeconds(s => s - 1), 1000);
    return () => clearTimeout(t);
  }, [seconds]);
  const expired = seconds <= 0;
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr 80px 110px 80px 180px',
      gap: 12,
      padding: '10px 18px',
      alignItems: 'center',
      background: SLATE.rowBg,
      borderLeft: `2px solid ${accent.mid}`,
      borderBottom: `1px solid ${SLATE.panel2}`,
      opacity: expired ? 0.4 : 1,
    }}>
      <div>
        <div style={{ fontSize: 13, color: SLATE.ink, fontWeight: 600 }}>
          From <span style={{ color: accent.mid }}>{inv.inviter}</span>
        </div>
        <div style={{
          fontSize: 10, color: SLATE.inkDim, fontFamily: slateFonts.mono,
          marginTop: 2, letterSpacing: 1, textTransform: 'uppercase',
        }}>Lobby owner: {inv.owner}</div>
      </div>
      <div style={{ fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.inkMid, textAlign: 'right' }}>—</div>
      <div><SlateTag color={TIER_C[inv.tier].c}>{TIER_C[inv.tier].label}</SlateTag></div>
      <div style={{
        fontFamily: slateFonts.mono, fontSize: 13,
        color: expired ? SLATE.danger : (seconds < 30 ? SLATE.warn : SLATE.inkMid),
        fontVariantNumeric: 'tabular-nums',
      }}>
        {expired ? 'EXPIRED' : fmtTime(seconds)}
      </div>
      <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
        <SlateBtn tone="good" size="sm" accent={accent} disabled={expired || busy} onClick={() => onAccept(inv)}>Accept</SlateBtn>
        <SlateBtn tone="danger" size="sm" accent={accent} onClick={() => onDecline(inv)}>Decline</SlateBtn>
      </div>
    </div>
  );
}

function InviteColumns() {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr 80px 110px 80px 180px',
      gap: 12,
      padding: '8px 18px',
      fontFamily: slateFonts.mono, fontSize: 10,
      color: SLATE.inkDim, letterSpacing: 1.5, textTransform: 'uppercase',
      borderBottom: `1px solid ${SLATE.hairline}`,
      background: SLATE.panel2,
    }}>
      <span>From</span>
      <span></span>
      <span>Tier</span>
      <span>Expires</span>
      <span></span>
    </div>
  );
}

function LobbiesTab({ accent, currentName, myLobbyId, busy, invites, lobbies, onJoin, onRequest, onAccept, onDecline, onCreate }) {
  return (
    <div className="sm-noscroll" style={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
      {invites.length > 0 && (
        <>
          <SlateSection
            label="My Invites" accent={accent} count={invites.length}
          />
          <InviteColumns />
          {invites.map(inv => (
            <InviteRow
              key={inv.id} inv={inv} accent={accent} busy={busy}
              onAccept={onAccept} onDecline={onDecline}
            />
          ))}
        </>
      )}

      <SlateSection
        label="Available Lobbies" accent={accent} count={lobbies.length}
        action={
          <SlateBtn tone="primary" size="sm" accent={accent} onClick={onCreate} disabled={busy || myLobbyId}>
            ＋ Create Lobby
          </SlateBtn>
        }
      />
      <LobbyColumns />
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {lobbies.map(l => (
          <LobbyRow
            key={l.id} lobby={l} accent={accent}
            currentName={currentName} myLobbyId={myLobbyId} busy={busy}
            onJoin={onJoin} onRequest={onRequest}
          />
        ))}
      </div>
      {lobbies.length === 0 && (
        <SlateEmpty
          icon="∅"
          title="No lobbies open"
          body="Be the first to host. Tier, visibility and hardcore can all be tweaked after creating."
          accent={accent}
          action={<SlateBtn tone="primary" accent={accent} onClick={onCreate}>＋ Create Lobby</SlateBtn>}
        />
      )}
    </div>
  );
}

// ----- My Lobby tab --------------------------------------------------------

function MemberRow({ m, accent, isOwner, canKick, onKick }) {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '24px 1fr 90px 80px 90px',
      gap: 10,
      padding: '8px 18px',
      alignItems: 'center',
      background: SLATE.rowBg,
      borderBottom: `1px solid ${SLATE.panel2}`,
      opacity: m.online ? 1 : 0.55,
    }}>
      <div style={{
        width: 18, height: 18,
        background: m.online ? '#0e2818' : '#2a1c1c',
        border: `1px solid ${m.online ? SLATE.good : SLATE.danger}55`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: slateFonts.pixel, fontSize: 8,
        color: m.online ? SLATE.good : SLATE.danger,
      }}>{m.online ? '✓' : '!'}</div>
      <div style={{
        fontSize: 14, fontWeight: 600, color: m.isSelf ? accent.mid : SLATE.ink,
        display: 'flex', alignItems: 'center', gap: 6,
      }}>
        <span>{m.name}</span>
        {m.isOwner && <SlateTag color={accent.mid} size="sm">★ Owner</SlateTag>}
        {m.isSelf && !m.isOwner && <SlateTag color={accent.mid} size="sm">You</SlateTag>}
      </div>
      <div style={{
        fontFamily: slateFonts.mono, fontSize: 10,
        color: m.online ? SLATE.good : SLATE.danger,
        letterSpacing: 1.5, textTransform: 'uppercase',
      }}>{m.online ? '● Online' : '○ Offline'}</div>
      <div>
        <SlateTag color={m.ready ? SLATE.good : SLATE.inkDim}>
          {m.ready ? 'Ready' : 'Not Ready'}
        </SlateTag>
      </div>
      <div style={{ textAlign: 'right' }}>
        {canKick && !m.isOwner && (
          <SlateBtn tone="danger" size="sm" accent={accent} onClick={() => onKick(m)}>Kick</SlateBtn>
        )}
      </div>
    </div>
  );
}

function MyLobbyTab({
  accent, currentName, lobby, members, isOwner, isReady, allReady, freeInstance, busy,
  inviteValue, setInviteValue, onInvite, onKick,
  onToggleReady, onLeave, onStart, onTierChange, onVisChange, onHardcoreToggle,
}) {
  if (!lobby) {
    return (
      <SlateEmpty
        icon="∅"
        title="You're not in a lobby"
        body="Browse the Lobbies tab to join one, or create your own."
        accent={accent}
      />
    );
  }

  return (
    <div className="sm-noscroll" style={{ flex: 1, overflow: 'auto' }}>
      {/* Summary card */}
      <div style={{
        margin: '16px 18px 6px',
        padding: 14,
        background: SLATE.rowBg,
        border: `1px solid ${SLATE.hairline}`,
        borderLeft: `3px solid ${accent.mid}`,
        display: 'grid',
        gridTemplateColumns: 'repeat(5, 1fr)',
        gap: 14,
      }}>
        {[
          ['Owner', lobby.owner, accent.mid],
          ['Tier', TIER_C[lobby.tier].label, TIER_C[lobby.tier].c],
          ['Members', `${members.length} / ${lobby.max}`, SLATE.ink],
          ['Visibility', VIS_C[lobby.vis].label, VIS_C[lobby.vis].c],
          ['Hardcore', lobby.hc ? 'On' : 'Off', lobby.hc ? SLATE.danger : SLATE.inkMid],
        ].map(([label, value, color]) => (
          <div key={label}>
            <div style={{
              fontFamily: slateFonts.mono, fontSize: 9,
              color: SLATE.inkDim, letterSpacing: 1.5,
              textTransform: 'uppercase', marginBottom: 4,
            }}>{label}</div>
            <div style={{
              fontFamily: slateFonts.mono, fontSize: 14,
              color, fontWeight: 700, letterSpacing: 0.3,
            }}>{value}</div>
          </div>
        ))}
      </div>

      {/* Members */}
      <SlateSection label="Members" accent={accent} count={`${members.filter(m=>m.ready).length} ready · ${members.length} / ${lobby.max}`} />
      <div style={{
        display: 'grid',
        gridTemplateColumns: '24px 1fr 90px 80px 90px',
        gap: 10,
        padding: '6px 18px',
        fontFamily: slateFonts.mono, fontSize: 9,
        color: SLATE.inkDim, letterSpacing: 1.5, textTransform: 'uppercase',
        background: SLATE.panel2,
        borderBottom: `1px solid ${SLATE.hairline}`,
      }}>
        <span></span>
        <span>Name</span>
        <span>Connection</span>
        <span>Ready</span>
        <span></span>
      </div>
      {members.map(m => (
        <MemberRow key={m.name} m={m} accent={accent} canKick={isOwner} onKick={onKick} />
      ))}

      {/* Owner controls */}
      {isOwner && (
        <>
          <SlateSection label="Owner Controls" accent={accent} />
          <div style={{ padding: '4px 18px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {/* Tier selector */}
            <div>
              <div style={{
                fontFamily: slateFonts.mono, fontSize: 9,
                color: SLATE.inkDim, letterSpacing: 1.5,
                textTransform: 'uppercase', marginBottom: 6,
              }}>Tier</div>
              <div style={{ display: 'flex', gap: 4 }}>
                {['Normal', 'Hard', 'Hell'].map(t => {
                  const active = lobby.tier === t;
                  const c = TIER_C[t];
                  return (
                    <button
                      key={t}
                      onClick={() => onTierChange(t)}
                      style={{
                        flex: 1,
                        padding: '10px 12px',
                        background: active ? `${c.c}1c` : SLATE.panel2,
                        border: `1px solid ${active ? c.c : SLATE.hairlineHi}`,
                        borderLeft: active ? `3px solid ${c.c}` : `3px solid ${SLATE.hairlineHi}`,
                        color: active ? c.c : SLATE.inkMid,
                        fontFamily: slateFonts.sans, fontWeight: 600,
                        fontSize: 12, letterSpacing: 1.5, textTransform: 'uppercase',
                        cursor: 'pointer',
                      }}
                    >{t}</button>
                  );
                })}
              </div>
            </div>
            {/* Visibility selector */}
            <div>
              <div style={{
                fontFamily: slateFonts.mono, fontSize: 9,
                color: SLATE.inkDim, letterSpacing: 1.5,
                textTransform: 'uppercase', marginBottom: 6,
              }}>Visibility</div>
              <div style={{ display: 'flex', gap: 4 }}>
                {['PUBLIC', 'FRIENDS', 'PRIVATE'].map(v => {
                  const active = lobby.vis === v;
                  const cfg = VIS_C[v];
                  return (
                    <button
                      key={v}
                      onClick={() => onVisChange(v)}
                      style={{
                        flex: 1,
                        padding: '10px 12px',
                        background: active ? `${cfg.c}1c` : SLATE.panel2,
                        border: `1px solid ${active ? cfg.c : SLATE.hairlineHi}`,
                        borderLeft: active ? `3px solid ${cfg.c}` : `3px solid ${SLATE.hairlineHi}`,
                        color: active ? cfg.c : SLATE.inkMid,
                        fontFamily: slateFonts.sans, fontWeight: 600,
                        fontSize: 12, letterSpacing: 1.5, textTransform: 'uppercase',
                        cursor: 'pointer',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                      }}
                    >
                      <span style={{ color: cfg.c }}>{cfg.icon}</span>
                      {cfg.label}
                    </button>
                  );
                })}
              </div>
            </div>
            {/* Hardcore toggle */}
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '10px 14px',
              background: SLATE.panel2,
              border: `1px solid ${SLATE.hairlineHi}`,
              borderLeft: `3px solid ${lobby.hc ? SLATE.danger : SLATE.hairlineHi}`,
            }}>
              <div>
                <div style={{
                  fontFamily: slateFonts.mono, fontSize: 9,
                  color: SLATE.inkDim, letterSpacing: 1.5,
                  textTransform: 'uppercase', marginBottom: 2,
                }}>Hardcore</div>
                <div style={{ fontSize: 12, color: SLATE.inkMid }}>
                  Permadeath. No second attempts.
                </div>
              </div>
              <button
                onClick={onHardcoreToggle}
                style={{
                  width: 52, height: 26,
                  background: lobby.hc ? SLATE.danger : SLATE.hairline,
                  border: 'none',
                  position: 'relative',
                  cursor: 'pointer',
                  padding: 2,
                }}
              >
                <div style={{
                  width: 22, height: 22,
                  background: '#fff',
                  transform: `translateX(${lobby.hc ? 24 : 0}px)`,
                  transition: 'transform 150ms ease-out',
                }} />
              </button>
            </div>
            {/* Invite control */}
            <div>
              <div style={{
                fontFamily: slateFonts.mono, fontSize: 9,
                color: SLATE.inkDim, letterSpacing: 1.5,
                textTransform: 'uppercase', marginBottom: 6,
              }}>Invite player</div>
              <div style={{ display: 'flex', gap: 4 }}>
                <SlateInput
                  value={inviteValue}
                  onChange={e => setInviteValue(e.target.value)}
                  placeholder="Player name or UUID"
                  accent={accent}
                  mono
                  prefix="@"
                  style={{ flex: 1 }}
                />
                <SlateBtn
                  tone="primary"
                  accent={accent}
                  disabled={!inviteValue.trim() || members.length >= lobby.max}
                  onClick={onInvite}
                >Invite</SlateBtn>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

// =========================================================================
//                            ADMIN CONTROLLER
// =========================================================================

function InstancesTab({ accent, instances, onUp, onDown, onRemove }) {
  const active = instances.filter(i => i.status === 'RUNNING').length;
  return (
    <div className="sm-noscroll" style={{ flex: 1, overflow: 'auto' }}>
      {/* Summary */}
      <div style={{
        margin: '14px 18px 6px',
        padding: 14,
        background: SLATE.rowBg,
        border: `1px solid ${SLATE.hairline}`,
        borderLeft: `3px solid ${accent.mid}`,
        display: 'flex', gap: 24, alignItems: 'center',
      }}>
        <div>
          <div style={{
            fontFamily: slateFonts.mono, fontSize: 9, color: SLATE.inkDim,
            letterSpacing: 1.5, textTransform: 'uppercase', marginBottom: 4,
          }}>Active Runs</div>
          <div style={{
            fontFamily: slateFonts.mono, fontSize: 20, color: SLATE.good,
            fontWeight: 700,
          }}>{active}<span style={{ color: SLATE.inkDim, fontSize: 14 }}> / {instances.length}</span></div>
        </div>
        <div style={{ width: 1, height: 32, background: SLATE.hairline }} />
        <div>
          <div style={{
            fontFamily: slateFonts.mono, fontSize: 9, color: SLATE.inkDim,
            letterSpacing: 1.5, textTransform: 'uppercase', marginBottom: 4,
          }}>Idle / Cooldown</div>
          <div style={{ fontFamily: slateFonts.mono, fontSize: 20, color: SLATE.ink, fontWeight: 700 }}>
            {instances.filter(i => i.status === 'IDLE' || i.status === 'COOLDOWN').length}
          </div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={{
          fontSize: 11, color: SLATE.inkDim, fontFamily: slateFonts.mono,
          letterSpacing: 0.5, fontStyle: 'italic', maxWidth: 280, textAlign: 'right',
        }}>
          Add new instances with <span style={{ color: SLATE.warn }}>Linker</span> mode in the world.
        </div>
      </div>

      {/* Column header */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '40px 1fr 160px 140px 160px',
        gap: 12,
        padding: '8px 18px',
        fontFamily: slateFonts.mono, fontSize: 9,
        color: SLATE.inkDim, letterSpacing: 1.5, textTransform: 'uppercase',
        borderBottom: `1px solid ${SLATE.hairline}`,
        background: SLATE.panel2,
        alignItems: 'center',
      }}>
        <span>#</span>
        <span>Position</span>
        <span>Status</span>
        <span>Tier · Party</span>
        <span></span>
      </div>

      {instances.map((inst, idx) => {
        const cfg = INST_C[inst.status];
        return (
          <div key={inst.id} style={{
            display: 'grid',
            gridTemplateColumns: '40px 1fr 160px 140px 160px',
            gap: 12,
            padding: '10px 18px',
            alignItems: 'center',
            background: SLATE.rowBg,
            borderBottom: `1px solid ${SLATE.panel2}`,
          }}>
            <div style={{
              fontFamily: slateFonts.mono, fontSize: 16, color: SLATE.ink, fontWeight: 700,
            }}>{inst.id}</div>
            <div style={{
              fontFamily: slateFonts.mono, fontSize: 12, color: SLATE.inkMid, letterSpacing: 0.3,
            }}>{inst.pos}</div>
            <div>
              <SlateTag color={cfg.c}>
                {cfg.label}
                {inst.status === 'COOLDOWN' && (
                  <span style={{ marginLeft: 6, fontVariantNumeric: 'tabular-nums' }}>
                    {fmtTime(inst.cd)}
                  </span>
                )}
              </SlateTag>
            </div>
            <div style={{
              fontFamily: slateFonts.mono, fontSize: 11,
              color: SLATE.inkMid,
              display: 'flex', flexDirection: 'column', gap: 2,
            }}>
              {inst.tier && (
                <span style={{ color: TIER_C[inst.tier].c }}>{inst.tier}</span>
              )}
              <span style={{ color: inst.party ? SLATE.ink : SLATE.inkDim, fontStyle: inst.party ? 'normal' : 'italic' }}>
                {inst.party || '—'}
              </span>
            </div>
            <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
              <SlateBtn tone="solid" size="sm" accent={accent} disabled={idx === 0} onClick={() => onUp(inst)}>↑</SlateBtn>
              <SlateBtn tone="solid" size="sm" accent={accent} disabled={idx === instances.length - 1} onClick={() => onDown(inst)}>↓</SlateBtn>
              <SlateBtn
                tone={inst.status === 'PENDING' ? 'warn' : 'danger'}
                size="sm"
                accent={accent}
                disabled={inst.status === 'RUNNING'}
                onClick={() => onRemove(inst)}
              >
                {inst.status === 'PENDING' ? 'Cancel' : 'Remove'}
              </SlateBtn>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function GeneralTab({ accent, cfg, setCfg, onApply, applied }) {
  const [errors, setErrors] = useState({});
  const set = (k) => (v) => setCfg({ ...cfg, [k]: v });

  const apply = () => {
    const e = {};
    if (Number(cfg.cooldown) < 0) e.cooldown = 'Must be ≥ 0';
    if (Number(cfg.closeTimer) < 5) e.closeTimer = 'Must be ≥ 5';
    if (Number(cfg.partySize) < 1 || Number(cfg.partySize) > 16) e.partySize = '1–16';
    if (Number(cfg.inviteExpiry) < 10) e.inviteExpiry = 'Must be ≥ 10';
    setErrors(e);
    if (Object.keys(e).length === 0) onApply();
  };

  return (
    <div className="sm-noscroll" style={{ flex: 1, overflow: 'auto' }}>
      <SlateSection label="General Configuration" accent={accent} />
      <div style={{
        padding: '4px 18px 16px',
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: 14,
      }}>
        <SlateNumber
          label="Cooldown"
          hint="seconds after a run"
          value={cfg.cooldown}
          onChange={set('cooldown')}
          accent={accent}
          suffix="s"
          step={30}
          error={errors.cooldown}
        />
        <SlateNumber
          label="Close timer"
          hint="auto-close idle lobby"
          value={cfg.closeTimer}
          onChange={set('closeTimer')}
          accent={accent}
          suffix="s"
          step={30}
          error={errors.closeTimer}
        />
        <SlateNumber
          label="Max party size"
          hint="players per lobby"
          value={cfg.partySize}
          onChange={set('partySize')}
          accent={accent}
          suffix="P"
          step={1}
          error={errors.partySize}
        />
        <SlateNumber
          label="Invite expiry"
          hint="how long invites last"
          value={cfg.inviteExpiry}
          onChange={set('inviteExpiry')}
          accent={accent}
          suffix="s"
          step={30}
          error={errors.inviteExpiry}
        />
      </div>

      {Object.keys(errors).length > 0 && (
        <div style={{
          margin: '0 18px 16px',
          padding: '10px 12px',
          background: '#3a1814',
          borderLeft: `3px solid ${SLATE.danger}`,
          fontFamily: slateFonts.mono, fontSize: 11,
          color: '#ffb8a8', letterSpacing: 0.5,
          display: 'flex', flexDirection: 'column', gap: 4,
        }}>
          {Object.entries(errors).map(([k, v]) => (
            <div key={k}>⚠ <b>{k}</b> · {v}</div>
          ))}
        </div>
      )}

      <div style={{
        margin: '0 18px 16px',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        {applied ? (
          <span style={{
            fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.good,
            letterSpacing: 1, textTransform: 'uppercase',
          }}>✓ Saved</span>
        ) : <span />}
        <SlateBtn tone="primary" accent={accent} onClick={apply}>Apply Changes</SlateBtn>
      </div>
    </div>
  );
}

function TierTab({ accent, tier, tierCfg, setTierCfg, onApply, applied, leaderboard }) {
  const set = (k) => (v) => setTierCfg({ ...tierCfg, [k]: v });
  const c = TIER_C[tier];
  return (
    <div className="sm-noscroll" style={{ flex: 1, overflow: 'auto' }}>
      {/* Tier banner */}
      <div style={{
        margin: '14px 18px 6px',
        padding: '12px 14px',
        background: `${c.c}1c`,
        borderLeft: `3px solid ${c.c}`,
        display: 'flex', alignItems: 'center', gap: 12,
      }}>
        <SlateTag color={c.c}>{tier} Tier</SlateTag>
        <span style={{ fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.inkMid }}>
          Affects all <span style={{ color: c.c }}>{tier}</span> runs across all instances.
        </span>
      </div>

      <SlateSection label="Tier Parameters" accent={accent} />
      <div style={{
        padding: '4px 18px 14px',
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: 14,
      }}>
        <SlateNumber
          label="Health multiplier"
          hint="enemy HP × N"
          value={tierCfg.health}
          onChange={set('health')}
          accent={accent}
          suffix="×"
          step={1}
          min={1}
        />
        <SlateNumber
          label="Damage multiplier"
          hint="enemy DMG × N"
          value={tierCfg.damage}
          onChange={set('damage')}
          accent={accent}
          suffix="×"
          step={1}
          min={1}
        />
        <div>
          <div style={{
            fontFamily: slateFonts.mono, fontSize: 10, color: SLATE.inkMid,
            letterSpacing: 1.5, textTransform: 'uppercase', marginBottom: 6,
          }}>Loot table</div>
          <SlateInput
            value={tierCfg.loot}
            onChange={e => set('loot')(e.target.value)}
            accent={accent}
            mono
            prefix="id:"
          />
        </div>
        <SlateNumber
          label="Run time limit"
          hint="seconds"
          value={tierCfg.duration}
          onChange={set('duration')}
          accent={accent}
          suffix="s"
          step={30}
        />
      </div>

      {/* Hardcore default */}
      <div style={{ padding: '0 18px 16px' }}>
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '10px 14px',
          background: SLATE.panel2,
          border: `1px solid ${SLATE.hairlineHi}`,
          borderLeft: `3px solid ${tierCfg.hardcore ? SLATE.danger : SLATE.hairlineHi}`,
        }}>
          <div>
            <div style={{
              fontFamily: slateFonts.mono, fontSize: 9, color: SLATE.inkDim,
              letterSpacing: 1.5, textTransform: 'uppercase', marginBottom: 2,
            }}>Hardcore default</div>
            <div style={{ fontSize: 12, color: SLATE.inkMid }}>
              New {tier} lobbies start with Hardcore enabled.
            </div>
          </div>
          <button
            onClick={() => set('hardcore')(!tierCfg.hardcore)}
            style={{
              width: 52, height: 26,
              background: tierCfg.hardcore ? SLATE.danger : SLATE.hairline,
              border: 'none', cursor: 'pointer', padding: 2,
            }}
          >
            <div style={{
              width: 22, height: 22, background: '#fff',
              transform: `translateX(${tierCfg.hardcore ? 24 : 0}px)`,
              transition: 'transform 150ms ease-out',
            }} />
          </button>
        </div>
      </div>

      <div style={{
        margin: '0 18px 16px',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        {applied ? (
          <span style={{
            fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.good,
            letterSpacing: 1, textTransform: 'uppercase',
          }}>✓ {tier} saved</span>
        ) : <span />}
        <SlateBtn tone="primary" accent={accent} onClick={onApply}>Apply {tier} Tier</SlateBtn>
      </div>

      {/* Leaderboard */}
      <SlateSection label={`${tier} · Top Runs`} accent={accent} count={leaderboard.length} />
      <div style={{
        display: 'grid',
        gridTemplateColumns: '50px 1fr 100px',
        gap: 12,
        padding: '6px 18px',
        fontFamily: slateFonts.mono, fontSize: 9,
        color: SLATE.inkDim, letterSpacing: 1.5, textTransform: 'uppercase',
        background: SLATE.panel2,
        borderBottom: `1px solid ${SLATE.hairline}`,
      }}>
        <span>Rank</span>
        <span>Player</span>
        <span style={{ textAlign: 'right' }}>Time</span>
      </div>
      {leaderboard.map((row) => {
        const isTop = row.rank === 1;
        return (
          <div key={row.rank} style={{
            display: 'grid',
            gridTemplateColumns: '50px 1fr 100px',
            gap: 12,
            padding: '8px 18px',
            alignItems: 'center',
            background: SLATE.rowBg,
            borderBottom: `1px solid ${SLATE.panel2}`,
          }}>
            <div style={{
              fontFamily: slateFonts.mono, fontSize: 14, fontWeight: 700,
              color: isTop ? c.c : SLATE.inkMid,
            }}>
              {isTop ? '★' : '#'}{row.rank}
            </div>
            <div style={{ fontSize: 13, color: SLATE.ink, fontWeight: 500 }}>
              {row.name}
            </div>
            <div style={{
              textAlign: 'right',
              fontFamily: slateFonts.mono, fontSize: 14, fontWeight: 600,
              color: isTop ? c.c : SLATE.inkMid,
              fontVariantNumeric: 'tabular-nums',
            }}>{row.time}</div>
          </div>
        );
      })}
    </div>
  );
}

// =========================================================================
//                              MAIN APP
// =========================================================================

function DungeonControllerApp({ defaults }) {
  const [t, setTweak] = useTweaks(defaults);
  const accent = SLATE_ACCENTS[t.accent] || SLATE_ACCENTS.violet;

  // ─── PLAYER STATE ────────────────────────────────────────────────────
  const [lobbies, setLobbies] = useState(SAMPLE_LOBBIES);
  const [invites, setInvites] = useState(SAMPLE_INVITES);
  const [myLobbyId, setMyLobbyId] = useState(null);
  const [inviteValue, setInviteValue] = useState('');
  const [toast, setToast] = useState(null);
  const [error, setError] = useState(null);

  // My lobby derived
  const myLobby = useMemo(() => {
    return t.isOwner
      ? { id: 'my', owner: t.playerName, members: 3, max: 4, tier: 'Hard', vis: 'PUBLIC', hc: false }
      : (myLobbyId ? lobbies.find(l => l.id === myLobbyId) : null);
  }, [t.isOwner, t.playerName, myLobbyId, lobbies]);

  // Members for current lobby
  const members = useMemo(() => {
    if (!myLobby) return [];
    if (t.isOwner) {
      return [
        { name: t.playerName, isOwner: true,  isSelf: true,  online: true,  ready: t.isReady },
        { name: 'Alex',       isOwner: false, isSelf: false, online: true,  ready: t.allReady },
        { name: 'Maria',      isOwner: false, isSelf: false, online: t.allReady, ready: t.allReady },
      ];
    }
    return [
      { name: myLobby.owner, isOwner: true,  isSelf: false, online: true,  ready: t.allReady },
      { name: t.playerName,  isOwner: false, isSelf: true,  online: true,  ready: t.isReady },
      { name: 'Alex',        isOwner: false, isSelf: false, online: true,  ready: t.allReady },
    ];
  }, [myLobby, t.isOwner, t.isReady, t.allReady, t.playerName]);

  const allReadyComputed = members.length > 0 && members.every(m => m.ready && m.online);
  const canStart = t.isOwner && allReadyComputed && t.freeInstance && myLobby;
  const busy = myLobby && myLobby.status === 'IN_RUN';

  // Show invites only when tweak says so
  const effectiveInvites = t.hasInvites ? invites : [];

  // ─── ADMIN STATE ─────────────────────────────────────────────────────
  const [instances, setInstances] = useState(SAMPLE_INSTANCES);
  const [adminCfg, setAdminCfg] = useState({
    cooldown: '180',
    closeTimer: '120',
    partySize: '4',
    inviteExpiry: '180',
  });
  const [adminApplied, setAdminApplied] = useState(false);

  const [tierCfgs, setTierCfgs] = useState({
    normal: { health: '1', damage: '1',  loot: 'arenas:loot/normal', duration: '600',  hardcore: false },
    hard:   { health: '2', damage: '2',  loot: 'arenas:loot/hard',   duration: '900',  hardcore: false },
    hell:   { health: '5', damage: '4',  loot: 'arenas:loot/hell',   duration: '1200', hardcore: true },
  });
  const [tierApplied, setTierApplied] = useState({});

  // Cooldown ticker for instances (live update demonstration)
  useEffect(() => {
    const i = setInterval(() => {
      setInstances(prev => prev.map(inst => {
        if (inst.status === 'COOLDOWN' && inst.cd > 0) {
          const cd = inst.cd - 1;
          return cd <= 0 ? { ...inst, status: 'IDLE', cd: 0, tier: null } : { ...inst, cd };
        }
        return inst;
      }));
    }, 1000);
    return () => clearInterval(i);
  }, []);

  // ─── HANDLERS ────────────────────────────────────────────────────────
  const onJoin = (l) => {
    if (myLobby) { setError('You\'re already in a lobby. Leave it first.'); setTimeout(() => setError(null), 4000); return; }
    setMyLobbyId(l.id);
    setTweak('isOwner', false);
    setTweak('playerTab', 'mylobby');
    setToast({ kind: 'success', title: 'Joined lobby', body: `You joined ${l.owner}'s lobby.` });
  };
  const onRequest = (l) => {
    setToast({ kind: 'info', title: 'Request sent', body: `${l.owner} will see your join request.` });
  };
  const onCreate = () => {
    setTweak('isOwner', true);
    setTweak('playerTab', 'mylobby');
    setMyLobbyId('my');
    setToast({ kind: 'success', title: 'Lobby created', body: 'You\'re now hosting.' });
  };
  const onAccept = (inv) => {
    setInvites(invites.filter(i => i.id !== inv.id));
    onJoin({ id: 'inv-' + inv.id, owner: inv.owner });
  };
  const onDecline = (inv) => setInvites(invites.filter(i => i.id !== inv.id));
  const onLeave = () => {
    setMyLobbyId(null);
    setTweak('isOwner', false);
    setTweak('playerTab', 'lobbies');
  };
  const onStart = () => {
    if (!t.freeInstance) { setError('No free instance available. Wait for one to finish.'); setTimeout(() => setError(null), 4000); return; }
    if (!allReadyComputed) { setError('All members must be ready and online.'); setTimeout(() => setError(null), 4000); return; }
    setToast({ kind: 'success', title: 'Run started', body: 'Lobby has been dissolved.' });
    setMyLobbyId(null);
    setTweak('isOwner', false);
    setTweak('playerTab', 'lobbies');
  };

  const onInvitePlayer = () => {
    if (!inviteValue.trim()) return;
    setToast({ kind: 'info', title: 'Invite sent', body: `Invite sent to ${inviteValue}.` });
    setInviteValue('');
  };

  // Admin actions
  const onMoveUp = (inst) => {
    const i = instances.findIndex(x => x.id === inst.id);
    if (i <= 0) return;
    const a = [...instances]; [a[i-1], a[i]] = [a[i], a[i-1]]; setInstances(a);
  };
  const onMoveDown = (inst) => {
    const i = instances.findIndex(x => x.id === inst.id);
    if (i < 0 || i >= instances.length - 1) return;
    const a = [...instances]; [a[i], a[i+1]] = [a[i+1], a[i]]; setInstances(a);
  };
  const onRemoveInst = (inst) => {
    if (inst.status === 'RUNNING') return;
    if (inst.status === 'PENDING') {
      setInstances(instances.map(x => x.id === inst.id ? { ...x, status: 'IDLE' } : x));
    } else {
      setInstances(instances.map(x => x.id === inst.id ? { ...x, status: 'PENDING' } : x));
    }
  };

  // ─── TAB ROUTING ─────────────────────────────────────────────────────
  const isAdmin = t.ui === 'admin';

  const playerTabs = [
    ['lobbies', 'Lobbies', lobbies.length],
    ['mylobby', 'My Lobby', myLobby ? members.length : null],
    ...(effectiveInvites.length > 0 ? [['invites', 'Invites', effectiveInvites.length]] : []),
  ];

  const adminTabs = [
    ['instances', 'Instances', instances.length],
    ['general',   'General'],
    ['normal',    'Normal'],
    ['hard',      'Hard'],
    ['hell',      'Hell'],
  ];

  // Player status indicator
  const playerStatus =
    busy ? { c: SLATE.warn,   label: 'Busy' } :
    myLobby ? { c: SLATE.info, label: 'In Lobby' } :
    { c: SLATE.good, label: 'Available' };

  // ─── RENDER ──────────────────────────────────────────────────────────
  return (
    <div style={{
      width: '100vw', height: '100vh',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      background:
        `radial-gradient(1200px 600px at 30% 20%, ${accent.soft}, transparent 60%),` +
        `radial-gradient(900px 500px at 80% 90%, rgba(0,0,0,0.4), transparent 60%),` +
        SLATE.bg,
      padding: 40,
      fontFamily: slateFonts.sans,
      color: SLATE.ink,
    }}>
      <SlateShell accent={accent} style={{ width: 'min(1100px, 100%)', maxHeight: '92vh' }}>
        <ControllerHeader
          accent={accent}
          title={isAdmin ? 'Dungeon Controller · ADMIN' : 'Dungeon Controller'}
          isAdmin={isAdmin}
          pos={t.controllerPos}
          status={playerStatus}
          onClose={() => {}}
        />

        <SlateTabs
          tabs={isAdmin ? adminTabs : playerTabs}
          value={isAdmin ? t.adminTab : t.playerTab}
          onChange={(k) => isAdmin ? setTweak('adminTab', k) : setTweak('playerTab', k)}
          accent={accent}
        />

        {/* === PLAYER BODY === */}
        {!isAdmin && t.playerTab === 'lobbies' && (
          <LobbiesTab
            accent={accent}
            currentName={t.playerName}
            myLobbyId={myLobbyId}
            busy={busy}
            invites={effectiveInvites}
            lobbies={lobbies}
            onJoin={onJoin}
            onRequest={onRequest}
            onAccept={onAccept}
            onDecline={onDecline}
            onCreate={onCreate}
          />
        )}
        {!isAdmin && t.playerTab === 'mylobby' && (
          <MyLobbyTab
            accent={accent}
            currentName={t.playerName}
            lobby={myLobby}
            members={members}
            isOwner={t.isOwner}
            isReady={t.isReady}
            allReady={allReadyComputed}
            freeInstance={t.freeInstance}
            busy={busy}
            inviteValue={inviteValue}
            setInviteValue={setInviteValue}
            onInvite={onInvitePlayer}
            onKick={(m) => setToast({ kind: 'warn', title: 'Kicked', body: `${m.name} was kicked.` })}
            onToggleReady={() => setTweak('isReady', !t.isReady)}
            onLeave={onLeave}
            onStart={onStart}
            onTierChange={(tier) => {
              if (myLobby?.id === 'my') { /* owner: synthesize */ }
              else setLobbies(ls => ls.map(l => l.id === myLobbyId ? { ...l, tier } : l));
            }}
            onVisChange={(vis) => {
              if (myLobby?.id === 'my') { }
              else setLobbies(ls => ls.map(l => l.id === myLobbyId ? { ...l, vis } : l));
            }}
            onHardcoreToggle={() => {
              if (myLobby?.id === 'my') { }
              else setLobbies(ls => ls.map(l => l.id === myLobbyId ? { ...l, hc: !l.hc } : l));
            }}
          />
        )}
        {!isAdmin && t.playerTab === 'invites' && (
          <div className="sm-noscroll" style={{ flex: 1, overflow: 'auto' }}>
            <SlateSection label="My Invites" accent={accent} count={effectiveInvites.length} />
            {effectiveInvites.length === 0 ? (
              <SlateEmpty icon="✉" title="No invites" body="Invites from other players will appear here." accent={accent} />
            ) : (
              <>
                <InviteColumns />
                {effectiveInvites.map(inv => (
                  <InviteRow key={inv.id} inv={inv} accent={accent} onAccept={onAccept} onDecline={onDecline} />
                ))}
              </>
            )}
          </div>
        )}

        {/* === ADMIN BODY === */}
        {isAdmin && t.adminTab === 'instances' && (
          <InstancesTab
            accent={accent}
            instances={instances}
            onUp={onMoveUp}
            onDown={onMoveDown}
            onRemove={onRemoveInst}
          />
        )}
        {isAdmin && t.adminTab === 'general' && (
          <GeneralTab
            accent={accent}
            cfg={adminCfg}
            setCfg={setAdminCfg}
            applied={adminApplied}
            onApply={() => { setAdminApplied(true); setTimeout(() => setAdminApplied(false), 2200); }}
          />
        )}
        {isAdmin && (t.adminTab === 'normal' || t.adminTab === 'hard' || t.adminTab === 'hell') && (
          <TierTab
            accent={accent}
            tier={t.adminTab === 'normal' ? 'Normal' : t.adminTab === 'hard' ? 'Hard' : 'Hell'}
            tierCfg={tierCfgs[t.adminTab]}
            setTierCfg={(c) => setTierCfgs({ ...tierCfgs, [t.adminTab]: c })}
            applied={tierApplied[t.adminTab]}
            onApply={() => {
              setTierApplied({ ...tierApplied, [t.adminTab]: true });
              setTimeout(() => setTierApplied(prev => ({ ...prev, [t.adminTab]: false })), 2200);
            }}
            leaderboard={SAMPLE_LEADERBOARD[t.adminTab] || []}
          />
        )}

        {/* === FOOTER === */}
        <Footer
          accent={accent}
          error={error}
          left={
            !isAdmin && myLobby && t.playerTab === 'mylobby' ? (
              <div style={{
                fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.inkMid,
                letterSpacing: 0.5,
                display: 'flex', gap: 14, alignItems: 'center',
              }}>
                {!t.freeInstance && (
                  <span style={{ color: SLATE.warn, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontSize: 13 }}>⚠</span> No free instance
                  </span>
                )}
                {!allReadyComputed && t.isOwner && (
                  <span style={{ color: SLATE.inkDim }}>
                    Waiting for all members to ready up
                  </span>
                )}
              </div>
            ) : !isAdmin && !myLobby && t.playerTab === 'lobbies' ? (
              <div style={{
                fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.inkDim,
                letterSpacing: 0.5,
              }}>
                Right-click another controller to view it · Press E does <span style={{ color: SLATE.warn }}>NOT</span> close this screen
              </div>
            ) : isAdmin ? (
              <div style={{
                fontFamily: slateFonts.mono, fontSize: 11, color: SLATE.inkDim, letterSpacing: 0.5,
              }}>
                /arenasld dungeon admin · live updates active
              </div>
            ) : null
          }
          right={
            !isAdmin && myLobby && t.playerTab === 'mylobby' ? (
              <>
                <SlateBtn tone="danger" accent={accent} onClick={onLeave}>Leave Lobby</SlateBtn>
                <SlateBtn
                  tone={t.isReady ? 'warn' : 'good'}
                  accent={accent}
                  onClick={() => setTweak('isReady', !t.isReady)}
                >
                  {t.isReady ? 'Unready' : 'Ready'}
                </SlateBtn>
                {t.isOwner && (
                  <SlateBtn
                    tone="primary"
                    accent={accent}
                    disabled={!canStart}
                    onClick={onStart}
                  >▶ Start Run</SlateBtn>
                )}
              </>
            ) : null
          }
        />

        {toast && (
          <SlateToast
            kind={toast.kind}
            title={toast.title}
            body={toast.body}
            accent={accent}
            onDone={() => setToast(null)}
          />
        )}
      </SlateShell>

      {/* ───────── Tweaks ───────── */}
      <TweaksPanel title="Tweaks">
        <TweakSection label="Screen" />
        <TweakRadio
          label="GUI"
          value={t.ui}
          options={['player', 'admin']}
          onChange={v => setTweak('ui', v)}
        />
        {t.ui === 'player' && (
          <TweakSelect
            label="Player tab"
            value={t.playerTab}
            options={['lobbies', 'mylobby', 'invites']}
            onChange={v => setTweak('playerTab', v)}
          />
        )}
        {t.ui === 'admin' && (
          <TweakSelect
            label="Admin tab"
            value={t.adminTab}
            options={['instances', 'general', 'normal', 'hard', 'hell']}
            onChange={v => setTweak('adminTab', v)}
          />
        )}

        <TweakSection label="Theme" />
        <TweakColor
          label="Accent"
          value={accent.mid}
          options={Object.values(SLATE_ACCENTS).map(a => a.mid)}
          onChange={v => {
            const key = Object.keys(SLATE_ACCENTS).find(k => SLATE_ACCENTS[k].mid === v) || 'violet';
            setTweak('accent', key);
          }}
        />

        {t.ui === 'player' && (
          <>
            <TweakSection label="Player state" />
            <TweakToggle
              label="In a lobby"
              value={!!myLobby}
              onChange={v => {
                if (v) { setMyLobbyId('lob_steve'); }
                else   { setMyLobbyId(null); setTweak('isOwner', false); }
              }}
            />
            <TweakToggle
              label="I'm the owner"
              value={t.isOwner}
              onChange={v => { setTweak('isOwner', v); if (v) setMyLobbyId('my'); }}
            />
            <TweakToggle
              label="I'm ready"
              value={t.isReady}
              onChange={v => setTweak('isReady', v)}
            />
            <TweakToggle
              label="All members ready"
              value={t.allReady}
              onChange={v => setTweak('allReady', v)}
            />
            <TweakToggle
              label="Has invites"
              value={t.hasInvites}
              onChange={v => setTweak('hasInvites', v)}
            />
            <TweakToggle
              label="Free instance available"
              value={t.freeInstance}
              onChange={v => setTweak('freeInstance', v)}
            />
          </>
        )}

        <TweakSection label="Help" />
        <div style={{ fontSize: 10.5, color: 'rgba(41,38,27,.6)', lineHeight: 1.55 }}>
          Toggle <b>I'm the owner</b> to see owner-only controls on the My Lobby tab.
          {' '}Switch <b>GUI</b> between player and admin.
        </div>
      </TweaksPanel>
    </div>
  );
}

window.DungeonControllerApp = DungeonControllerApp;
