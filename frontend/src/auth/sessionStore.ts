import { z } from 'zod';

const storageKey = 'deliveryhub.session';

const sessionSchema = z.object({
  token: z.string().min(1),
  user: z.object({
    id: z.number(),
    email: z.string(),
    name: z.string(),
    role: z.string(),
  }),
});

export type Session = z.infer<typeof sessionSchema>;
export type SessionUser = Session['user'];

function readStored(): Session | null {
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) {
      return null;
    }
    const parsed = sessionSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

function persist(session: Session | null) {
  try {
    if (session) {
      localStorage.setItem(storageKey, JSON.stringify(session));
    } else {
      localStorage.removeItem(storageKey);
    }
  } catch {
    return;
  }
}

let current: Session | null = readStored();
const listeners = new Set<() => void>();

function update(session: Session | null) {
  current = session;
  persist(session);
  listeners.forEach((listener) => {
    listener();
  });
}

export const sessionStore = {
  get(): Session | null {
    return current;
  },
  set(session: Session) {
    update(session);
  },
  clear() {
    if (current) {
      update(null);
    }
  },
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
};
