import { z } from 'zod';
import { request } from './http';

const optionalText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

export const addressSchema = z.object({
  id: z.number(),
  label: z.string(),
  city: z.string(),
  street: z.string(),
  house: z.string(),
  apartment: optionalText,
  entrance: optionalText,
  floor: optionalText,
  courierComment: optionalText,
  primary: z.boolean(),
});

export type Address = z.infer<typeof addressSchema>;

export type AddressInput = {
  label: string;
  city: string;
  street: string;
  house: string;
  apartment: string;
  entrance: string;
  floor: string;
  courierComment: string;
};

export const supportedCity = 'Томск';
export const maxAddresses = 10;

export const addressesApi = {
  list(signal?: AbortSignal) {
    return request('/api/users/me/addresses', { schema: z.array(addressSchema), signal });
  },
  create(input: AddressInput) {
    return request('/api/users/me/addresses', {
      method: 'POST',
      body: input,
      schema: addressSchema,
    });
  },
  update(id: number, input: AddressInput) {
    return request(`/api/users/me/addresses/${id}`, {
      method: 'PUT',
      body: input,
      schema: addressSchema,
    });
  },
  remove(id: number) {
    return request(`/api/users/me/addresses/${id}`, {
      method: 'DELETE',
      schema: z.undefined(),
    });
  },
  makePrimary(id: number) {
    return request(`/api/users/me/addresses/${id}/default`, {
      method: 'POST',
      schema: addressSchema,
    });
  },
};
