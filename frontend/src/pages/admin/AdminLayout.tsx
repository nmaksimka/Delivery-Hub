import { NavLink, Outlet } from 'react-router';
import { cx } from '../../lib/cx';
import styles from '../Management.module.css';

const tabs = [
  { to: 'analytics', label: 'Аналитика' },
  { to: 'couriers', label: 'Курьеры' },
  { to: 'imports', label: 'Импорт меню' },
];

export function AdminLayout() {
  return (
    <>
      <div className={styles.head}>
        <h1 className={styles.title}>Администрирование</h1>
      </div>
      <nav className={styles.tabs} aria-label="Разделы администратора">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) => cx(styles.tab, isActive && styles.tabActive)}
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  );
}
