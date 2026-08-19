export type Point = {
  latitude: number;
  longitude: number;
  heading: number;
};

export function shortestTurn(from: number, to: number) {
  return ((((to - from) % 360) + 540) % 360) - 180;
}

export function interpolate(from: Point, to: Point, progress: number): Point {
  const t = Math.min(1, Math.max(0, progress));
  return {
    latitude: from.latitude + (to.latitude - from.latitude) * t,
    longitude: from.longitude + (to.longitude - from.longitude) * t,
    heading: (from.heading + shortestTurn(from.heading, to.heading) * t + 360) % 360,
  };
}
