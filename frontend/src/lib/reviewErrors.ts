import { ApiError, errorMessage } from '../api/http';

export function reviewErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'ORDER_NOT_DELIVERED':
        return 'Оценить можно только доставленный заказ';
      case 'REVIEW_ALREADY_EXISTS':
        return 'Вы уже оставили отзыв на этот заказ';
      case 'COURIER_NOT_ASSIGNED':
        return 'У этого заказа нет курьера, оценить можно только ресторан';
      case 'VALIDATION_FAILED':
        return 'Поставьте оценку от 1 до 5 и напишите пару слов';
      case 'FORBIDDEN':
        return 'Оставить отзыв может только автор заказа';
      default:
        break;
    }
  }
  return errorMessage(error);
}
