import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from backend.app.core.config import settings
from backend.app.database.init_db import init_db
from backend.app.api import (
    auth,
    search,
    admin_timetables,
    admin_academic,
    admin_users,
    admin_overrides,
    admin_settings,
    audit
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("studentai")

allowed_origins = (
    ["*"]
    if settings.ALLOWED_ORIGINS == "*"
    else [
        origin.strip()
        for origin in settings.ALLOWED_ORIGINS.split(",")
        if origin.strip()
    ]
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"Starting {settings.APP_NAME} v{settings.APP_VERSION}...")
    try:
        init_db()
        logger.info("Database initialized successfully.")
    except Exception as e:
        logger.error(f"Database initialization error: {e}", exc_info=True)
    yield
    logger.info("Shutting down StudentAI.com...")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="Campus Timetable & Student Verification System for Faculty and Administrators.",
    lifespan=lifespan
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global Exception Handler
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception on {request.method} {request.url}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "An internal server error occurred. Please contact the administrator."}
    )

# Register API Routers
app.include_router(auth.router, prefix="/api")
app.include_router(search.router, prefix="/api")
app.include_router(admin_timetables.router, prefix="/api")
app.include_router(admin_academic.router, prefix="/api")
app.include_router(admin_users.router, prefix="/api")
app.include_router(admin_overrides.router, prefix="/api")
app.include_router(admin_settings.router, prefix="/api")
app.include_router(audit.router, prefix="/api")

@app.get("/api/health")
def health_check():
    return {
        "status": "healthy",
        "app": settings.APP_NAME,
        "gemini_configured": bool(settings.GEMINI_API_KEY)
    }

# Serve Frontend static assets if built
from pathlib import Path
from fastapi.responses import FileResponse

frontend_dist = Path(__file__).resolve().parent.parent.parent / "frontend" / "dist"

if frontend_dist.exists():
    app.mount("/assets", StaticFiles(directory=str(frontend_dist / "assets")), name="assets")

    @app.get("/{full_path:path}")
    async def serve_spa(full_path: str):
        # Don't hijack API, docs, or OpenAPI routes
        if full_path.startswith("api") or full_path.startswith("docs") or full_path == "openapi.json":
            return JSONResponse(status_code=404, content={"detail": "Not Found"})
        file_target = frontend_dist / full_path
        if file_target.is_file():
            return FileResponse(file_target)
        return FileResponse(frontend_dist / "index.html")
else:
    @app.get("/")
    def root():
        return {
            "app": settings.APP_NAME,
            "version": settings.APP_VERSION,
            "status": "online",
            "timezone": settings.TIMEZONE,
            "docs_url": "/docs"
        }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.app.main:app", host="0.0.0.0", port=8000, reload=True)
