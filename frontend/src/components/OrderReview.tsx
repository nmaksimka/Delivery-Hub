import { useState, type FormEvent } from 'react';
import type { Order } from '../api/orders';
import { useMyReviews, useSubmitReview } from '../api/queries';
import { reviewErrorMessage } from '../lib/reviewErrors';
import { Button } from './Button';
import { StarIcon } from './icons';
import { StarRating } from './StarRating';
import styles from './OrderReview.module.css';

const maxCommentLength = 2000;

function ReviewForm({ orderId }: { orderId: number }) {
  const submit = useSubmitReview();
  const [open, setOpen] = useState(false);
  const [restaurantRating, setRestaurantRating] = useState<number | null>(null);
  const [courierRating, setCourierRating] = useState<number | null>(null);
  const [comment, setComment] = useState('');

  if (!open) {
    return (
      <Button
        variant="ghost"
        size="small"
        onClick={() => {
          setOpen(true);
        }}
      >
        Оставить отзыв
      </Button>
    );
  }

  const ready = restaurantRating !== null && comment.trim().length > 0;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (restaurantRating === null || !ready) {
      return;
    }
    submit.mutate({
      orderId,
      input: { restaurantRating, courierRating, comment: comment.trim() },
    });
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} aria-label={`Отзыв на заказ №${orderId}`}>
      <div className={styles.row}>
        <span className={styles.caption}>Ресторан</span>
        <StarRating
          label="Оценка ресторана"
          value={restaurantRating}
          onChange={setRestaurantRating}
          disabled={submit.isPending}
        />
      </div>
      <div className={styles.row}>
        <span className={styles.caption}>Курьер</span>
        <StarRating
          label="Оценка курьера"
          value={courierRating}
          onChange={setCourierRating}
          clearable
          disabled={submit.isPending}
        />
        <span className={styles.hint}>необязательно</span>
      </div>
      <label className={styles.commentLabel}>
        <span className={styles.caption}>Отзыв</span>
        <textarea
          className={styles.comment}
          rows={3}
          maxLength={maxCommentLength}
          value={comment}
          placeholder="Как всё прошло: еда, упаковка, скорость"
          disabled={submit.isPending}
          onChange={(event) => {
            setComment(event.target.value);
          }}
        />
      </label>
      {submit.isError && (
        <p className={styles.error} role="alert">
          {reviewErrorMessage(submit.error)}
        </p>
      )}
      <div className={styles.actions}>
        <Button type="submit" size="small" disabled={!ready || submit.isPending}>
          Отправить
        </Button>
        <Button
          variant="ghost"
          size="small"
          disabled={submit.isPending}
          onClick={() => {
            setOpen(false);
          }}
        >
          Отмена
        </Button>
      </div>
    </form>
  );
}

export function OrderReview({ order }: { order: Order }) {
  const reviews = useMyReviews();

  if (order.status !== 'DELIVERED' || !reviews.isSuccess) {
    return null;
  }

  const review = reviews.data.items.find((item) => item.orderId === order.id);
  if (!review) {
    return (
      <div className={styles.root}>
        <ReviewForm orderId={order.id} />
      </div>
    );
  }

  return (
    <div className={styles.root}>
      <p className={styles.given}>
        <StarIcon size={16} className={styles.givenStar} />
        <span>
          Ваша оценка ресторана: {review.restaurantRating}
          {review.courierRating !== null && `, курьера: ${review.courierRating}`}
        </span>
      </p>
      <p className={styles.givenComment}>{review.comment}</p>
    </div>
  );
}
