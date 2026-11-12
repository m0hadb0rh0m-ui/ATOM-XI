import os
import json
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

app = FastAPI(title="ATOM AI X v2 API", version="2.0.0")
MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
API_URL = "https://generativelanguage.googleapis.com/v1beta/models"

class Message(BaseModel):
    role: str
    content: str = Field(min_length=1, max_length=32000)

class ChatRequest(BaseModel):
    messages: list[Message] = Field(min_length=1, max_length=40)

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL, "configured": bool(os.getenv("GEMINI_API_KEY"))}

@app.post("/v1/chat/stream")
async def chat(req: ChatRequest):
    key = os.getenv("GEMINI_API_KEY")
    if not key:
        raise HTTPException(503, "Model provider not configured")
    contents = []
    for msg in req.messages:
        if msg.role not in ("user", "assistant"):
            raise HTTPException(422, "Invalid message role")
        contents.append({"role": "model" if msg.role == "assistant" else "user", "parts": [{"text": msg.content}]})
    async def events():
        url = f"{API_URL}/{MODEL}:streamGenerateContent"
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(90.0, connect=10.0)) as client:
                async with client.stream("POST", url, params={"alt": "sse", "key": key}, json={"contents": contents}) as response:
                    if response.status_code != 200:
                        yield "data: " + json.dumps({"error": f"Provider HTTP {response.status_code}"}) + "\n\n"
                        return
                    async for line in response.aiter_lines():
                        if not line.startswith("data: "):
                            continue
                        try:
                            item = json.loads(line[6:])
                            text = "".join(p.get("text", "") for c in item.get("candidates", []) for p in c.get("content", {}).get("parts", []))
                            if text:
                                yield "data: " + json.dumps({"text": text}, ensure_ascii=False) + "\n\n"
                        except (ValueError, TypeError):
                            continue
        except httpx.HTTPError:
            yield 'data: {"error":"Model connection failed"}\n\n'
        yield 'data: {"done":true}\n\n'
    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control":"no-cache","X-Accel-Buffering":"no"})
