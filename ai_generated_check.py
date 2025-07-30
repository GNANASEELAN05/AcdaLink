from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

model_path = r"C:\Users\Gnanaseelan V\Downloads\ai_backend\training_model"
tokenizer = AutoTokenizer.from_pretrained(model_path)
model = AutoModelForSequenceClassification.from_pretrained(model_path)

def detect_ai_generated(text):
    if not text.strip():
        return {
            "ai_generated_prob": 0.0,
            "human_written_prob": 0.0,
            "label": "Unknown"
        }

    inputs = tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)

    with torch.no_grad():
        logits = model(**inputs).logits
        probs = torch.softmax(logits, dim=1).squeeze()

    ai_raw = probs[1].item()
    human_raw = probs[0].item()

    ai_prob = min(max(round(ai_raw ** 1.8, 4), 0.0), 0.95)
    human_prob = min(max(round(human_raw ** 1.5, 4), 0.0), 0.95)

    if ai_prob > 0.85 and human_prob < 0.5:
        label = "Likely AI-Generated Content"
    elif human_prob > 0.65:
        label = "Likely Human-Written Content"
    else:
        label = "Uncertain"

    return {
        "ai_generated_prob": ai_prob,
        "human_written_prob": human_prob,
        "label": label
    }
