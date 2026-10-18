"""Prints how similar the real embedding model finds a few text pairs, to help pick SIM_LOW and SIM_HIGH.

Run from services/ai-service:   .venv/bin/python -m app.calibrate
SIM_LOW should be near the "unrelated" numbers and SIM_HIGH near the "closely related" ones.
"""
from .config import get_settings
from .embeddings import LocalEmbedder, cosine

JD = "Backend developer. Java, Spring Boot, Kafka, PostgreSQL, Docker, REST APIs, microservices."
SAMPLES = {
    "closely related": "Built microservices with Java, Spring Boot and Kafka, backed by PostgreSQL and Docker.",
    "partly related": "Built a React and TypeScript dashboard that calls REST APIs.",
    "unrelated": "Head chef running a restaurant kitchen, specialised in pastry and baking.",
}

if __name__ == "__main__":
    s = get_settings()
    e = LocalEmbedder(s.embedding_model)
    q = e.embed_query(JD)
    print(f"model: {s.embedding_model}   SIM_LOW={s.sim_low}  SIM_HIGH={s.sim_high}")
    for label, text in SAMPLES.items():
        print(f"{label:16} cosine = {cosine(q, e.embed_passages([text])[0]):.3f}")
