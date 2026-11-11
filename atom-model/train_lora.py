"""Optional LoRA fine-tuning; requires GPU and dependencies. Not executed by this repo."""
import json
from pathlib import Path
import torch
from datasets import Dataset
from transformers import AutoModelForCausalLM, AutoTokenizer, TrainingArguments, Trainer, DataCollatorForLanguageModeling
from peft import LoraConfig, get_peft_model

MODEL = "Qwen/Qwen2.5-1.5B-Instruct"
rows = [json.loads(line) for line in Path(__file__).with_name("training_examples.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
tokenizer = AutoTokenizer.from_pretrained(MODEL)
texts = [tokenizer.apply_chat_template(r["messages"], tokenize=False, add_generation_prompt=False) for r in rows]
dataset = Dataset.from_dict({"text": texts})
def encode(batch):
    out = tokenizer(batch["text"], truncation=True, max_length=1024)
    out["labels"] = [ids.copy() for ids in out["input_ids"]]
    return out
dataset = dataset.map(encode, batched=True, remove_columns=["text"])
model = AutoModelForCausalLM.from_pretrained(MODEL, torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32)
model = get_peft_model(model, LoraConfig(r=8, lora_alpha=16, lora_dropout=0.05, target_modules=["q_proj","v_proj"], task_type="CAUSAL_LM"))
args = TrainingArguments(output_dir="atom-lora-output", num_train_epochs=3, per_device_train_batch_size=1, gradient_accumulation_steps=4, learning_rate=2e-4, logging_steps=1, save_strategy="epoch", report_to="none")
trainer = Trainer(model=model, args=args, train_dataset=dataset, data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False))
trainer.train()
model.save_pretrained("atom-lora-adapter")
tokenizer.save_pretrained("atom-lora-adapter")
