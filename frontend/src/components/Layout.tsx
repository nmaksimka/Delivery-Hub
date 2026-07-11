import { Outlet, ScrollRestoration } from 'react-router';
import { CartDrawer } from './CartDrawer';
import { Header } from './Header';
import styles from './Layout.module.css';

export function Layout() {
  return (
    <div className={styles.shell}>
      <Header />
      <main className={styles.main}>
        <Outlet />
      </main>
      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <span className={styles.footerBrand}>DeliveryHub</span>
          <span>Доставка еды из ресторанов Томска</span>
        </div>
      </footer>
      <CartDrawer />
      <ScrollRestoration />
    </div>
  );
}
