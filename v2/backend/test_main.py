from fastapi.testclient import TestClient
from main import app
client = TestClient(app)
def test_health():
    assert client.get("/health").status_code == 200
def test_empty_messages_rejected():
    assert client.post("/v1/chat/stream", json={"messages":[]}).status_code == 422
def test_missing_key(monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    r = client.post("/v1/chat/stream", json={"messages":[{"role":"user","content":"hi"}]})
    assert r.status_code == 503
