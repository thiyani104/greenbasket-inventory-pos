import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import { Spinner } from './components/ui';
import ActivityPage from './pages/ActivityPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import DashboardPage from './pages/DashboardPage';
import { ForgotPage, ResetPage } from './pages/ForgotReset';
import LoginPage from './pages/LoginPage';
import PosPage from './pages/PosPage';
import ProductsPage from './pages/ProductsPage';
import PurchasingPage from './pages/PurchasingPage';
import ReportsPage from './pages/ReportsPage';
import SalesPage from './pages/SalesPage';
import SettingsPage from './pages/SettingsPage';
import UsersPage from './pages/UsersPage';

function Protected({ children, manager }: { children: ReactElement; manager?: boolean }) {
  const { user, isManager } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  if (manager && !isManager) return <Navigate to="/pos" replace />;
  return children;
}

export default function App() {
  const { ready, user, isManager } = useAuth();
  if (!ready) return <Spinner />;
  const home = isManager ? '/' : '/pos';
  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to={user.mustChangePassword ? '/change-password' : home} replace /> : <LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPage />} />
      <Route path="/reset-password" element={<ResetPage />} />
      <Route path="/change-password" element={user ? <ChangePasswordPage /> : <Navigate to="/login" replace />} />
      <Route element={<Protected><Layout /></Protected>}>
        <Route index element={isManager ? <DashboardPage /> : <Navigate to="/pos" replace />} />
        <Route path="pos" element={<PosPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="sales" element={<SalesPage />} />
        <Route path="purchasing" element={<Protected manager><PurchasingPage /></Protected>} />
        <Route path="reports" element={<Protected manager><ReportsPage /></Protected>} />
        <Route path="users" element={<Protected manager><UsersPage /></Protected>} />
        <Route path="activity" element={<Protected manager><ActivityPage /></Protected>} />
        <Route path="settings" element={<Protected manager><SettingsPage /></Protected>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
