import { BrowserRouter, Route, Routes } from 'react-router-dom'

import { AuthProvider } from './api/AuthProvider'
import AppLayout from './components/layout/AppLayout'
import RequireAuth from './components/layout/RequireAuth'
import RequireRole from './components/layout/RequireRole'
import AdminAuditPage from './pages/AdminAuditPage'
import AdminStudentsPage from './pages/AdminStudentsPage'
import DashboardPage from './pages/DashboardPage'
import ImportReviewPage from './pages/ImportReviewPage'
import ImportPage from './pages/ImportPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import SectionTimetablePage from './pages/SectionTimetablePage'
import StudentLookupPage from './pages/StudentLookupPage'
import TimetableAdminPage from './pages/TimetableAdminPage'

/**
 * Routing.
 *
 * `RequireAuth` gates the whole application behind a session; `RequireRole` adds the
 * administrator check on the management screens. Both exist as components rather than scattered
 * conditionals so a new route cannot accidentally skip them.
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route element={<AppLayout />}>
              <Route index element={<DashboardPage />} />
              <Route path="lookup" element={<StudentLookupPage />} />
              <Route path="sections/:sectionId/timetable" element={<SectionTimetablePage />} />
              <Route element={<RequireRole role="ADMIN" />}>
                <Route path="admin/students" element={<AdminStudentsPage />} />
                <Route path="admin/timetables" element={<TimetableAdminPage />} />
                <Route path="admin/imports" element={<ImportPage />} />
                <Route path="admin/imports/:jobId" element={<ImportReviewPage />} />
                <Route path="admin/audit" element={<AdminAuditPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
