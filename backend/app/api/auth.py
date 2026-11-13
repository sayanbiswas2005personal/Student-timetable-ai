from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional

from backend.app.database.session import get_db
from backend.app.models.user import User
from backend.app.schemas.user import UserLogin, TokenResponse, UserResponse
from backend.app.core.security import verify_password, create_access_token
from backend.app.core.dependencies import get_current_user

router = APIRouter(prefix="/auth", tags=["Authentication"])

class DirectLoginRequest(BaseModel):
    email: str
    password: str

@router.post("/login", response_model=TokenResponse)
def login(login_req: DirectLoginRequest, db: Session = Depends(get_db)):
    """
    Authenticate a user (Faculty or Admin) and return a signed JWT.
    """
    email_clean = login_req.email.strip().lower()
    user = db.query(User).filter(User.email == email_clean).first()
    
    if not user or not verify_password(login_req.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Your account has been deactivated. Please contact an administrator."
        )

    access_token = create_access_token(data={"sub": user.email, "role": user.role.value, "uid": user.id})
    return TokenResponse(
        access_token=access_token,
        token_type="bearer",
        user=UserResponse.model_validate(user)
    )

@router.post("/logout")
def logout():
    """
    Client-side JWT logout confirmation.
    """
    return {"message": "Successfully logged out."}

@router.get("/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    """
    Return currently authenticated user profile.
    """
    return UserResponse.model_validate(current_user)

