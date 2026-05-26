// slate-ui.jsx — Shared design tokens + primitives for the Slate language.
// Used by Slate Market and Dungeon Controller so they read as the same UI kit.

const SLATE = {
  bg:         '#04070a',
  panel:      '#121922',
  panel2:     '#0c1218',
  hairline:   '#283442',
  hairlineHi: '#3a4a5c',
  rowBg:      '#19222d',
  rowBgAlt:   '#16202a',
  rowHover:   '#22303d',
  ink:        '#e8eef5',
  inkMid:     '#9aa8b8',
  inkDim:     '#5f6e80',
  good:       '#86d36c',
  warn:       '#f5b042',
  danger:     '#e8624a',
  info:       '#6da3e8',
};

const SLATE_ACCENTS = {
  amber:   { mid: '#f5b042', dark: '#a87520', soft: '#f5b04222', ink: '#1a1208' },
  emerald: { mid: '#5fc77d', dark: '#2d8a4a', soft: '#5fc77d22', ink: '#0a1a10' },
  cyan:    { mid: '#5fc7d8', dark: '#2d8a9c', soft: '#5fc7d822', ink: '#0a1a1d' },
  violet:  { mid: '#a98be8', dark: '#6c4fb5', soft: '#a98be822', ink: '#160a2a' },
};

const slateFonts = {
  mono:  '"JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace',
  sans:  '"Geist", -apple-system, BlinkMacSystemFont, system-ui, sans-serif',
  pixel: '"Press Start 2P", monospace',
};

// ---------- Buttons ----------

function slateBtn({ tone = 'ghost', size = 'md', icon = false, accent }) {
  const a = accent || SLATE_ACCENTS.violet;
  const tones = {
    ghost:   { bg: 'transparent', border: SLATE.hairline,   ink: SLATE.inkMid },
    solid:   { bg: SLATE.rowBg,    border: SLATE.hairlineHi, ink: SLATE.ink },
    primary: { bg: a.mid,          border: a.dark,           ink: a.ink },
    danger:  { bg: 'transparent',  border: '#5a2a24',        ink: SLATE.danger },
    edit:    { bg: 'transparent',  border: SLATE.hairlineHi, ink: SLATE.info },
    accent:  { bg: 'transparent',  border: a.mid,            ink: a.mid },
    good:    { bg: 'transparent',  border: '#2a5a30',        ink: SLATE.good },
    warn:    { bg: 'transparent',  border: '#5a4520',        ink: SLATE.warn },
  };
  const t = tones[tone];
  const pad = icon
    ? (size === 'sm' ? '6px' : '8px')
    : (size === 'sm' ? '5px 10px' : size === 'lg' ? '12px 24px' : '7px 14px');
  return {
    background: t.bg,
    color: t.ink,
    padding: pad,
    border: `1px solid ${t.border}`,
    fontFamily: slateFonts.sans,
    fontWeight: 600,
    fontSize: size === 'sm' ? 11 : size === 'lg' ? 13 : 12,
    textTransform: 'uppercase',
    letterSpacing: 1.2,
    cursor: 'pointer',
    borderRadius: 0,
    minHeight: icon ? (size === 'sm' ? 26 : 30) : 'auto',
    minWidth:  icon ? (size === 'sm' ? 26 : 30) : 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    whiteSpace: 'nowrap',
  };
}

function SlateBtn({ tone, size, icon, accent, children, disabled, style, ...rest }) {
  return (
    <button
      disabled={disabled}
      style={{
        ...slateBtn({ tone, size, icon, accent }),
        opacity: disabled ? 0.4 : 1,
        cursor: disabled ? 'not-allowed' : 'pointer',
        ...style,
      }}
      {...rest}
    >
      {children}
    </button>
  );
}

// ---------- Tag ----------

function SlateTag({ children, color, soft = true, size = 'md' }) {
  const pad = size === 'sm' ? '2px 5px' : '3px 6px';
  return (
    <span style={{
      fontFamily: slateFonts.mono, fontSize: size === 'sm' ? 8 : 9,
      background: soft ? `${color}22` : color,
      color: soft ? color : '#0a0e14',
      padding: pad,
      border: `1px solid ${color}55`,
      letterSpacing: 1.5,
      textTransform: 'uppercase',
      lineHeight: 1,
      whiteSpace: 'nowrap',
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4,
    }}>
      {children}
    </span>
  );
}

