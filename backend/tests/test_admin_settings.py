import pytest

def test_admin_settings_access(client, admin_token, faculty_token):
    """Verify settings endpoint role authorization and data retrieval."""
    headers_admin = {"Authorization": f"Bearer {admin_token}"}
    headers_faculty = {"Authorization": f"Bearer {faculty_token}"}

    # 1. Faculty cannot access settings (403)
    res_fac = client.get("/api/admin/settings", headers=headers_faculty)
    assert res_fac.status_code == 403

    # 2. Admin can access settings
    res_adm = client.get("/api/admin/settings", headers=headers_admin)
    assert res_adm.status_code == 200
    data = res_adm.json()
    assert "gemini_api_key_configured" in data
    assert "gemini_model" in data
    assert "timezone" in data

def test_admin_settings_update(client, admin_token):
    """Verify updating settings persists at runtime."""
    headers_admin = {"Authorization": f"Bearer {admin_token}"}

    # Update timezone and model
    res_up = client.post(
        "/api/admin/settings",
        json={"gemini_model": "gemini-3.8-flash", "timezone": "Asia/Kolkata"},
        headers=headers_admin
    )
    assert res_up.status_code == 200
    assert res_up.json()["gemini_model"] == "gemini-3.8-flash"
    assert res_up.json()["timezone"] == "Asia/Kolkata"

