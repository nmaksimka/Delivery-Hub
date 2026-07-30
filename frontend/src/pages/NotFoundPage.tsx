import { Link } from 'react-router';
import { buttonClass } from '../components/buttonClass';
import styles from './NotFoundPage.module.css';

export function NotFoundPage() {
  return (
    <section className={styles.page}>
      <p className={styles.code} aria-hidden="true">
        404
      </p>
      <h1 className={styles.title}>Такой страницы нет</h1>
      <p className={styles.text}>
        Возможно, ссылка устарела или в адресе опечатка. Рестораны по-прежнему ждут на главной.
      </p>
      <Link to="/" className={buttonClass({ variant: 'dark' })}>
        К ресторанам
      </Link>
    </section>
  );
}
