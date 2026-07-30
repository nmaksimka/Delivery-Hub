import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { ApiError, errorMessage } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { redirectTarget } from '../auth/redirectTarget';
import { emailError } from '../auth/validation';
import { AuthArt } from '../components/AuthArt';
import { Button } from '../components/Button';
import { Field } from '../components/Field';
import styles from './AuthPage.module.css';

type Errors = {
  email?: string;
  password?: string;
};

export function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to={redirectTarget(location.state)} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found: Errors = {
      email: emailError(email),
      password: password ? undefined : 'Введите пароль',
    };
    setErrors(found);
    setFormError(null);
    if (found.email || found.password) {
      return;
    }

    setSubmitting(true);
    try {
      await login({ email: email.trim(), password });
    } catch (error) {
      const wrongCredentials =
        error instanceof ApiError && [400, 401, 403, 404].includes(error.status);
      setFormError(wrongCredentials ? 'Неверная почта или пароль' : errorMessage(error));
      setSubmitting(false);
    }
  }

  return (
    <section className={styles.page}>
      <div>
        <h1 className={styles.title}>С возвращением</h1>
        <p className={styles.lead}>Войдите, чтобы оформлять заказы и следить за ними.</p>

        <form
          className={styles.form}
          noValidate
          onSubmit={(event) => {
            void submit(event);
          }}
        >
          {formError && (
            <p className={styles.error} role="alert">
              {formError}
            </p>
          )}
          <Field
            label="Почта"
            type="email"
            name="email"
            autoComplete="email"
            inputMode="email"
            value={email}
            error={errors.email}
            onChange={(event) => {
              setEmail(event.target.value);
            }}
          />
          <Field
            label="Пароль"
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            error={errors.password}
            onChange={(event) => {
              setPassword(event.target.value);
            }}
          />
          <Button type="submit" block disabled={submitting}>
            {submitting ? 'Входим…' : 'Войти'}
          </Button>
        </form>

        <p className={styles.switch}>
          Ещё нет аккаунта?{' '}
          <Link to="/register" state={location.state} className={styles.switchLink}>
            Зарегистрироваться
          </Link>
        </p>
      </div>
      <AuthArt />
    </section>
  );
}
