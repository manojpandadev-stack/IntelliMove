import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import CustomerDashboard from './pages/CustomerDashboard';
import DriverDashboard from './pages/DriverDashboard';
import AdminDashboard from './pages/AdminDashboard';
import RidesPage from './pages/RidesPage';
import RideDetailsPage from './pages/RideDetailsPage';
import PaymentsPage from './pages/PaymentsPage';
import NotificationsPage from './pages/NotificationsPage';
import ProfilePage from './pages/ProfilePage';
import SavedPlacesPage from './pages/SavedPlacesPage';
import SettingsPage from './pages/SettingsPage';
import HelpCenterPage from './pages/HelpCenterPage';
import SafetyPage from './pages/SafetyPage';
import ContactSupportPage from './pages/ContactSupportPage';
import DriverEarningsPage from './pages/DriverEarningsPage';
import DriverProfilePage from './pages/DriverProfilePage';
import AdminAiPage from './pages/AdminAiPage';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function RoleRoute({ roles, children }: { roles: string[]; children: React.ReactNode }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) return <Navigate to="/login" />;
  return <>{children}</>;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Rider */}
            <Route path="/dashboard" element={
              <ProtectedRoute><RoleRoute roles={['CUSTOMER']}><CustomerDashboard /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/rides" element={
              <ProtectedRoute><RoleRoute roles={['CUSTOMER']}><RidesPage /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/rides/:id" element={
              <ProtectedRoute><RoleRoute roles={['CUSTOMER']}><RideDetailsPage /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/payments" element={
              <ProtectedRoute><RoleRoute roles={['CUSTOMER']}><PaymentsPage /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/profile" element={
              <ProtectedRoute><RoleRoute roles={['CUSTOMER']}><ProfilePage /></RoleRoute></ProtectedRoute>
            } />

            {/* Driver */}
            <Route path="/driver" element={
              <ProtectedRoute><RoleRoute roles={['DRIVER']}><DriverDashboard /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/driver/earnings" element={
              <ProtectedRoute><RoleRoute roles={['DRIVER']}><DriverEarningsPage /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/driver/profile" element={
              <ProtectedRoute><RoleRoute roles={['DRIVER']}><DriverProfilePage /></RoleRoute></ProtectedRoute>
            } />

            {/* Admin */}
            <Route path="/admin" element={
              <ProtectedRoute><RoleRoute roles={['ADMIN', 'SUPER_ADMIN']}><AdminDashboard /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/admin/ai" element={
              <ProtectedRoute><RoleRoute roles={['ADMIN', 'SUPER_ADMIN']}><AdminAiPage /></RoleRoute></ProtectedRoute>
            } />

                                    {/* Shared (role-checked inside) */}
            <Route path="/notifications" element={
              <ProtectedRoute><NotificationsPage /></ProtectedRoute>
            } />
            <Route path="/saved-places" element={
              <ProtectedRoute><RoleRoute roles={['CUSTOMER']}><SavedPlacesPage /></RoleRoute></ProtectedRoute>
            } />
            <Route path="/settings" element={
              <ProtectedRoute><SettingsPage /></ProtectedRoute>
            } />
            <Route path="/help-center" element={
              <ProtectedRoute><HelpCenterPage /></ProtectedRoute>
            } />
            <Route path="/safety" element={
              <ProtectedRoute><SafetyPage /></ProtectedRoute>
            } />
            <Route path="/contact-support" element={
              <ProtectedRoute><ContactSupportPage /></ProtectedRoute>
            } />

            <Route path="*" element={<Navigate to="/login" />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}

