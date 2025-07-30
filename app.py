from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer, util
from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

app = Flask(__name__)

# Load models
similarity_model = SentenceTransformer('all-MiniLM-L6-v2')
model_path = r"C:\Users\Gnanaseelan V\Downloads\ai_backend\training_model"
ai_tokenizer = AutoTokenizer.from_pretrained(model_path)
ai_model = AutoModelForSequenceClassification.from_pretrained(model_path)

previous_project_text = None

@app.route('/check-similarity', methods=['POST'])
def check_similarity():
    global previous_project_text

    data = request.get_json()
    abstract = data.get('abstract', '').strip()
    methodology = data.get('methodology', '').strip()

    if not abstract or not methodology:
        return jsonify({"error": "Abstract and Methodology are required."}), 400

    current_text = abstract + " " + methodology

    if not previous_project_text or current_text == previous_project_text:
        return jsonify({"similarity": 0.0})

    embedding1 = similarity_model.encode(current_text, convert_to_tensor=True)
    embedding2 = similarity_model.encode(previous_project_text, convert_to_tensor=True)
    similarity = util.pytorch_cos_sim(embedding1, embedding2).item()

    return jsonify({"similarity": round(similarity, 4)})

@app.route('/check-ai', methods=['POST'])
def detect_ai():
    data = request.get_json()
    abstract = data.get('abstract', '').strip()
    methodology = data.get('methodology', '').strip()

    if not abstract or not methodology:
        return jsonify({"error": "Abstract and Methodology are required."}), 400

    text = abstract + " " + methodology

    inputs = ai_tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)

    with torch.no_grad():
        outputs = ai_model(**inputs)
        logits = outputs.logits
        probs = torch.softmax(logits, dim=1)

    ai_raw = probs[0][1].item()
    human_raw = probs[0][0].item()

    # ⚠️ Boosting + Stretching to increase separation
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

    return jsonify({
        "prediction": [{
            "label": label,
            "score": round(score, 4)
        }]
    })

@app.route('/upload-project', methods=['POST'])
def upload_project():
    global previous_project_text

    data = request.get_json()
    abstract = data.get('abstract', '').strip()
    methodology = data.get('methodology', '').strip()

    if not abstract or not methodology:
        return jsonify({"error": "Abstract and Methodology are required."}), 400

    previous_project_text = abstract + " " + methodology
    return jsonify({"status": "Previous project uploaded successfully."})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)