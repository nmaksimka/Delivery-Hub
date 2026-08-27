import { Navigate, type RouteObject } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { Layout } from './components/Layout';
import { AdminAnalyticsPage } from './pages/AdminAnalyticsPage';
import { AdminCouriersPage } from './pages/admin/AdminCouriersPage';
import { AdminImportsPage } from './pages/admin/AdminImportsPage';
import { AdminLayout } from './pages/admin/AdminLayout';
import { CabinetAnalyticsPage } from './pages/cabinet/CabinetAnalyticsPage';
import { CabinetHomePage } from './pages/cabinet/CabinetHomePage';
import { CabinetMenuPage } from './pages/cabinet/CabinetMenuPage';
import { CabinetOrdersPage } from './pages/cabinet/CabinetOrdersPage';
import { CabinetRestaurantLayout } from './pages/cabinet/CabinetRestaurantLayout';
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
        children: [
          {
            element: <AdminLayout />,
            children: [
              { index: true, element: <Navigate to="analytics" replace /> },
              { path: 'analytics', element: <AdminAnalyticsPage /> },
              { path: 'couriers', element: <AdminCouriersPage /> },
              { path: 'imports', element: <AdminImportsPage /> },
            ],
          },
        ],
      },
      {
        path: 'cabinet',
        element: <RequireRole role="RESTAURANT_OWNER" />,
        children: [
          { index: true, element: <CabinetHomePage /> },
          {
            path: ':restaurantId',
            element: <CabinetRestaurantLayout />,
            children: [
              { index: true, element: <Navigate to="menu" replace /> },
              { path: 'menu', element: <CabinetMenuPage /> },
              { path: 'orders', element: <CabinetOrdersPage /> },
              { path: 'analytics', element: <CabinetAnalyticsPage /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
