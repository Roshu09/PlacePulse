import io

from pypdf import PdfReader
from pypdf.errors import PyPdfError


class ResumeUnreadable(Exception):
    pass


def extract_text(data: bytes) -> str:
    """The text of a PDF, pages joined by blank lines. Raises ResumeUnreadable for anything that is not a usable PDF."""
    if not data.startswith(b"%PDF"):
        raise ResumeUnreadable("That file is not a PDF.")
    try:
        reader = PdfReader(io.BytesIO(data))
        if reader.is_encrypted:
            raise ResumeUnreadable("That PDF is password protected. Remove the password and upload it again.")
        pages = [(page.extract_text() or "").strip() for page in reader.pages[:6]]  # a resume is not longer than that
    except ResumeUnreadable:
        raise
    except (PyPdfError, ValueError, KeyError, OSError):
        raise ResumeUnreadable("Could not read that PDF. Export it again and retry.") from None
    text = "\n\n".join(p for p in pages if p)
    if len(text) < 200:
        raise ResumeUnreadable("No readable text found. If this is a scanned image, export a text PDF from your editor instead.")
    return text
