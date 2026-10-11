import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { GuestOnly, RequireAuth, RequireRole } from './components/guards'
import ComingSoon from './pages/ComingSoon'
import Login from './pages/Login'
import Overview from './pages/Overview'
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
          <Route
            path="drives"
            element={
              <ComingSoon
                title="Drives"
                description="Browse company drives, check your eligibility and apply before the deadline."
              />
            }
          />

          <Route element={<RequireRole role="STUDENT" />}>
            <Route
              path="applications"
              element={
                <ComingSoon
                  title="Applications"
                  description="Follow each application from applied to offer, with the status of every round."
                />
              }
            />
          </Route>

          <Route element={<RequireRole role="TPR" />}>
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
