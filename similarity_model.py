from sentence_transformers import SentenceTransformer, util

# Load the model once globally for efficiency
model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')

def get_similarity_score(text1, text2):
    # Return 0.0 if either input is missing (i.e., no previous project available)
    if not text1 or not text2:
        return 0.0

    # Encode both inputs
    embedding1 = model.encode(text1, convert_to_tensor=True)
    embedding2 = model.encode(text2, convert_to_tensor=True)

    # Compute cosine similarity
    score = util.pytorch_cos_sim(embedding1, embedding2).item()
    return round(score, 4)
