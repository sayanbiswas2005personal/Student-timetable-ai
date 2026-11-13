import pytest

def test_login_flow(client):
    """Req 10: Test authentication login for faculty and admin."""
    # Valid admin login
    res_admin = client.post("/api/auth/login", json={"email": "testadmin@studentai.com", "password": "adminpass"})
    assert res_admin.status_code == 200
    assert "access_token" in res_admin.json()
    assert res_admin.json()["user"]["role"] == "ADMIN"

    # Valid faculty login
    res_faculty = client.post("/api/auth/login", json={"email": "testfaculty@studentai.com", "password": "facultypass"})
    assert res_faculty.status_code == 200
    assert "access_token" in res_faculty.json()
    assert res_faculty.json()["user"]["role"] == "FACULTY"

    # Invalid credentials
    res_invalid = client.post("/api/auth/login", json={"email": "testadmin@studentai.com", "password": "wrongpassword"})
    assert res_invalid.status_code == 401

def test_role_authorization_enforcement(client, faculty_token, admin_token):
    """Req 10: Role-based authorization enforcement between Faculty and Admin."""
    # 1. Unauthenticated access to search should be 401
    res_no_auth = client.get("/api/search/current-class?department=CSE&year=3&semester=7&section=A")
    assert res_no_auth.status_code == 401

    # 2. Faculty can access search
    headers_faculty = {"Authorization": f"Bearer {faculty_token}"}
    res_fac_search = client.get("/api/search/current-class?department=CSE&year=3&semester=7&section=A", headers=headers_faculty)
    assert res_fac_search.status_code == 200

    # 3. Faculty CANNOT access admin endpoints (403 Forbidden)
    res_fac_admin = client.get("/api/admin/timetables", headers=headers_faculty)
    assert res_fac_admin.status_code == 403

    # 4. Admin CAN access admin endpoints (200 OK)
    headers_admin = {"Authorization": f"Bearer {admin_token}"}
    res_admin_access = client.get("/api/admin/timetables", headers=headers_admin)
    assert res_admin_access.status_code == 200

    # 5. Admin can also access faculty search endpoints
    res_admin_search = client.get("/api/search/current-class?department=CSE&year=3&semester=7&section=A", headers=headers_admin)
    assert res_admin_search.status_code == 200

