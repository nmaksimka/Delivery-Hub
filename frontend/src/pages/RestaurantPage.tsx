import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError, errorMessage } from '../api/http';
import { useMenu, useRestaurant } from '../api/queries';
import type { MenuItem } from '../api/restaurants';
import { useCart } from '../cart/CartContext';
import { Button } from '../components/Button';
import { Chips } from '../components/Chips';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Cover } from '../components/Cover';
import { ArrowLeftIcon, ClockIcon, PinIcon, StarIcon } from '../components/icons';
import { MenuItemCard } from '../components/MenuItemCard';
import { StateMessage } from '../components/StateMessage';
import { formatClock } from '../lib/format';
import { groupMenu } from '../lib/menuGroups';
import { placeholderEta, placeholderRating } from '../lib/placeholders';
import { isOpenAt } from '../lib/restaurantFilters';
import { useNow } from '../lib/useNow';
import { NotFoundPage } from './NotFoundPage';
import styles from './RestaurantPage.module.css';

const skeletons = [1, 2, 3, 4];

export function RestaurantPage() {
  const params = useParams();
  const restaurantId = Number(params.restaurantId);
  const restaurant = useRestaurant(restaurantId);
  const menu = useMenu(restaurantId);
  const cart = useCart();
  const now = useNow();
  const [activeGroupId, setActiveGroupId] = useState<string | null>(null);
  const [pendingItem, setPendingItem] = useState<MenuItem | null>(null);
  const groups = useMemo(() => groupMenu(menu.data ?? []), [menu.data]);

  const missing =
    !Number.isInteger(restaurantId) ||
    (restaurant.error instanceof ApiError && restaurant.error.status === 404);
  if (missing) {
    return <NotFoundPage />;
  }

  if (restaurant.isPending) {
    return <div className={styles.headSkeleton} aria-busy="true" aria-label="Загружаем ресторан" />;
  }

  if (restaurant.isError) {
    return (
      <StateMessage
        role="alert"
        title="Не получилось открыть ресторан"
        text={errorMessage(restaurant.error)}
      >
        <Button
          variant="dark"
          onClick={() => {
            void restaurant.refetch();
          }}
        >
          Повторить
        </Button>
      </StateMessage>
    );
  }

  const current = restaurant.data;
  const open = isOpenAt(current, now);
  const opensAt = formatClock(current.openTime);
  const closesAt = formatClock(current.closeTime);
  const otherRestaurant =
    cart.restaurant && cart.restaurant.id !== current.id ? cart.restaurant : null;

  function addToCart(item: MenuItem) {
    cart.add(
      { id: current.id, name: current.name },
      { menuItemId: item.id, name: item.name, price: item.price },
    );
  }

  function requestAdd(item: MenuItem) {
    if (otherRestaurant) {
      setPendingItem(item);
    } else {
      addToCart(item);
    }
  }

  function scrollToGroup(groupId: string) {
    setActiveGroupId(groupId);
    document.getElementById(groupId)?.scrollIntoView({ block: 'start' });
  }

  return (
    <>
      <Link to="/" className={styles.back}>
        <ArrowLeftIcon size={18} />
        Все рестораны
      </Link>

      <header className={styles.head}>
        <Cover title={current.name} seed={current.id} className={styles.headCover} />
        <div>
          <h1 className={styles.title}>{current.name}</h1>
          <ul className={styles.meta}>
            <li className={styles.metaItem}>
              <StarIcon size={16} className={styles.star} />
              {placeholderRating(current.id)}
            </li>
            <li className={styles.metaItem}>
              <ClockIcon size={16} />
              {placeholderEta(current.id)}
            </li>
            {opensAt && closesAt && (
              <li className={styles.metaItem}>
                {opensAt}–{closesAt}
              </li>
            )}
            {current.address && (
              <li className={styles.metaItem}>
                <PinIcon size={16} />
                {current.address}
              </li>
            )}
          </ul>
          {!current.active && (
            <p className={styles.notice}>Ресторан временно не принимает заказы.</p>
          )}
          {current.active && !open && (
            <p className={styles.notice}>
              Сейчас закрыто{opensAt ? `, откроется в ${opensAt}` : ''}.
            </p>
          )}
        </div>
      </header>

      {groups.length > 1 && (
        <div className={styles.categories}>
          <Chips
            label="Разделы меню"
            options={groups.map((group) => ({ id: group.id, label: group.title }))}
            activeId={activeGroupId}
            onSelect={scrollToGroup}
          />
        </div>
      )}

      {menu.isPending && (
        <div className={styles.group}>
          <div className={styles.grid} aria-busy="true" aria-label="Загружаем меню">
            {skeletons.map((key) => (
              <div key={key} className={styles.skeleton} />
            ))}
          </div>
        </div>
      )}

      {menu.isError && (
        <StateMessage role="alert" title="Меню не загрузилось" text={errorMessage(menu.error)}>
          <Button
            variant="dark"
            onClick={() => {
              void menu.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {menu.isSuccess && groups.length === 0 && (
        <StateMessage
          title="Меню пока пустое"
          text="Ресторан ещё не добавил блюда. Загляните позже."
        />
      )}

      {groups.map((group) => (
        <section key={group.id} id={group.id} className={styles.group}>
          <h2 className={styles.groupTitle}>{group.title}</h2>
          <ul className={styles.grid}>
            {group.items.map((item) => (
              <li key={item.id}>
                <MenuItemCard
                  item={item}
                  quantity={otherRestaurant ? 0 : cart.quantityOf(item.id)}
                  orderable={current.active && item.available}
                  onAdd={() => {
                    requestAdd(item);
                  }}
                  onDecrement={() => {
                    cart.decrement(item.id);
                  }}
                />
              </li>
            ))}
          </ul>
        </section>
      ))}

      {pendingItem && otherRestaurant && (
        <ConfirmDialog
          title="Начать новую корзину?"
          text={`В корзине блюда из «${otherRestaurant.name}». Чтобы заказать в «${current.name}», её придётся очистить.`}
          confirmLabel="Очистить и добавить"
          cancelLabel="Оставить"
          onCancel={() => {
            setPendingItem(null);
          }}
          onConfirm={() => {
            addToCart(pendingItem);
            setPendingItem(null);
          }}
        />
      )}
    </>
  );
}
