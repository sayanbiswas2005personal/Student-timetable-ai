from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List
from pydantic import BaseModel, ConfigDict
from datetime import datetime

from backend.app.database.session import get_db
from backend.app.core.dependencies import require_admin
from backend.app.models.user import User
from backend.app.models.audit_log import AuditLog

router = APIRouter(prefix="/admin/audit-logs", tags=["Audit Logs"])

class AuditLogResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int | None
    user_email: str | None
    action: str
    details: str | None
    timestamp: datetime

@router.get("", response_model=List[AuditLogResponse])
def get_audit_logs(
    limit: int = 50,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    return db.query(AuditLog).order_by(AuditLog.timestamp.desc()).limit(limit).all()
