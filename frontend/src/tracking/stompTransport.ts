import { Client } from '@stomp/stompjs';
import type { FailureKind, TransportFactory } from './connection';

const unauthorizedPattern = /token|auth|forbidden|denied/i;

export function trackingTopic(orderId: number) {
  return `/topic/orders/${orderId}/tracking`;
}

export function trackingUrl(origin: string, accessToken: string) {
  const url = new URL('/ws/tracking', origin);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.searchParams.set('access_token', accessToken);
  return url.toString();
}

export const createStompTransport: TransportFactory = ({ accessToken, orderId }) => {
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
        brokerURL: trackingUrl(window.location.origin, accessToken),
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
