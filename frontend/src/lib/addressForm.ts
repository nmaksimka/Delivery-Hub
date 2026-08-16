import { supportedCity, type Address, type AddressInput } from '../api/addresses';
import { ApiError, errorMessage } from '../api/http';

export type AddressErrors = Partial<Record<keyof AddressInput, string>>;

export const emptyAddressInput: AddressInput = {
  label: '',
  city: supportedCity,
  street: '',
  house: '',
  apartment: '',
  entrance: '',
  floor: '',
  courierComment: '',
};

export function inputFromAddress(address: Address): AddressInput {
  return {
    label: address.label,
    city: address.city,
    street: address.street,
    house: address.house,
    apartment: address.apartment ?? '',
    entrance: address.entrance ?? '',
    floor: address.floor ?? '',
    courierComment: address.courierComment ?? '',
  };
}

const limits: Record<keyof AddressInput, number> = {
  label: 40,
  city: 80,
  street: 160,
  house: 20,
  apartment: 20,
  entrance: 20,
  floor: 20,
  courierComment: 300,
};

const required: Partial<Record<keyof AddressInput, string>> = {
  label: 'Назовите адрес, например «Дом»',
  street: 'Укажите улицу',
  house: 'Укажите номер дома',
};

export function validateAddress(input: AddressInput): AddressErrors {
  const errors: AddressErrors = {};
  (Object.keys(limits) as (keyof AddressInput)[]).forEach((field) => {
    const value = input[field].trim();
    if (!value && required[field]) {
      errors[field] = required[field];
    } else if (value.length > limits[field]) {
      errors[field] = `Не длиннее ${limits[field]} символов`;
    }
  });
  if (input.city.trim().toLowerCase() !== supportedCity.toLowerCase()) {
    errors.city = `Пока доставляем только по городу ${supportedCity}`;
  }
  return errors;
}

export function normalizedAddress(input: AddressInput): AddressInput {
  return {
    label: input.label.trim(),
    city: supportedCity,
    street: input.street.trim(),
    house: input.house.trim(),
    apartment: input.apartment.trim(),
    entrance: input.entrance.trim(),
    floor: input.floor.trim(),
    courierComment: input.courierComment.trim(),
  };
}

export function addressErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'ADDRESS_LIMIT_REACHED':
        return 'Можно сохранить не больше 10 адресов — удалите ненужный';
      case 'ADDRESS_NOT_FOUND':
        return 'Этого адреса уже нет, обновите страницу';
      case 'ADDRESS_NOT_FOUND_ON_MAP':
        return 'Не нашли такой дом на карте Томска — проверьте улицу и номер дома';
      case 'VALIDATION_FAILED':
        return 'Проверьте поля адреса: доставляем только по Томску';
      default:
        break;
    }
  }
  return errorMessage(error);
}

export function addressLine(address: Address) {
  const parts = [`${address.street}, ${address.house}`];
  if (address.apartment) {
    parts.push(`кв. ${address.apartment}`);
  }
  return parts.join(', ');
}
