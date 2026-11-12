# ATOM AI X V2 — isolated development
This is an **incomplete** implementation on the feature/atom-ai-x-v2 branch. The original main branch is unchanged.

## Backend
```bash
cd v2/backend
python -m pip install -r requirements.txt
pytest -q
GEMINI_API_KEY=... uvicorn main:app --host 127.0.0.1 --port 8080
```
Never commit provider keys. Store GEMINI_API_KEY in Cloud Run Secret Manager. Public unauthenticated endpoints must **not** be deployed as-is: add identity, quotas and rate limiting before deployment.

## Status
- FastAPI /health and Gemini SSE integration: implemented in source, **not live-tested**.
- Android V2: pending integration and build.
- Google Cloud Run: **not deployed**.
- APK: **not built**.
- Full v2 specification: **not complete**.
