Dungeon Controller v2 — Export Bundle
======================================

Files:
  Dungeon Controller v2.html   ← entry point, open this
  dungeon-controller.jsx       ← main app component
  slate-ui.jsx                 ← shared UI primitives
  tweaks-panel.jsx             ← in-page Tweaks panel

External dependencies (loaded via CDN, requires internet):
  - React 18.3.1, ReactDOM, Babel Standalone (unpkg)
  - Google Fonts: Geist, JetBrains Mono, Press Start 2P

How to run locally:
  Because the page loads .jsx files via <script src>, you need a local
  web server (browsers block file:// fetches). Easiest options:

    # Python 3
    cd export-dungeon-v2
    python3 -m http.server 8000
    # then open http://localhost:8000/Dungeon%20Controller%20v2.html

    # Node
    npx serve .

Bringing into Figma:
  Use the html.to.design plugin and point it at the local server URL,
  or upload the bundle somewhere public and use that URL.
