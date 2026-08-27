import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { formatRating, ratingCountLabel } from './lib/rating';
import { renderApp, stubApi } from './test/renderApp';

const session = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 5, email: 'max@example.com', name: 'Максим', role: 'USER' },
};

const pizzeria = {
  id: 1,
  name: 'Пицца на Ленина',
  address: null,
  phone: null,
  openTime: null,
  closeTime: null,
  active: true,
  rating: 4.5,
  ratingCount: 12,
};

const sushiBar = { ...pizzeria, id: 2, name: 'Суши Томь', rating: null, ratingCount: 0 };

function ordersPage(status: string) {
  return {
    content: [
      {
        id: 77,
        userId: 5,
        restaurantId: 1,
        status,
        cancellationReason: null,
        refundedAt: null,
        totalAmount: '1218.00',
        discount: '0.00',
        promoCode: null,
        createdAt: '2026-07-01T12:00:00Z',
        updatedAt: '2026-07-01T12:00:00Z',
        orderItems: [
          { id: 1, menuItemId: 10, menuItemName: 'Маргарита', quantity: 2, pricePerUnit: 549 },
        ],
      },
    ],
    number: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  };
}

function reviewsPage(content: unknown[]) {
  return { content, number: 0, size: 200, totalElements: content.length, totalPages: 1 };
}

const savedReview = {
  id: 3,
  orderId: 77,
  restaurantId: 1,
  courierId: 9001,
  restaurantRating: 5,
  courierRating: 4,
  comment: 'Горячая пицца',
  createdAt: '2026-07-01T13:00:00Z',
};

describe('rating formatting', () => {
  it('showsPlaceholderWhenNobodyRatedYet', () => {
    expect(formatRating(null, 0)).toBe('Нет оценок');
    expect(formatRating(4.5, 12)).toBe('4,5');
  });

  it('declinesRatingCount', () => {
    expect(ratingCountLabel(1)).toBe('1 оценка');
    expect(ratingCountLabel(3)).toBe('3 оценки');
    expect(ratingCountLabel(12)).toBe('12 оценок');
  });
});

describe('restaurant rating', () => {
  it('showsAverageRatingInCatalog', async () => {
    stubApi({ 'GET /api/restaurants': { body: [pizzeria, sushiBar] } });
    renderApp('/');

    expect(await screen.findByText('4,5')).toBeInTheDocument();
    expect(screen.getByText('12 оценок')).toBeInTheDocument();
    expect(screen.getByText('Нет оценок')).toBeInTheDocument();
  });
});

describe('order review', () => {
  it('submitsRatingsAndCommentForDeliveredOrder', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/orders': { body: ordersPage('DELIVERED') },
      'GET /api/orders/reviews/mine': { body: reviewsPage([]) },
      'POST /api/orders/77/review': { status: 201, body: savedReview },
    });
    const user = userEvent.setup();
    renderApp('/orders');

    await user.click(await screen.findByRole('button', { name: 'Оставить отзыв' }));
    const submit = screen.getByRole('button', { name: 'Отправить' });
    expect(submit).toBeDisabled();

    await user.click(
      within(screen.getByRole('radiogroup', { name: 'Оценка ресторана' })).getByRole('radio', {
        name: '5 из 5',
      }),
    );
    await user.type(screen.getByRole('textbox', { name: /Отзыв/ }), 'Горячая пицца');
    expect(submit).toBeEnabled();
    await user.click(submit);

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) => String(url) === '/api/orders/77/review');
      expect(call?.[1]?.method).toBe('POST');
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({
        restaurantRating: 5,
        courierRating: null,
        comment: 'Горячая пицца',
      });
    });
  });

  it('showsGivenReviewInsteadOfTheForm', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/orders': { body: ordersPage('DELIVERED') },
      'GET /api/orders/reviews/mine': { body: reviewsPage([savedReview]) },
    });
    renderApp('/orders');

    expect(await screen.findByText('Ваша оценка ресторана: 5, курьера: 4')).toBeInTheDocument();
    expect(screen.getByText('Горячая пицца')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Оставить отзыв' })).not.toBeInTheDocument();
  });

  it('doesNotOfferReviewForUndeliveredOrder', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/orders': { body: ordersPage('IN_DELIVERY') },
      'GET /api/orders/reviews/mine': { body: reviewsPage([]) },
    });
    renderApp('/orders');

    expect(await screen.findByRole('link', { name: 'Следить' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Оставить отзыв' })).not.toBeInTheDocument();
  });

  it('explainsWhyReviewWasRejected', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/orders': { body: ordersPage('DELIVERED') },
      'GET /api/orders/reviews/mine': { body: reviewsPage([]) },
      'POST /api/orders/77/review': {
        status: 409,
        body: { code: 'REVIEW_ALREADY_EXISTS', detail: 'Order 77 is already reviewed' },
      },
    });
    const user = userEvent.setup();
    renderApp('/orders');

    await user.click(await screen.findByRole('button', { name: 'Оставить отзыв' }));
    await user.click(
      within(screen.getByRole('radiogroup', { name: 'Оценка ресторана' })).getByRole('radio', {
        name: '4 из 5',
      }),
    );
    await user.type(screen.getByRole('textbox', { name: /Отзыв/ }), 'Нормально');
    await user.click(screen.getByRole('button', { name: 'Отправить' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Вы уже оставили отзыв на этот заказ',
    );
  });
});
