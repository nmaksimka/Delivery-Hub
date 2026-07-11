const emailPattern = /^[^@\s]+@[^@\s]+[.][^@\s]+$/;
const phonePattern = /^[+]?[0-9]{10,15}$/;

export const passwordMinLength = 8;
export const passwordMaxLength = 72;

export function emailError(email: string) {
  if (!email.trim()) {
    return 'Укажите почту';
  }
  return emailPattern.test(email.trim()) ? undefined : 'Похоже, в адресе почты ошибка';
}

export function newPasswordError(password: string) {
  if (password.length < passwordMinLength) {
    return `Минимум ${passwordMinLength} символов`;
  }
  return password.length > passwordMaxLength ? `Максимум ${passwordMaxLength} символа` : undefined;
}

export function normalizePhone(phone: string) {
  return phone.replace(/[\s()-]/g, '');
}

export function phoneError(phone: string) {
  const normalized = normalizePhone(phone);
  if (!normalized) {
    return undefined;
  }
  return phonePattern.test(normalized) ? undefined : 'Номер из 10–15 цифр, например +79131234567';
}
