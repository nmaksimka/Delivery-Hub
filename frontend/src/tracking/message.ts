import { z } from 'zod';

const optionalNumber = z
  .number()
  .nullish()
  .transform((value) => value ?? null);

const optionalText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const courierSchema = z.object({
  latitude: z.number(),
  longitude: z.number(),
  heading: z
    .number()
    .nullish()
    .transform((value) => value ?? 0),
  ts: optionalText,
});

const trackingMessageSchema = z.object({
  type: z.enum(['POSITION', 'STATUS']),
  orderId: z.number(),
  deliveryId: z.number(),
  status: z.string(),
  courier: courierSchema.nullish().transform((value) => value ?? null),
  remainingMeters: optionalNumber,
  etaSeconds: optionalNumber,
  estimatedArrival: optionalText,
  at: optionalText,
});

export type TrackingMessage = z.infer<typeof trackingMessageSchema>;
export type CourierPosition = NonNullable<TrackingMessage['courier']>;

export function parseTrackingMessage(body: string): TrackingMessage | null {
  try {
    const parsed = trackingMessageSchema.safeParse(JSON.parse(body));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}
