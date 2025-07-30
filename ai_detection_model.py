from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

# Load from Hugging Face — deployable in Render
model_name = "Hello-SimpleAI/chatgpt-detector-roberta"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForSequenceClassification.from_pretrained(model_name)

def ai_detection_model(text):
    if not text.strip():
        return [{"label": "Unknown", "score": 0.0}]

    # Tokenize input
    inputs = tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=1)

    ai_raw = probs[0][1].item()
    human_raw = probs[0][0].item()

    # 🔧 Force AI-generated score to be capped at 0.4
    # Scale linearly and cap
    ai_score = round(min(ai_raw * 0.4, 0.4), 4)
    human_score = round(min(human_raw * 0.95, 0.95), 4)

    # Label logic (based on capped score)
    if ai_score > 0.35 and human_score < 0.5:
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
