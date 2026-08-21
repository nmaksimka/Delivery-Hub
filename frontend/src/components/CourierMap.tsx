import L, { type LatLngBoundsExpression, type LatLngTuple } from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useEffect, useRef } from 'react';
import { MapContainer, Marker, TileLayer, useMap } from 'react-leaflet';
import type { Point } from '../tracking/motion';
import { useAnimatedPoint } from '../tracking/useAnimatedPoint';
import styles from './CourierMap.module.css';

export type MapPlace = {
  latitude: number;
  longitude: number;
};

type CourierMapProps = {
  restaurant: MapPlace | null;
  destination: MapPlace | null;
  courier: Point | null;
};

const attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
const tileUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const defaultCenter: LatLngTuple = [56.4846, 84.9476];

const restaurantIcon = L.divIcon({
  className: styles.restaurantPin,
  html: '<span></span>',
  iconSize: [28, 28],
  iconAnchor: [14, 14],
});

const destinationIcon = L.divIcon({
  className: styles.destinationPin,
  html: '<span></span>',
  iconSize: [28, 28],
  iconAnchor: [14, 14],
});

function courierIcon(heading: number) {
  return L.divIcon({
    className: styles.courierPin,
    html: `<span style="transform: rotate(${heading.toFixed(0)}deg)"></span>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
  });
}

function tupleOf(place: MapPlace): LatLngTuple {
  return [place.latitude, place.longitude];
}

function FitToPoints({ points }: { points: LatLngTuple[] }) {
  const map = useMap();
  const latest = useRef(points);
  const signature = points.length;

  useEffect(() => {
    latest.current = points;
  });

  useEffect(() => {
    const known = latest.current;
    if (known.length === 1 && known[0]) {
      map.setView(known[0], 15);
    } else if (known.length > 1) {
      map.fitBounds(known as LatLngBoundsExpression, { padding: [40, 40] });
    }
  }, [map, signature]);

  return null;
}

export default function CourierMap({ restaurant, destination, courier }: CourierMapProps) {
  const animated = useAnimatedPoint(courier);
  const anchors = [restaurant, destination, courier].filter((place) => place !== null).map(tupleOf);

  return (
    <MapContainer
      className={styles.map}
      center={anchors[0] ?? defaultCenter}
      zoom={14}
      scrollWheelZoom={false}
      attributionControl
    >
      <TileLayer attribution={attribution} url={tileUrl} />
      <FitToPoints points={anchors} />
      {restaurant && <Marker position={tupleOf(restaurant)} icon={restaurantIcon} />}
      {destination && <Marker position={tupleOf(destination)} icon={destinationIcon} />}
      {animated && (
        <Marker
          position={[animated.latitude, animated.longitude]}
          icon={courierIcon(animated.heading)}
        />
      )}
    </MapContainer>
  );
}
