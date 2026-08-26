import { Navigate, Outlet, useLocation } from 'react-router';
import { StateMessage } from '../components/StateMessage';
import { useAuth } from './AuthContext';

export function RequireRole({ role }: { role: string }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (user.role !== role) {
    return (
      <StateMessage
        role="alert"
        title="Раздел недоступен"
        text="Этот раздел открыт только для другой роли."
      />
    );
  }
  return <Outlet />;
}
