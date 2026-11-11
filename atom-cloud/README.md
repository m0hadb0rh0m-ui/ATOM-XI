---
title: ATOM AI X Cloud
emoji: ⚛️
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
---

# ATOM AI X Cloud

A **deployable template**, not a live service. Runs the Apache-2.0-licensed Qwen2.5-1.5B-Instruct model using FastAPI.

## Deploy to Hugging Face Spaces
1. Create a new Docker Space at https://huggingface.co/new-space .
2. Copy the contents of this `atom-cloud/` directory to the root of that Space.
3. Wait for the Space to build. Free CPU instances may be slow or lack sufficient memory for this model; availability is not guaranteed.
4. Open `https://YOUR-SPACE.hf.space/health` and verify it reports status ok.
5. Set the Android app's endpoint to `https://YOUR-SPACE.hf.space` after deployment.

## API
POST `/chat` with JSON:
`{"messages":[{"role":"user","content":"مرحبا"}]}`
Response:
`{"reply":"...","model":"Qwen/Qwen2.5-1.5B-Instruct"}`

**Security:** This demo endpoint has no authentication or rate limiting. Do not publish it for unrestricted public use or transmit private data before adding access controls. A private Space also requires an appropriate authentication design for the Android app.

No cloud account or paid infrastructure has been created, and the model has not been fine-tuned. The service uses a customized system instruction.
