import os
from pathlib import Path
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from typing import Optional

from backend.app.core.config import settings
from backend.app.core.dependencies import require_admin
from backend.app.models.user import User
from backend.app.models.audit_log import AuditLog
from backend.app.database.session import get_db
from sqlalchemy.orm import Session

router = APIRouter(prefix="/admin/settings", tags=["Admin Settings"])

class SettingsResponse(BaseModel):
    gemini_api_key_configured: bool
    gemini_api_key_masked: str
    gemini_model: str
    timezone: str
    database_url: str
    storage_dir: str
    max_upload_size_mb: int

class SettingsUpdateRequest(BaseModel):
    gemini_api_key: Optional[str] = None
    gemini_model: Optional[str] = None
    timezone: Optional[str] = None

class TestConnectionRequest(BaseModel):
    api_key: Optional[str] = None
    model: Optional[str] = None

def mask_key(k: str) -> str:
    if not k:
        return ""
    if len(k) <= 8:
        return "********"
    return f"{k[:4]}...{k[-4:]}"

def update_env_file(key: str, value: str):
    """Safely updates or adds a key=value line in the root .env file."""
    env_path = Path(__file__).resolve().parent.parent.parent.parent / ".env"
    lines = []
    found = False
    if env_path.exists():
        with open(env_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

    new_lines = []
    for line in lines:
        if line.strip().startswith(f"{key}=") or line.strip().startswith(f"export {key}="):
            new_lines.append(f"{key}={value}\n")
            found = True
        else:
            new_lines.append(line)

    if not found:
        new_lines.append(f"{key}={value}\n")

    with open(env_path, "w", encoding="utf-8") as f:
        f.writelines(new_lines)

@router.get("", response_model=SettingsResponse)
def get_settings(current_user: User = Depends(require_admin)):
    """
    Returns application configuration status for the admin.
    """
    return SettingsResponse(
        gemini_api_key_configured=bool(settings.GEMINI_API_KEY),
        gemini_api_key_masked=mask_key(settings.GEMINI_API_KEY),
        gemini_model=settings.GEMINI_MODEL,
        timezone=settings.TIMEZONE,
        database_url=settings.DATABASE_URL.split("///")[-1] if "sqlite" in settings.DATABASE_URL else "Configured",
        storage_dir=str(settings.STORAGE_DIR),
        max_upload_size_mb=settings.MAX_UPLOAD_SIZE_MB
    )

@router.post("")
def update_settings(
    req: SettingsUpdateRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Updates system settings (including Google Gemini API credentials and model).
    Persists to both memory and .env file.
    """
    updated_fields = []

    if req.gemini_api_key is not None:
        key_val = req.gemini_api_key.strip()
        settings.GEMINI_API_KEY = key_val
        update_env_file("GEMINI_API_KEY", key_val)
        updated_fields.append("GEMINI_API_KEY")

    if req.gemini_model is not None and req.gemini_model.strip():
        model_val = req.gemini_model.strip()
        settings.GEMINI_MODEL = model_val
        update_env_file("GEMINI_MODEL", model_val)
        updated_fields.append("GEMINI_MODEL")

    if req.timezone is not None and req.timezone.strip():
        tz_val = req.timezone.strip()
        settings.TIMEZONE = tz_val
        update_env_file("TIMEZONE", tz_val)
        updated_fields.append("TIMEZONE")

    audit = AuditLog(
        user_id=current_user.id,
        user_email=current_user.email,
        action="UPDATE_SETTINGS",
        details=f"Updated configuration fields: {', '.join(updated_fields)}"
    )
    db.add(audit)
    db.commit()

    return {
        "message": "Settings updated successfully.",
        "gemini_api_key_configured": bool(settings.GEMINI_API_KEY),
        "gemini_api_key_masked": mask_key(settings.GEMINI_API_KEY),
        "gemini_model": settings.GEMINI_MODEL,
        "timezone": settings.TIMEZONE
    }

@router.post("/test-gemini")
def test_gemini_connection(
    req: TestConnectionRequest,
    current_user: User = Depends(require_admin)
):
    """
    Test Google Gemini API connectivity with the provided or stored API key.
    """
    key_to_test = req.api_key.strip() if req.api_key else settings.GEMINI_API_KEY
    if not key_to_test:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No Gemini API Key provided or configured."
        )

    model_to_use = req.model.strip() if req.model else settings.GEMINI_MODEL

    try:
        from google import genai
        client = genai.Client(api_key=key_to_test)

        # Quick test generation call
        interaction = client.interactions.create(
            model=model_to_use,
            input="Ping. Reply with 'Pong - StudentAI.com connection verified'."
        )

        return {
            "success": True,
            "message": "Successfully connected to Google Gemini API!",
            "model_tested": model_to_use,
            "response": interaction.output_text
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Google Gemini API connection test failed: {str(e)}"
        )

