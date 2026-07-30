import { z, type ZodType } from 'zod';

export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

const pageInfo = {
  number: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
};

export function pageOf<T>(item: ZodType<T>) {
  const flat = z.object({ content: z.array(item), ...pageInfo });
  const nested = z.object({ content: z.array(item), page: z.object(pageInfo) });

  return z.union([nested, flat]).transform((value): Page<T> => {
    const info = 'page' in value ? value.page : value;
    return {
      items: value.content,
      page: info.number,
      size: info.size,
      totalElements: info.totalElements,
      totalPages: info.totalPages,
    };
  });
}
