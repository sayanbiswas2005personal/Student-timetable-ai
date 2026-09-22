from fastapi import APIRouter
from app.api.endpoints import health, student, timetable, admin

router = APIRouter()
router.include_router(health.router, tags=["health"])
router.include_router(student.router, prefix="/student", tags=["student"])
router.include_router(timetable.router, prefix="/timetable", tags=["timetable"])
router.include_router(admin.router, prefix="/admin", tags=["admin"])
