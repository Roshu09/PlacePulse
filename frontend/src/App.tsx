import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { GuestOnly, RequireAuth, RequireRole } from './components/guards'
import Applications from './pages/Applications'
import ComingSoon from './pages/ComingSoon'
import DriveApplicants from './pages/DriveApplicants'
import Drives from './pages/Drives'
import Login from './pages/Login'
import Overview from './pages/Overview'
import Profile from './pages/Profile'
import Register from './pages/Register'

export default function App() {
  return (
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<Overview />} />
          <Route path="drives" element={<Drives />} />

          <Route element={<RequireRole role="STUDENT" />}>
            <Route path="applications" element={<Applications />} />
            <Route path="profile" element={<Profile />} />
          </Route>

          <Route element={<RequireRole role="TPR" />}>
            <Route path="drives/:id" element={<DriveApplicants />} />
            <Route
              path="students"
              element={
                <ComingSoon
                  title="Students"
                  description="See who has registered, who applied where, and who is still waiting on a result."
                />
              }
            />
            <Route
              path="analytics"
              element={
                <ComingSoon
                  title="Analytics"
                  description="Batch-wide numbers for every drive: applications, shortlists and offers."
                />
              }
            />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
