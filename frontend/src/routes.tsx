import type { RouteObject } from 'react-router';
import { Layout } from './components/Layout';
import { NotFoundPage } from './pages/NotFoundPage';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: [{ path: '*', element: <NotFoundPage /> }],
  },
];
