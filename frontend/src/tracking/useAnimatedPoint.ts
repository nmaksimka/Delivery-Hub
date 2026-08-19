import { useEffect, useRef, useState } from 'react';
import { interpolate, type Point } from './motion';

const glideMs = 1500;

export function useAnimatedPoint(target: Point | null): Point | null {
  const [shown, setShown] = useState<Point | null>(null);
  const shownRef = useRef<Point | null>(null);

  useEffect(() => {
    if (!target) {
      shownRef.current = null;
      return;
    }
    const from = shownRef.current ?? target;
    const startedAt = performance.now();
    let frame = 0;
    const step = (now: number) => {
      const progress = (now - startedAt) / glideMs;
      const next = interpolate(from, target, progress);
      shownRef.current = next;
      setShown(next);
      if (progress < 1) {
        frame = requestAnimationFrame(step);
      }
    };
    frame = requestAnimationFrame(step);
    return () => {
      cancelAnimationFrame(frame);
    };
  }, [target]);

  return target ? (shown ?? target) : null;
}
