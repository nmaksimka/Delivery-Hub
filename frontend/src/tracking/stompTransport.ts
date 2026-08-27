import { Client } from '@stomp/stompjs';
import { trackingApi } from '../api/tracking';
import type { FailureKind, TransportFactory } from './connection';

const unauthorizedPattern = /token|auth|forbidden|denied/i;

export function trackingTopic(orderId: number) {
  return `/topic/orders/${orderId}/tracking`;
}

export function trackingUrl(origin: string, ticket: string) {
  const url = new URL('/ws/tracking', origin);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.searchParams.set('ticket', ticket);
  return url.toString();
}

export const createStompTransport: TransportFactory = ({ orderId }) => {
  let client: Client | null = null;
  return {
    open(handlers) {
      let failed = false;
      const fail = (kind: FailureKind) => {
        if (!failed) {
          failed = true;
          handlers.onFailure(kind);
        }
      };
      client = new Client({
        beforeConnect: async (stomp) => {
          try {
            const { ticket } = await trackingApi.ticket();
            stomp.brokerURL = trackingUrl(window.location.origin, ticket);
          } catch {
            fail('unauthorized');
            void stomp.deactivate();
          }
        },
        reconnectDelay: 0,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          client?.subscribe(trackingTopic(orderId), (frame) => {
            handlers.onMessage(frame.body);
          });
          handlers.onConnected();
        },
        onStompError: (frame) => {
          const reason = `${frame.headers.message ?? ''} ${frame.body}`;
          fail(unauthorizedPattern.test(reason) ? 'unauthorized' : 'dropped');
        },
        onWebSocketError: () => {
          fail('dropped');
        },
        onWebSocketClose: () => {
          fail('dropped');
        },
      });
      client.activate();
    },
    close() {
      const current = client;
      client = null;
      if (current) {
        void current.deactivate();
      }
    },
  };
};
