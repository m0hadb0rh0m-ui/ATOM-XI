# ATOM AI X — Custom Open-Weight Model

ATOM AI X is a customized **configuration** of the Apache-2.0-licensed Qwen2.5-1.5B-Instruct model. This repository does **not** contain trained weights or a newly fine-tuned checkpoint.

## Run with Ollama (PC or Linux server)
1. Install Ollama: https://ollama.com/
2. Download the base model: `ollama pull qwen2.5:1.5b-instruct`
3. From this directory run: `ollama create atom-ai-x -f Modelfile`
4. Test: `ollama run atom-ai-x`

The model runs wherever Ollama is installed. GitHub Actions does not host a persistent inference service. Android cannot directly execute this Modelfile without a compatible local runtime.

## Fine-tuning
The optional LoRA script in `train_lora.py` trains adapters from `training_examples.jsonl` on a machine with Python, PyTorch, Transformers, PEFT and suitable compute. Training has **not** been run; no trained adapter is included.

## License
The underlying Qwen2.5-1.5B-Instruct model is licensed Apache-2.0. Respect its license and review the model card before redistribution.
