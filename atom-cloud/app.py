"""ATOM AI X cloud inference service for Hugging Face Spaces (CPU).
This is a deployment template, not a currently running endpoint.
"""
import os
import threading
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoTokenizer, AutoModelForCausalLM
import torch

MODEL_ID = os.getenv("ATOM_MODEL_ID", "Qwen/Qwen2.5-1.5B-Instruct")
SYSTEM = ("أنت ATOM AI X، مساعد ذكاء اصطناعي عربي مخصص. "
          "أجب بدقة ووضوح، وصرّح عندما لا تعرف الإجابة. "
          "ساعد في التعليم والبرمجة وحل المشكلات دون اختلاق مصادر.")
app = FastAPI(title="ATOM AI X")
_lock = threading.Lock()
_tokenizer = None
_model = None

class Message(BaseModel):
    role: str
    content: str

class ChatRequest(BaseModel):
    messages: list[Message] = Field(min_length=1, max_length=30)
    max_new_tokens: int = Field(default=256, ge=32, le=512)

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_ID, "loaded": _model is not None}

@app.post("/chat")
def chat(request: ChatRequest):
    global _tokenizer, _model
    with _lock:
        if _model is None:
            try:
                _tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
                _model = AutoModelForCausalLM.from_pretrained(
                    MODEL_ID, torch_dtype=torch.float32, low_cpu_mem_usage=True
                ).eval()
            except Exception as exc:
                raise HTTPException(status_code=503, detail="Model unavailable: " + str(exc))
        history = [{"role": "system", "content": SYSTEM}]
        for msg in request.messages[-20:]:
            if msg.role in ("user", "assistant") and msg.content.strip():
                history.append({"role": msg.role, "content": msg.content[:8000]})
        if not any(m["role"] == "user" for m in history):
            raise HTTPException(status_code=422, detail="User message required")
        encoded = _tokenizer.apply_chat_template(
            history, tokenize=True, add_generation_prompt=True, return_tensors="pt"
        )
        with torch.inference_mode():
            result = _model.generate(
                encoded, max_new_tokens=request.max_new_tokens,
                do_sample=True, temperature=0.6, top_p=0.9,
                pad_token_id=_tokenizer.eos_token_id,
            )
        answer = _tokenizer.decode(result[0][encoded.shape[-1]:], skip_special_tokens=True)
        return {"reply": answer.strip(), "model": MODEL_ID}
