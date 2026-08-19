import { reconnectDelay } from './backoff';
import { parseTrackingMessage, type TrackingMessage } from './message';

export type ConnectionState = 'connecting' | 'live' | 'reconnecting' | 'closed';

export type FailureKind = 'unauthorized' | 'dropped';

export type TransportHandlers = {
  onConnected: () => void;
  onMessage: (body: string) => void;
  onFailure: (kind: FailureKind) => void;
};

export type Transport = {
  open: (handlers: TransportHandlers) => void;
  close: () => void;
};

export type TransportFactory = (options: { accessToken: string; orderId: number }) => Transport;

export type Scheduler = {
  after: (delayMs: number, task: () => void) => () => void;
};

export type ConnectionOptions = {
  orderId: number;
  createTransport: TransportFactory;
  getAccessToken: (forceRenew: boolean) => Promise<string | null>;
  isFinal: (message: TrackingMessage) => boolean;
  onMessage: (message: TrackingMessage) => void;
  onState: (state: ConnectionState) => void;
  scheduler?: Scheduler;
};

const timerScheduler: Scheduler = {
  after(delayMs, task) {
    const handle = setTimeout(task, delayMs);
    return () => {
      clearTimeout(handle);
    };
  },
};

export class TrackingConnection {
  private readonly options: ConnectionOptions;
  private readonly scheduler: Scheduler;
  private transport: Transport | null = null;
  private cancelRetry: (() => void) | null = null;
  private attempt = 0;
  private stopped = false;
  private generation = 0;

  constructor(options: ConnectionOptions) {
    this.options = options;
    this.scheduler = options.scheduler ?? timerScheduler;
  }

  start() {
    this.stopped = false;
    this.options.onState('connecting');
    void this.connect(false);
  }

  stop() {
    this.stopped = true;
    this.generation += 1;
    this.cancelRetry?.();
    this.cancelRetry = null;
    this.closeTransport();
    this.options.onState('closed');
  }

  private closeTransport() {
    const transport = this.transport;
    this.transport = null;
    transport?.close();
  }

  private async connect(forceRenew: boolean) {
    const generation = this.generation;
    const accessToken = await this.options.getAccessToken(forceRenew);
    if (this.stopped || generation !== this.generation) {
      return;
    }
    if (!accessToken) {
      this.stop();
      return;
    }
    const transport = this.options.createTransport({
      accessToken,
      orderId: this.options.orderId,
    });
    this.transport = transport;
    transport.open({
      onConnected: () => {
        if (transport !== this.transport) {
          return;
        }
        this.attempt = 0;
        this.options.onState('live');
      },
      onMessage: (body) => {
        if (transport !== this.transport) {
          return;
        }
        const message = parseTrackingMessage(body);
        if (!message) {
          return;
        }
        this.options.onMessage(message);
        if (this.options.isFinal(message)) {
          this.stop();
        }
      },
      onFailure: (kind) => {
        if (transport !== this.transport) {
          return;
        }
        this.closeTransport();
        this.scheduleRetry(kind === 'unauthorized');
      },
    });
  }

  private scheduleRetry(forceRenew: boolean) {
    if (this.stopped) {
      return;
    }
    this.options.onState('reconnecting');
    const delay = reconnectDelay(this.attempt);
    this.attempt += 1;
    this.cancelRetry = this.scheduler.after(delay, () => {
      this.cancelRetry = null;
      void this.connect(forceRenew);
    });
  }
}
