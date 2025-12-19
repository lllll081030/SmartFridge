import os

# Backend API URL - configurable via environment variable
API_URL = os.environ.get("SMARTFRIDGE_API_URL", "http://localhost:8080/api")
