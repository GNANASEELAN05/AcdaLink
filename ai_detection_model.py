from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

model_path = r"C:\Users\Gnanaseelan V\Downloads\ai_backend\training_model"
tokenizer = AutoTokenizer.from_pretrained(model_path)
model = AutoModelForSequenceClassification.from_pretrained(model_path)

def ai_detection_model(text):
    if not text.strip():
        return [{"label": "Unknown", "score": 0.0}]

    inputs = tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=1)

    ai_raw = probs[0][1].item()
    human_raw = probs[0][0].item()

    ai_score = min(max(round(ai_raw ** 1.8, 4), 0.0), 0.95)
    human_score = min(max(round(human_raw ** 1.5, 4), 0.0), 0.95)

    if ai_score > 0.85 and human_score < 0.5:
        label = "AI-Generated"
        score = ai_score
    elif human_score > 0.65:
        label = "Human-Written"
        score = human_score
    else:
        label = "Uncertain"
        score = max(ai_score, human_score)

    return [{
        "label": label,
        "score": round(score, 4)
    }]
