import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useMarkNotificationRead, useNotifications } from '../api/queries';
import { cx } from '../lib/cx';
import styles from './NotificationBell.module.css';
import { BellIcon } from './icons';

const dateFormat = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

export function NotificationBell() {
  const notifications = useNotifications();
  const markRead = useMarkNotificationRead();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const unread = notifications.data?.unread ?? 0;
  const items = notifications.data?.items ?? [];

  useEffect(() => {
    if (!open) {
      return;
    }
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  return (
    <div className={styles.root} ref={root}>
      <button
        type="button"
        className={styles.button}
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-label={unread > 0 ? `Уведомления, непрочитанных: ${unread}` : 'Уведомления'}
      >
        <BellIcon />
        {unread > 0 && <span className={styles.count}>{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className={styles.panel} role="region" aria-label="Список уведомлений">
          {items.length === 0 ? (
            <p className={styles.empty}>Пока ничего нового</p>
          ) : (
            <ul className={styles.list}>
              {items.map((item) => (
                <li key={item.id} className={cx(styles.item, !item.read && styles.unread)}>
                  <div className={styles.body}>
                    <strong className={styles.title}>{item.title}</strong>
                    <span className={styles.message}>{item.message}</span>
                    <span className={styles.meta}>
                      {dateFormat.format(new Date(item.createdAt))}
                      {item.orderId !== null && (
                        <>
                          {' · '}
                          <Link to={`/orders/${item.orderId}`} onClick={() => setOpen(false)}>
                            Заказ №{item.orderId}
                          </Link>
                        </>
                      )}
                    </span>
                  </div>
                  {!item.read && (
                    <button
                      type="button"
                      className={styles.read}
                      onClick={() => markRead.mutate(item.id)}
                      disabled={markRead.isPending}
                    >
                      Прочитано
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
