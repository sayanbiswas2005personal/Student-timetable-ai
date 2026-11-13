import os
from pathlib import Path
from dotenv import load_dotenv

# Load root .env or backend .env
ROOT_DIR = Path(__file__).resolve().parent.parent.parent.parent
env_path = ROOT_DIR / ".env"
if env_path.exists():
    load_dotenv(dotenv_path=env_path)
else:
    load_dotenv()

class Settings:
    APP_NAME: str = "StudentAI.com"
    APP_VERSION: str = "1.0.0"
    
    # Gemini API Configuration
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "").strip()
    GEMINI_MODEL: str = os.getenv("GEMINI_MODEL", "gemini-3.8-flash").strip()
    
    # Database
    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///./studentai.db").strip()
    
    # Security
    SECRET_KEY: str = os.getenv("SECRET_KEY", "studentai-secret-key-2026-change-in-production-secure").strip()
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", "1440"))
    
    # Timezone
    TIMEZONE: str = os.getenv("TIMEZONE", "Asia/Kolkata").strip()
    
    # Storage
    STORAGE_DIR: Path = ROOT_DIR / os.getenv("STORAGE_DIR", "storage/timetables")
    MAX_UPLOAD_SIZE_MB: int = int(os.getenv("MAX_UPLOAD_SIZE_MB", "25"))
    
    def __init__(self):
        self.STORAGE_DIR.mkdir(parents=True, exist_ok=True)

settings = Settings()

