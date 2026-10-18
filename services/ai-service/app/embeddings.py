import hashlib
import re
from typing import Protocol

import numpy as np


class Embedder(Protocol):
    def embed_passages(self, texts: list[str]) -> list[list[float]]: ...

    def embed_query(self, text: str) -> list[float]: ...


class LocalEmbedder:
    """Free embeddings that run on this machine (fastembed, ONNX). The model, about 130 MB, is downloaded once on first use."""

    def __init__(self, model_name: str):
        self._model_name = model_name
        self._model = None

    def _load(self):
        if self._model is None:
            from fastembed import TextEmbedding  # imported late: it is slow to import and not needed by tests

            self._model = TextEmbedding(self._model_name)
        return self._model

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        return [v.tolist() for v in self._load().passage_embed(texts)]

    def embed_query(self, text: str) -> list[float]:
        return next(iter(self._load().query_embed([text]))).tolist()


class FakeEmbedder:
    """Bag-of-words hashing: texts sharing words get similar vectors. Deterministic, for tests only."""

    DIM = 256

    def _vec(self, text: str) -> list[float]:
        v = np.zeros(self.DIM)
        for word in re.findall(r"[a-z0-9+#.]+", text.lower()):
            v[int(hashlib.md5(word.encode()).hexdigest(), 16) % self.DIM] += 1.0
        norm = np.linalg.norm(v)
        return (v / norm).tolist() if norm else v.tolist()

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        return [self._vec(t) for t in texts]

    def embed_query(self, text: str) -> list[float]:
        return self._vec(text)


def cosine(a: list[float], b: list[float]) -> float:
    x, y = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
    denom = float(np.linalg.norm(x) * np.linalg.norm(y))
    return float(x @ y) / denom if denom else 0.0


def top_chunks(query_vec: list[float], chunks: list[dict], k: int) -> list[tuple[float, str]]:
    """The k resume pieces most similar to the query, best first, as (similarity, text)."""
    scored = [(cosine(query_vec, c["embedding"]), c["text"]) for c in chunks]
    scored.sort(key=lambda s: s[0], reverse=True)
    return scored[:k]


def similarity_score(best: list[tuple[float, str]], low: float, high: float) -> int:
    """0-100 fit from the best matching pieces: the mean of the top 3 similarities, stretched between low and high."""
    if not best:
        return 0
    top = [s for s, _ in best[:3]]
    mean = sum(top) / len(top)
    return int(round(max(0.0, min(1.0, (mean - low) / (high - low))) * 100))

