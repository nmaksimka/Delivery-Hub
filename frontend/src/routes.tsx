import type { RouteObject } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { Layout } from './components/Layout';
import { AdminAnalyticsPage } from './pages/AdminAnalyticsPage';
import { AddressesPage } from './pages/AddressesPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { OrderPage } from './pages/OrderPage';
import { OrdersPage } from './pages/OrdersPage';
import { RegisterPage } from './pages/RegisterPage';
import { RestaurantPage } from './pages/RestaurantPage';
import { WalletPage } from './pages/WalletPage';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'restaurants/:restaurantId', element: <RestaurantPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        element: <RequireAuth />,
        children: [
          { path: 'orders', element: <OrdersPage /> },
          { path: 'orders/:orderId', element: <OrderPage /> },
          { path: 'wallet', element: <WalletPage /> },
          { path: 'addresses', element: <AddressesPage /> },
        ],
      },
      {
        path: 'admin',
        element: <RequireRole role="ADMIN" />,
        children: [{ path: 'analytics', element: <AdminAnalyticsPage /> }],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
