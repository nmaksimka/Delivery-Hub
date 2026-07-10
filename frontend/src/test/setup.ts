import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';
import { sessionStore } from '../auth/sessionStore';

Object.defineProperty(window, 'scrollTo', { value: () => undefined, writable: true });
Object.defineProperty(Element.prototype, 'scrollIntoView', {
  value: () => undefined,
  writable: true,
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessionStore.clear();
  localStorage.clear();
});
