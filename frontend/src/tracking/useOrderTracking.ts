import { useEffect, useState } from 'react';
import { isFinalStatus } from '../lib/orderStatus';
import { currentAccessToken } from './accessToken';
import { TrackingConnection, type ConnectionState, type TransportFactory } from './connection';
import type { CourierPosition, TrackingMessage } from './message';
import { createStompTransport } from './stompTransport';

export type TrackingSnapshot = {
  connection: ConnectionState;
  latest: TrackingMessage | null;
  courier: CourierPosition | null;
};

const idle: TrackingSnapshot = { connection: 'closed', latest: null, courier: null };

export function useOrderTracking(
  orderId: number,
  enabled: boolean,
  createTransport: TransportFactory = createStompTransport,
): TrackingSnapshot {
  const [snapshot, setSnapshot] = useState<TrackingSnapshot>(idle);

  useEffect(() => {
    if (!enabled || !Number.isInteger(orderId)) {
      return;
    }
    const connection = new TrackingConnection({
      orderId,
      createTransport,
      getAccessToken: currentAccessToken,
      isFinal: (message) => isFinalStatus(message.status),
      onMessage: (message) => {
        setSnapshot((previous) => ({
          ...previous,
          latest: message,
          courier: message.courier ?? previous.courier,
        }));
      },
      onState: (state) => {
        setSnapshot((previous) => ({ ...previous, connection: state }));
      },
    });
    connection.start();
    return () => {
      connection.stop();
    };
  }, [orderId, enabled, createTransport]);

  return enabled ? snapshot : idle;
}
