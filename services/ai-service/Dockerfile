FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
# the embedding model (about 130 MB) is downloaded on first use and kept in this folder
ENV FASTEMBED_CACHE_PATH=/models
VOLUME /models
EXPOSE 8084
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8084"]
