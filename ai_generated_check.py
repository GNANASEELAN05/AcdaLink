from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch
import random

# Load model from Hugging Face
model_name = "Hello-SimpleAI/chatgpt-detector-roberta"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForSequenceClassification.from_pretrained(model_name)

def detect_ai_generated(text):
    if not text.strip():
        return {
            "ai_generated_prob": round(random.uniform(0.05, 0.35), 4),  # random under 40%
            "human_written_prob": 1.0,
            "label": "Unknown"
        }

    inputs = tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=1)

    ai_raw = probs[0][1].item()
    human_raw = probs[0][0].item()

    # Apply capped, randomized transformation to produce varied outputs under 0.4
    base_ai_score = ai_raw * 0.4  # scale to max 0.4
    noise = random.uniform(-0.05, 0.05)  # add realistic noise
    ai_score = min(max(base_ai_score + noise, 0.05), 0.39)  # keep it between 0.05–0.39

    label = "AI-Generated" if ai_score > 0.3 else "Human-Written"

    return {
        "ai_generated_prob": round(ai_score, 4),
        "human_written_prob": round(1 - ai_score, 4),
        "label": label
    }
