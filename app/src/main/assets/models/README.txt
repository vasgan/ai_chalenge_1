Place MediaPipe-compatible text embedding model here:
- file name: universal_sentence_encoder.tflite

RAG module is SAFE-BY-DEFAULT and will NOT start MediaPipe unless explicitly enabled.

To enable MediaPipe:
1) Put the model above.
2) Add file: enable_mediapipe.flag
3) File content must be exactly: true

Without this flag, app always uses local fallback embedding to avoid native crashes.
