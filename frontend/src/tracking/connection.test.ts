import { describe, expect, it, vi } from 'vitest';
import {
  TrackingConnection,
  type ConnectionState,
  type Scheduler,
  type Transport,
  type TransportHandlers,
} from './connection';
import type { TrackingMessage } from './message';

class FakeTransport implements Transport {
  handlers: TransportHandlers | null = null;
  closed = false;

  open(handlers: TransportHandlers) {
    this.handlers = handlers;
  }

  close() {
    this.closed = true;
  }
}

class ManualScheduler implements Scheduler {
  delays: number[] = [];
  private tasks: (() => void)[] = [];

  after(delayMs: number, task: () => void) {
    this.delays.push(delayMs);
    this.tasks.push(task);
    return () => {
      this.tasks = this.tasks.filter((candidate) => candidate !== task);
    };
  }

  runNext() {
    this.tasks.shift()?.();
  }

  get pending() {
    return this.tasks.length;
  }
}

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

function statusBody(status: string) {
  return JSON.stringify({ type: 'STATUS', orderId: 5, deliveryId: 1, status });
}

function setup(tokens: (string | null)[] = ['token-1']) {
  const transports: FakeTransport[] = [];
  const created: { accessToken: string; orderId: number }[] = [];
  const states: ConnectionState[] = [];
  const messages: TrackingMessage[] = [];
  const renewRequests: boolean[] = [];
  const scheduler = new ManualScheduler();
  const queue = [...tokens];
  const connection = new TrackingConnection({
    orderId: 5,
    scheduler,
    createTransport: (options) => {
      created.push(options);
      const transport = new FakeTransport();
      transports.push(transport);
      return transport;
    },
    getAccessToken: vi.fn((forceRenew: boolean) => {
      renewRequests.push(forceRenew);
      return Promise.resolve(queue.length > 1 ? (queue.shift() ?? null) : (queue[0] ?? null));
    }),
    isFinal: (message) => message.status === 'DELIVERED' || message.status === 'CANCELLED',
    onMessage: (message) => messages.push(message),
    onState: (state) => states.push(state),
  });
  return { connection, transports, created, states, messages, renewRequests, scheduler };
}

describe('TrackingConnection', () => {
  it('connectsWithAccessTokenAndReportsLiveState', async () => {
    const { connection, transports, created, states } = setup();
    connection.start();
    await flush();
    transports[0]?.handlers?.onConnected();
    expect(created).toEqual([{ accessToken: 'token-1', orderId: 5 }]);
    expect(states).toEqual(['connecting', 'live']);
  });

  it('deliversParsedMessagesAndIgnoresGarbage', async () => {
    const { connection, transports, messages } = setup();
    connection.start();
    await flush();
    transports[0]?.handlers?.onMessage('not json');
    transports[0]?.handlers?.onMessage(statusBody('IN_TRANSIT'));
    expect(messages.map((message) => message.status)).toEqual(['IN_TRANSIT']);
  });

  it('reconnectsWithGrowingBackoffAfterDrops', async () => {
    const { connection, transports, scheduler, states } = setup();
    connection.start();
    await flush();
    transports[0]?.handlers?.onFailure('dropped');
    scheduler.runNext();
    await flush();
    transports[1]?.handlers?.onFailure('dropped');
    scheduler.runNext();
    await flush();
    transports[2]?.handlers?.onFailure('dropped');
    expect(scheduler.delays).toEqual([1000, 2000, 4000]);
    expect(transports).toHaveLength(3);
    expect(states.at(-1)).toBe('reconnecting');
  });

  it('resetsBackoffAfterSuccessfulConnect', async () => {
    const { connection, transports, scheduler } = setup();
    connection.start();
    await flush();
    transports[0]?.handlers?.onFailure('dropped');
    scheduler.runNext();
    await flush();
    transports[1]?.handlers?.onConnected();
    transports[1]?.handlers?.onFailure('dropped');
    expect(scheduler.delays).toEqual([1000, 1000]);
  });

  it('requestsFreshTokenAfterUnauthorizedFailure', async () => {
    const { connection, transports, scheduler, renewRequests, created } = setup([
      'old-token',
      'new-token',
    ]);
    connection.start();
    await flush();
    transports[0]?.handlers?.onFailure('unauthorized');
    scheduler.runNext();
    await flush();
    expect(renewRequests).toEqual([false, true]);
    expect(created.map((options) => options.accessToken)).toEqual(['old-token', 'new-token']);
  });

  it('closesTransportAndStopsAfterFinalStatus', async () => {
    const { connection, transports, scheduler, states } = setup();
    connection.start();
    await flush();
    transports[0]?.handlers?.onMessage(statusBody('DELIVERED'));
    expect(transports[0]?.closed).toBe(true);
    expect(states.at(-1)).toBe('closed');
    expect(scheduler.pending).toBe(0);
  });

  it('cancelsPendingReconnectOnStop', async () => {
    const { connection, transports, scheduler } = setup();
    connection.start();
    await flush();
    transports[0]?.handlers?.onFailure('dropped');
    expect(scheduler.pending).toBe(1);
    connection.stop();
    expect(scheduler.pending).toBe(0);
    expect(transports).toHaveLength(1);
  });

  it('ignoresEventsFromReplacedTransport', async () => {
    const { connection, transports, scheduler, messages } = setup();
    connection.start();
    await flush();
    const stale = transports[0];
    stale?.handlers?.onFailure('dropped');
    scheduler.runNext();
    await flush();
    stale?.handlers?.onMessage(statusBody('IN_TRANSIT'));
    stale?.handlers?.onFailure('dropped');
    expect(messages).toHaveLength(0);
    expect(scheduler.pending).toBe(0);
  });

  it('stopsWithoutConnectingWhenThereIsNoToken', async () => {
    const { connection, transports, states } = setup([null]);
    connection.start();
    await flush();
    expect(transports).toHaveLength(0);
    expect(states.at(-1)).toBe('closed');
  });
});
