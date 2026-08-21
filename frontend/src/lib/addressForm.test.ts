import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http';
import {
  addressErrorMessage,
  emptyAddressInput,
  normalizedAddress,
  validateAddress,
} from './addressForm';

const filled = { ...emptyAddressInput, label: 'Дом', street: 'Ленина', house: '12' };

describe('validateAddress', () => {
  it('acceptsFilledRequiredFields', () => {
    expect(validateAddress(filled)).toEqual({});
  });

  it('requiresLabelStreetAndHouse', () => {
    const errors = validateAddress({ ...filled, label: ' ', street: '', house: '' });

    expect(Object.keys(errors).sort()).toEqual(['house', 'label', 'street']);
  });

  it('rejectsCityOtherThanTomsk', () => {
    expect(validateAddress({ ...filled, city: 'Москва' }).city).toMatch(/Томск/);
    expect(validateAddress({ ...filled, city: ' томск ' }).city).toBeUndefined();
  });

  it('rejectsTooLongValues', () => {
    expect(validateAddress({ ...filled, house: '1'.repeat(21) }).house).toBe(
      'Не длиннее 20 символов',
    );
  });
});

describe('normalizedAddress', () => {
  it('trimsFieldsAndFixesCity', () => {
    const result = normalizedAddress({
      ...filled,
      label: ' Дом ',
      city: 'томск',
      apartment: ' 4 ',
    });

    expect(result).toMatchObject({ label: 'Дом', city: 'Томск', apartment: '4' });
  });
});

describe('addressErrorMessage', () => {
  it('explainsAddressLimit', () => {
    const error = new ApiError(409, 'x', null, 'ADDRESS_LIMIT_REACHED');

    expect(addressErrorMessage(error)).toMatch(/10 адресов/);
  });
});
