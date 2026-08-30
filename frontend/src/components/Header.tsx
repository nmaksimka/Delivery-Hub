import { Link, NavLink } from 'react-router';
import { useWallet } from '../api/queries';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { cx } from '../lib/cx';
import { formatPrice, plural } from '../lib/format';
import styles from './Header.module.css';
import { NotificationBell } from './NotificationBell';
import { BagIcon, LogoutIcon, PinIcon, ReceiptIcon, UserIcon, WalletIcon } from './icons';

export function Header() {
  const { user, logout } = useAuth();
  const cart = useCart();
  const wallet = useWallet();
  const cartLabel =
    cart.count > 0
      ? `Корзина, ${cart.count} ${plural(cart.count, 'блюдо', 'блюда', 'блюд')}`
      : 'Корзина пуста';

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link to="/" className={styles.logo} aria-label="DeliveryHub, на главную">
          <span className={styles.logoMark} aria-hidden="true">
            D
          </span>
          <span className={styles.logoText}>DeliveryHub</span>
        </Link>
        <span className={styles.city}>
          <PinIcon size={16} />
          Томск
        </span>

        <nav className={styles.nav} aria-label="Основная навигация">
          {user ? (
            <>
              <NotificationBell />
              <NavLink
                to="/wallet"
                aria-label={
                  wallet.data
                    ? `Кошелёк, на счёте ${formatPrice(wallet.data.available)}`
                    : 'Кошелёк'
                }
                className={({ isActive }) =>
                  cx(styles.link, styles.wallet, isActive && styles.linkActive)
                }
              >
                <WalletIcon />
                <span className={styles.balance}>
                  {wallet.data ? formatPrice(wallet.data.available) : 'Кошелёк'}
                </span>
              </NavLink>
              <NavLink
                to="/addresses"
                aria-label="Мои адреса"
                className={({ isActive }) => cx(styles.link, isActive && styles.linkActive)}
              >
                <PinIcon />
                <span className={styles.label}>Адреса</span>
              </NavLink>
              <NavLink
                to="/orders"
                aria-label="Мои заказы"
                className={({ isActive }) => cx(styles.link, isActive && styles.linkActive)}
              >
                <ReceiptIcon />
                <span className={styles.label}>Заказы</span>
              </NavLink>
              {user.role === 'ADMIN' && (
                <NavLink
                  to="/admin"
                  className={({ isActive }) => cx(styles.link, isActive && styles.linkActive)}
                >
                  <span className={styles.label}>Админка</span>
                </NavLink>
              )}
              {user.role === 'RESTAURANT_OWNER' && (
                <NavLink
                  to="/cabinet"
                  className={({ isActive }) => cx(styles.link, isActive && styles.linkActive)}
                >
                  <span className={styles.label}>Кабинет</span>
                </NavLink>
              )}
              <button
                type="button"
                className={styles.link}
                onClick={logout}
                aria-label={`Выйти из аккаунта ${user.name}`}
              >
                <LogoutIcon />
                <span className={styles.label}>Выйти</span>
              </button>
            </>
          ) : (
            <NavLink
              to="/login"
              aria-label="Войти"
              className={({ isActive }) => cx(styles.link, isActive && styles.linkActive)}
            >
              <UserIcon />
              <span className={styles.label}>Войти</span>
            </NavLink>
          )}
          <button type="button" className={styles.cart} onClick={cart.open} aria-label={cartLabel}>
            <BagIcon />
            <span className={styles.label}>Корзина</span>
            {cart.count > 0 && <span className={styles.cartCount}>{cart.count}</span>}
          </button>
        </nav>
      </div>
    </header>
  );
}
