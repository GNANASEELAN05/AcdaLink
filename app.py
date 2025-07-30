from flask import Flask, request, jsonify
from similarity_check import get_similarity_score
from ai_generated_check import detect_ai_generated
import os

app = Flask(__name__)

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

    similarity = get_similarity_score(current_text, previous_project_text)
    return jsonify({"similarity": similarity})


@app.route('/check-ai', methods=['POST'])
def detect_ai():
    data = request.get_json()
    abstract = data.get('abstract', '').strip()
    methodology = data.get('methodology', '').strip()

    if not abstract or not methodology:
        return jsonify({"error": "Abstract and Methodology are required."}), 400

    text = abstract + " " + methodology
    result = detect_ai_generated(text)

    return jsonify({
        "prediction": [{
            "label": result["label"],
            "score": result["ai_generated_prob"]
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
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port)
