export function redirectTarget(state: unknown) {
  if (state && typeof state === 'object' && 'from' in state) {
    const from = state.from;
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) {
      return from;
    }
  }
  return '/';
}
