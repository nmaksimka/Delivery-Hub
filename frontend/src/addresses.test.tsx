import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';

const session = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 5, email: 'max@example.com', name: 'Максим', role: 'USER' },
};

function address(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    label: 'Дом',
    city: 'Томск',
    street: 'Ленина',
    house: '12',
    apartment: '4',
    entrance: null,
    floor: null,
    courierComment: null,
    primary: true,
    ...overrides,
  };
}

function callsTo(fetchMock: ReturnType<typeof stubApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => (init?.method ?? 'GET') === method && String(url).split('?')[0] === path,
  );
}

describe('addresses page', () => {
  it('listsAddressesWithPrimaryBadge', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/users/me/addresses': {
        body: [address(), address({ id: 2, label: 'Работа', primary: false })],
      },
    });
    renderApp('/addresses');

    expect(await screen.findByRole('heading', { name: 'Дом' })).toBeInTheDocument();
    expect(screen.getByText('Основной')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Сделать основным: Работа' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Сделать основным: Дом' })).not.toBeInTheDocument();
  });

  it('showsValidationErrorsAndDoesNotSendInvalidForm', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({ 'GET /api/users/me/addresses': { body: [] } });
    const user = userEvent.setup();
    renderApp('/addresses');

    await user.click(await screen.findByRole('button', { name: 'Добавить адрес' }));
    await user.click(screen.getByRole('button', { name: 'Сохранить адрес' }));

    expect(screen.getByText('Назовите адрес, например «Дом»')).toBeInTheDocument();
    expect(screen.getByText('Укажите улицу')).toBeInTheDocument();
    expect(screen.getByText('Укажите номер дома')).toBeInTheDocument();
    expect(callsTo(fetchMock, 'POST', '/api/users/me/addresses')).toHaveLength(0);
  });

  it('createsAddressInTomskAndRefreshesList', async () => {
    sessionStore.set(session);
    let saved = false;
    const fetchMock = stubApi({
      'GET /api/users/me/addresses': { body: () => (saved ? [address()] : []) },
      'POST /api/users/me/addresses': {
        status: 201,
        body: () => {
          saved = true;
          return address();
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/addresses');

    await user.click(await screen.findByRole('button', { name: 'Добавить адрес' }));
    await user.type(screen.getByLabelText('Название'), 'Дом');
    await user.type(screen.getByLabelText('Улица'), 'Ленина');
    await user.type(screen.getByLabelText('Дом'), '12');
    await user.click(screen.getByRole('button', { name: 'Сохранить адрес' }));

    expect(await screen.findByRole('heading', { name: 'Дом' })).toBeInTheDocument();
    const [, init] = callsTo(fetchMock, 'POST', '/api/users/me/addresses')[0] ?? [];
    expect(JSON.parse(String(init?.body))).toMatchObject({
      label: 'Дом',
      city: 'Томск',
      street: 'Ленина',
      house: '12',
    });
    expect(screen.queryByRole('form', { name: 'Адрес доставки' })).not.toBeInTheDocument();
  });

  it('explainsAddressLimitFromServer', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/users/me/addresses': { body: [address()] },
      'POST /api/users/me/addresses': {
        status: 409,
        body: { detail: 'limit', code: 'ADDRESS_LIMIT_REACHED' },
      },
    });
    const user = userEvent.setup();
    renderApp('/addresses');

    await user.click(await screen.findByRole('button', { name: 'Добавить адрес' }));
    await user.type(screen.getByLabelText('Название'), 'Дача');
    await user.type(screen.getByLabelText('Улица'), 'Мира');
    await user.type(screen.getByLabelText('Дом'), '1');
    await user.click(screen.getByRole('button', { name: 'Сохранить адрес' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('не больше 10 адресов');
  });

  it('disablesAddingWhenTenAddressesSaved', async () => {
    sessionStore.set(session);
    const ten = Array.from({ length: 10 }, (_, index) =>
      address({ id: index + 1, label: `Адрес ${index + 1}`, primary: index === 0 }),
    );
    stubApi({ 'GET /api/users/me/addresses': { body: ten } });
    renderApp('/addresses');

    expect(await screen.findByRole('button', { name: 'Добавить адрес' })).toBeDisabled();
  });

  it('makesAddressPrimary', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/users/me/addresses': {
        body: [address(), address({ id: 2, label: 'Работа', primary: false })],
      },
      'POST /api/users/me/addresses/2/default': { body: address({ id: 2, label: 'Работа' }) },
    });
    const user = userEvent.setup();
    renderApp('/addresses');

    await user.click(await screen.findByRole('button', { name: 'Сделать основным: Работа' }));

    expect(callsTo(fetchMock, 'POST', '/api/users/me/addresses/2/default')).toHaveLength(1);
  });

  it('deletesAddressAfterConfirm', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/users/me/addresses': { body: [address()] },
      'DELETE /api/users/me/addresses/1': { status: 204, body: null },
    });
    const user = userEvent.setup();
    renderApp('/addresses');

    await user.click(await screen.findByRole('button', { name: 'Удалить: Дом' }));
    expect(callsTo(fetchMock, 'DELETE', '/api/users/me/addresses/1')).toHaveLength(0);
    const dialog = screen.getByRole('alertdialog', { name: 'Удалить адрес?' });
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }));

    expect(callsTo(fetchMock, 'DELETE', '/api/users/me/addresses/1')).toHaveLength(1);
  });
});

describe('address choice in cart', () => {
  const pizzeria = {
    id: 1,
    name: 'Пицца на Ленина',
    address: null,
    phone: null,
    openTime: null,
    closeTime: null,
    active: true,
  };
  const cart = {
    restaurant: { id: 1, name: pizzeria.name, active: true, closed: false, workingHours: null },
    items: [
      {
        menuItemId: 10,
        name: 'Маргарита',
        price: 549,
        quantity: 1,
        lineTotal: 549,
        available: true,
      },
    ],
    subtotal: 549,
    discount: 0,
    total: 549,
    promoCode: null,
    promoProblem: null,
    orderable: true,
  };

  it('preselectsPrimaryAddressAndLetsPickAnother', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/cart': { body: cart },
      'GET /api/users/me/addresses': {
        body: [address(), address({ id: 2, label: 'Работа', street: 'Мира', primary: false })],
      },
    });
    const user = userEvent.setup();
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Корзина, 1 блюдо' }));
    const drawer = screen.getByRole('dialog', { name: 'Корзина' });
    const home = await within(drawer).findByRole('radio', { name: /Дом/ });
    const work = within(drawer).getByRole('radio', { name: /Работа/ });

    expect(home).toBeChecked();
    await user.click(work);

    expect(work).toBeChecked();
    expect(home).not.toBeChecked();
    expect(callsTo(fetchMock, 'POST', '/api/cart/checkout')).toHaveLength(0);
  });

  it('offersToAddFirstAddress', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/cart': { body: cart },
      'GET /api/users/me/addresses': { body: [] },
    });
    const user = userEvent.setup();
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Корзина, 1 блюдо' }));
    const drawer = screen.getByRole('dialog', { name: 'Корзина' });

    expect(await within(drawer).findByRole('link', { name: 'Добавить адрес' })).toHaveAttribute(
      'href',
      '/addresses',
    );
  });
});
