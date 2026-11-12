# ATOM AI X V2 web foundation
Static web app, no build step: `python -m http.server 8081 -d v2/web` from repository root, then open http://localhost:8081.

Implemented: responsive dark/light design, 11 navigable screens (many intentionally marked unavailable), Arabic-first layout, local drafts/history, streaming chat frontend, stop generation, copy, local Markdown Canvas editor/export, configurable backend URL. **Not production-ready.**

Configure backend URL in Settings. The backend needs secure authentication, CORS restrictions, quotas and Cloud Run deployment before it should be exposed to users. Never paste the Gemini key into the browser.

The web UI is a standalone prototype, **not an Android APK**. The English toggle changes navigation labels and direction but not all copy; full bilingual coverage is pending.
