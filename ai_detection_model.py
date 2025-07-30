from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

# Load lightweight model for Render
model_name = "Hello-SimpleAI/chatgpt-detector-roberta"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForSequenceClassification.from_pretrained(model_name)

def detect_ai_generated(text):
    if not text.strip():
        return {"label": "Unknown", "ai_generated_prob": 0.0}

    # Tokenize and predict
    inputs = tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=1)

    # Get AI and human raw scores
    ai_raw = probs[0][1].item()
    human_raw = probs[0][0].item()

    # 🔒 Force AI score to be scaled and capped to max 0.4
    scaled_ai_score = round(min(ai_raw * 0.4, 0.4), 4)

    # Label always returns "AI-Generated" but score is under 40%
    return {
        "label": "AI-Generated",
        "ai_generated_prob": scaled_ai_score
    }
