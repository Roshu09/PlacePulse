import re


def chunk_resume(text: str, max_chars: int = 600) -> list[str]:
    """Cuts a resume into pieces of about max_chars, on line and sentence boundaries, so each piece is one idea
    (a project, a job, a skills list) that can be matched on its own."""
    lines = [re.sub(r"[ \t]+", " ", ln).strip() for ln in text.replace("\r", "").split("\n")]
    pieces: list[str] = []
    current = ""
    for line in lines:
        if not line:
            if len(current) >= max_chars // 2:
                pieces.append(current)
                current = ""
            continue
        for part in _split_long(line, max_chars):
            if current and len(current) + len(part) + 1 > max_chars:
                pieces.append(current)
                current = part
            else:
                current = f"{current}\n{part}" if current else part
    if current:
        pieces.append(current)
    return [p for p in pieces if len(p) >= 20]


def _split_long(line: str, max_chars: int) -> list[str]:
    if len(line) <= max_chars:
        return [line]
    out, current = [], ""
    for sentence in re.split(r"(?<=[.!?;])\s+", line):
        while len(sentence) > max_chars:  # no punctuation at all: cut by words
            cut = sentence.rfind(" ", 0, max_chars)
            cut = cut if cut > 0 else max_chars
            out.append(sentence[:cut])
            sentence = sentence[cut:].strip()
        if current and len(current) + len(sentence) + 1 > max_chars:
            out.append(current)
            current = sentence
        else:
            current = f"{current} {sentence}".strip()
    if current:
        out.append(current)
    return out
