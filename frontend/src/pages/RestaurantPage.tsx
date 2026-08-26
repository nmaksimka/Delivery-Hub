import { useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError, errorMessage } from '../api/http';
import { useAddToCart, useChangeQuantity, useMenu, useRestaurant } from '../api/queries';
import type { MenuItem } from '../api/restaurants';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { Button } from '../components/Button';
import { Chips } from '../components/Chips';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Cover } from '../components/Cover';
import { ArrowLeftIcon, ClockIcon, PinIcon, StarIcon } from '../components/icons';
import { MenuItemCard } from '../components/MenuItemCard';
import { StateMessage } from '../components/StateMessage';
import { groupMenu } from '../lib/menuGroups';
import { orderErrorMessage } from '../lib/orderErrors';
import { placeholderEta } from '../lib/placeholders';
import { formatRating, ratingCountLabel } from '../lib/rating';
import { closedBadge, workingHoursLabel } from '../lib/workingHours';
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
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const addToCartMutation = useAddToCart();
  const changeQuantity = useChangeQuantity();
  const now = useNow();
  const [activeGroupId, setActiveGroupId] = useState<string | null>(null);
  const [pendingItem, setPendingItem] = useState<MenuItem | null>(null);
  const [askLogin, setAskLogin] = useState(false);
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
  const closedLabel = closedBadge(current, now);
  const hours = workingHoursLabel(current);
  const place = [current.address, current.city].filter(Boolean).join(', ');
  const cartRestaurant = cart.cart.restaurant;
  const otherRestaurant =
    cartRestaurant && cartRestaurant.id !== current.id && cart.count > 0 ? cartRestaurant : null;
  const cartError = addToCartMutation.error ?? changeQuantity.error;

  function dishName(menuItemId: number) {
    return menu.data?.find((item) => item.id === menuItemId)?.name;
  }

  function addToCart(item: MenuItem, replace: boolean) {
    changeQuantity.reset();
    addToCartMutation.mutate(
      { item: { restaurantId: current.id, menuItemId: item.id, quantity: 1 }, replace },
      {
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'CART_OTHER_RESTAURANT') {
            addToCartMutation.reset();
            setPendingItem(item);
          }
        },
      },
    );
  }

  function requestAdd(item: MenuItem) {
    if (!user) {
      setAskLogin(true);
    } else if (otherRestaurant) {
      setPendingItem(item);
    } else {
      addToCart(item, false);
    }
  }

  function decrement(item: MenuItem) {
    addToCartMutation.reset();
    changeQuantity.mutate({ menuItemId: item.id, quantity: cart.quantityOf(item.id) - 1 });
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
        <span className={styles.headMedia}>
          <Cover
            title={current.name}
            seed={current.id}
            imageUrl={current.imageUrl}
            className={styles.headCover}
          />
          {closedLabel && <span className={styles.closedBadge}>{closedLabel}</span>}
        </span>
        <div>
          {current.cuisine && <p className={styles.cuisine}>{current.cuisine}</p>}
          <h1 className={styles.title}>{current.name}</h1>
          <ul className={styles.meta}>
            <li className={styles.metaItem}>
              <StarIcon size={16} className={styles.star} />
              {formatRating(current.rating, current.ratingCount)}
              {current.ratingCount > 0 && (
                <span className={styles.ratingCount}>{ratingCountLabel(current.ratingCount)}</span>
              )}
            </li>
            <li className={styles.metaItem}>
              <ClockIcon size={16} />
              {placeholderEta(current.id)}
            </li>
            {hours && (
              <li className={styles.metaItem} aria-label={`Часы работы ${hours}`}>
                {hours}
              </li>
            )}
            {place && (
              <li className={styles.metaItem}>
                <PinIcon size={16} />
                {place}
              </li>
            )}
          </ul>
          {!current.active && (
            <p className={styles.notice}>Ресторан временно не принимает заказы.</p>
          )}
          {current.active && closedLabel && (
            <p className={styles.notice}>
              Сейчас закрыто. Меню можно посмотреть, заказ — после открытия.
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
                  orderable={!closedLabel}
                  onAdd={() => {
                    requestAdd(item);
                  }}
                  onDecrement={() => {
                    decrement(item);
                  }}
                />
              </li>
            ))}
          </ul>
        </section>
      ))}

      {cartError && (
        <p className={styles.cartError} role="alert">
          {orderErrorMessage(cartError, dishName)}
        </p>
      )}

      {pendingItem && (
        <ConfirmDialog
          title="Очистить корзину и добавить?"
          text={`В корзине блюда из «${otherRestaurant?.name ?? 'другого ресторана'}». Чтобы заказать в «${current.name}», её придётся очистить.`}
          confirmLabel="Очистить и добавить"
          cancelLabel="Оставить"
          onCancel={() => {
            setPendingItem(null);
          }}
          onConfirm={() => {
            addToCart(pendingItem, true);
            setPendingItem(null);
          }}
        />
      )}

      {askLogin && (
        <ConfirmDialog
          title="Войдите, чтобы собрать корзину"
          text="Корзина хранится в аккаунте: она не потеряется и будет ждать вас на любом устройстве."
          confirmLabel="Войти"
          cancelLabel="Позже"
          onCancel={() => {
            setAskLogin(false);
          }}
          onConfirm={() => {
            void navigate('/login', { state: { from: location.pathname } });
          }}
        />
      )}
    </>
  );
}
