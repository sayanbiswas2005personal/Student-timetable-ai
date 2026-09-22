from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    PROJECT_NAME: str = "AU Student Timetable Intelligence System"
    DATABASE_URL: str = "postgresql://postgres:postgres@localhost:5432/timetable_db"
    GEMINI_API_KEY: Optional[str] = None
    
    class Config:
        env_file = ".env"
        extra = "ignore"

settings = Settings()