// ---------- Panel chrome ----------

function SlateShell({ accent, children, style }) {
  return (
    <div style={{
      background: SLATE.panel,
      boxShadow: [
        `0 0 0 2px ${SLATE.hairlineHi}`,
        `0 0 0 4px #000`,
        `0 24px 60px rgba(0,0,0,0.6)`,
      ].join(','),
      display: 'flex',
      flexDirection: 'column',
      position: 'relative',
      overflow: 'hidden',
      color: SLATE.ink,
      fontFamily: slateFonts.sans,
      ...style,
    }}>
      {children}
    </div>
  );
}

// ---------- Tab bar ----------

function SlateTabs({ tabs, value, onChange, accent }) {
  return (
    <div style={{
      display: 'flex',
      borderBottom: `1px solid ${SLATE.hairline}`,
      background: SLATE.panel2,
    }}>
      {tabs.map(([key, label, count]) => {
        const active = key === value;
        return (
          <button
            key={key}
            onClick={() => onChange(key)}
            style={{
              padding: '10px 18px',
              background: active ? SLATE.panel : 'transparent',
              border: 'none',
              borderBottom: active ? `2px solid ${accent.mid}` : '2px solid transparent',
              borderTop: '2px solid transparent',
              color: active ? SLATE.ink : SLATE.inkMid,
              fontFamily: slateFonts.sans,
              fontSize: 12,
              fontWeight: 600,
              textTransform: 'uppercase',
              letterSpacing: 1.5,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              outline: 'none',
            }}
          >
            <span>{label}</span>
            {count != null && (
              <span style={{
                fontFamily: slateFonts.mono,
                fontSize: 10,
                color: active ? accent.mid : SLATE.inkDim,
                background: active ? accent.soft : SLATE.hairline,
                padding: '2px 6px',
                letterSpacing: 0.5,
              }}>{count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}

// ---------- Section heading inside a panel ----------

function SlateSection({ label, accent, action, count }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'baseline', justifyContent: 'space-between',
      padding: '14px 20px 8px',
      gap: 12,
    }}>
      <div style={{
        fontFamily: slateFonts.mono,
        fontSize: 10,
        color: SLATE.inkDim,
        letterSpacing: 2,
        textTransform: 'uppercase',
        display: 'flex',
        alignItems: 'baseline',
        gap: 8,
      }}>
        <span>{label}</span>
        {count != null && (
          <span style={{ color: accent ? accent.mid : SLATE.inkMid }}>· {count}</span>
        )}
      </div>
      {action}
    </div>
  );
}

// ---------- Input ----------

function SlateInput({ value, onChange, placeholder, accent, suffix, prefix, error, mono = false, type = 'text', dim = false, style }) {
  return (
    <div style={{
      background: SLATE.panel2,
      border: `1px solid ${error ? SLATE.danger : SLATE.hairlineHi}`,
      borderLeft: `3px solid ${error ? SLATE.danger : accent.mid}`,
      padding: '8px 12px',
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      fontFamily: mono ? slateFonts.mono : slateFonts.sans,
      color: SLATE.ink,
      opacity: dim ? 0.45 : 1,
      ...style,
    }}>
      {prefix && <span style={{ color: SLATE.inkDim, fontFamily: slateFonts.mono, fontSize: 11 }}>{prefix}</span>}
      <input
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        type={type}
        style={{
          flex: 1,
          minWidth: 0,
          background: 'transparent',
          border: 'none',
          outline: 'none',
          color: 'inherit',
          font: 'inherit',
          fontSize: 14,
          fontWeight: mono ? 600 : 500,
        }}
      />
      {suffix && (
        <span style={{
          color: SLATE.inkDim, fontFamily: slateFonts.mono, fontSize: 10,
          letterSpacing: 1.5, textTransform: 'uppercase',
        }}>{suffix}</span>
      )}
    </div>
  );
}

// ---------- Numeric field with +/- buttons ----------

function SlateNumber({ label, hint, value, onChange, accent, suffix, min = 0, max = 999999, step = 1, error }) {
  const num = Number(value) || 0;
  const dec = () => onChange(String(Math.max(min, num - step)));
  const inc = () => onChange(String(Math.min(max, num + step)));
  return (
    <div>
      <div style={{
        display: 'flex', alignItems: 'baseline', justifyContent: 'space-between',
        marginBottom: 6,
      }}>
        <label style={{
          fontSize: 10, fontFamily: slateFonts.mono, color: SLATE.inkMid,
          letterSpacing: 1.5, textTransform: 'uppercase',
        }}>{label}</label>
        {hint && <span style={{ fontSize: 10, color: SLATE.inkDim, fontStyle: 'italic' }}>{hint}</span>}
      </div>
      <div style={{ display: 'flex', gap: 4 }}>
        <button onClick={dec} style={{
          ...slateBtn({ tone: 'solid', size: 'md', accent }),
          minWidth: 32, padding: '8px 10px',
        }}>−</button>
        <SlateInput
          value={value}
          onChange={e => onChange(e.target.value.replace(/[^0-9-]/g, ''))}
          accent={accent}
          mono
          suffix={suffix}
          error={error}
          style={{ flex: 1, textAlign: 'right' }}
        />
        <button onClick={inc} style={{
          ...slateBtn({ tone: 'solid', size: 'md', accent }),
          minWidth: 32, padding: '8px 10px',
        }}>+</button>
      </div>
    </div>
  );
}

// ---------- Toast ----------

function SlateToast({ kind, title, body, accent, onDone }) {
  React.useEffect(() => {
    const t = setTimeout(onDone, 4000);
    return () => clearTimeout(t);
  }, [onDone]);
  const sides = {
    success: SLATE.good,
    info:    accent.mid,
    warn:    SLATE.warn,
    danger:  SLATE.danger,
  };
  const side = sides[kind] || accent.mid;
  return (
    <div style={{
      position: 'absolute',
      right: 20, bottom: 76,
      background: SLATE.panel,
      border: `1px solid ${SLATE.hairlineHi}`,
      borderLeft: `3px solid ${side}`,
      padding: '12px 16px',
      minWidth: 280,
      maxWidth: 360,
      boxShadow: `0 8px 28px rgba(0,0,0,0.5), 0 0 0 1px #000`,
      zIndex: 30,
      animation: 'sl-toast-in 200ms ease-out',
    }}>
      <div style={{
        fontSize: 10, fontFamily: slateFonts.mono, color: side,
        letterSpacing: 1.5, textTransform: 'uppercase', marginBottom: 4,
      }}>{title}</div>
      <div style={{ fontSize: 13, color: SLATE.ink, fontFamily: slateFonts.mono }}>{body}</div>
    </div>
  );
}

// ---------- Empty state ----------

function SlateEmpty({ icon = '∅', title, body, accent, action }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      gap: 12, textAlign: 'center', padding: '40px 24px',
      background:
        `repeating-linear-gradient(45deg, ${SLATE.panel} 0 12px, ${SLATE.panel2} 12px 24px)`,
    }}>
      <div style={{
        width: 60, height: 60,
        border: `2px dashed ${SLATE.hairlineHi}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: SLATE.inkDim, fontSize: 26, fontFamily: slateFonts.mono,
      }}>{icon}</div>
      <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: 0.5 }}>{title}</div>
      {body && (
        <div style={{
          fontSize: 11, color: SLATE.inkMid, fontFamily: slateFonts.mono,
          maxWidth: 360, lineHeight: 1.6, letterSpacing: 0.3,
        }}>{body}</div>
      )}
      {action}
    </div>
  );
}

// ---------- Global animation keyframes ----------

if (typeof document !== 'undefined' && !document.getElementById('sl-anims')) {
  const s = document.createElement('style');
  s.id = 'sl-anims';
  s.textContent = `
    @keyframes sl-blink { 0%,100% { opacity: 1 } 50% { opacity: 0 } }
    @keyframes sl-toast-in {
      from { transform: translateY(8px); opacity: 0; }
      to   { transform: none; opacity: 1; }
    }
    @keyframes sl-fade-in {
      from { opacity: 0; }
      to   { opacity: 1; }
    }
    @keyframes sl-pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.45; }
    }
  `;
  document.head.appendChild(s);
}

Object.assign(window, {
  SLATE, SLATE_ACCENTS, slateFonts, slateBtn,
  SlateBtn, SlateTag, SlateShell, SlateTabs, SlateSection,
  SlateInput, SlateNumber, SlateToast, SlateEmpty,
});
