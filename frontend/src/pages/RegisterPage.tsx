import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { ApiError, errorMessage } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { redirectTarget } from '../auth/redirectTarget';
import {
  emailError,
  newPasswordError,
  normalizePhone,
  passwordMinLength,
  phoneError,
} from '../auth/validation';
import { AuthArt } from '../components/AuthArt';
import { Button } from '../components/Button';
import { Field } from '../components/Field';
import styles from './AuthPage.module.css';

type Errors = {
  name?: string;
  email?: string;
  phone?: string;
  password?: string;
};

export function RegisterPage() {
  const { user, register } = useAuth();
  const location = useLocation();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
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
      name: name.trim() ? undefined : 'Как к вам обращаться?',
      email: emailError(email),
      phone: phoneError(phone),
      password: newPasswordError(password),
    };
    setErrors(found);
    setFormError(null);
    if (Object.values(found).some(Boolean)) {
      return;
    }

    setSubmitting(true);
    try {
      await register({
        name: name.trim(),
        email: email.trim(),
        password,
        phone: normalizePhone(phone) || undefined,
      });
    } catch (error) {
      const taken = error instanceof ApiError && error.status === 409;
      setFormError(taken ? 'Аккаунт с такой почтой уже есть' : errorMessage(error));
      setSubmitting(false);
    }
  }

  return (
    <section className={styles.page}>
      <div>
        <h1 className={styles.title}>Новый аккаунт</h1>
        <p className={styles.lead}>Пара полей — и можно заказывать еду по всему Томску.</p>

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
            label="Имя"
            name="name"
            autoComplete="given-name"
            value={name}
            error={errors.name}
            onChange={(event) => {
              setName(event.target.value);
            }}
          />
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
            label="Телефон"
            type="tel"
            name="phone"
            autoComplete="tel"
            inputMode="tel"
            placeholder="+7"
            hint="Необязательно — пригодится курьеру"
            value={phone}
            error={errors.phone}
            onChange={(event) => {
              setPhone(event.target.value);
            }}
          />
          <Field
            label="Пароль"
            type="password"
            name="password"
            autoComplete="new-password"
            hint={`Не короче ${passwordMinLength} символов`}
            value={password}
            error={errors.password}
            onChange={(event) => {
              setPassword(event.target.value);
            }}
          />
          <Button type="submit" block disabled={submitting}>
            {submitting ? 'Создаём аккаунт…' : 'Зарегистрироваться'}
          </Button>
        </form>

        <p className={styles.switch}>
          Уже есть аккаунт?{' '}
          <Link to="/login" state={location.state} className={styles.switchLink}>
            Войти
          </Link>
        </p>
      </div>
      <AuthArt />
    </section>
  );
}
