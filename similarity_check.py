from sentence_transformers import SentenceTransformer, util

# Load the model once globally
model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')

def get_similarity_score(text1, text2):
    # If either text is empty, treat it as no previous project – return 0.0 similarity
    if not text1.strip() or not text2.strip():
        return 0.0

    # Avoid comparing exact same text (e.g., new project identical to previous)
    if text1.strip() == text2.strip():
        return 0.0

    embedding1 = model.encode(text1, convert_to_tensor=True)
    embedding2 = model.encode(text2, convert_to_tensor=True)

    score = util.pytorch_cos_sim(embedding1, embedding2).item()
    return round(score, 4)
