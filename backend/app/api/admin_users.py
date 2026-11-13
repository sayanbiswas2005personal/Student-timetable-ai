from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List

from backend.app.database.session import get_db
from backend.app.core.dependencies import require_admin
from backend.app.models.user import User, UserRole
from backend.app.schemas.user import UserCreate, UserUpdate, UserResponse
from backend.app.core.security import hash_password

router = APIRouter(prefix="/admin/users", tags=["User Management"])

@router.get("", response_model=List[UserResponse])
def get_users(db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    return db.query(User).order_by(User.id.desc()).all()

@router.post("", response_model=UserResponse)
def create_user(user_in: UserCreate, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    email_clean = user_in.email.strip().lower()
    existing = db.query(User).filter(User.email == email_clean).first()
    if existing:
        raise HTTPException(status_code=400, detail="User with this email already exists.")
    
    user = User(
        name=user_in.name.strip(),
        email=email_clean,
        password_hash=hash_password(user_in.password),
        role=user_in.role,
        department=user_in.department,
        is_active=True
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user

@router.put("/{id}", response_model=UserResponse)
def update_user(id: int, user_update: UserUpdate, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    user = db.query(User).filter(User.id == id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found.")
    
    if user_update.name is not None:
        user.name = user_update.name.strip()
    if user_update.role is not None:
        user.role = user_update.role
    if user_update.department is not None:
        user.department = user_update.department
    if user_update.is_active is not None:
        user.is_active = user_update.is_active
    if user_update.password:
        user.password_hash = hash_password(user_update.password)

    db.commit()
    db.refresh(user)
    return user

@router.delete("/{id}")
def delete_user(id: int, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    if id == current_user.id:
        raise HTTPException(status_code=400, detail="Cannot delete your own account.")
    user = db.query(User).filter(User.id == id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found.")
    db.delete(user)
    db.commit()
    return {"message": "User deleted."}

