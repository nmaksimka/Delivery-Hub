import type { ReactNode } from 'react';

type IconProps = {
  size?: number;
  className?: string;
};

type BaseProps = IconProps & {
  filled?: boolean;
  children: ReactNode;
};

function Icon({ size = 20, className, filled = false, children }: BaseProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke={filled ? 'none' : 'currentColor'}
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      {children}
    </svg>
  );
}

export function BagIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M5 8h14l-1.100 10.200a2 2 0 0 1-2 1.800H8.100a2 2 0 0 1-2-1.800L5 8z" />
      <path d="M9 8V6.500a3 3 0 0 1 6 0V8" />
    </Icon>
  );
}

export function SearchIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.600-3.600" />
    </Icon>
  );
}

export function ClockIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.500V12l3 2" />
    </Icon>
  );
}

export function StarIcon(props: IconProps) {
  return (
    <Icon {...props} filled>
      <path d="M12 3.200l2.700 5.500 6.100.900-4.400 4.300 1 6-5.400-2.800-5.400 2.800 1-6-4.400-4.300 6.100-.900L12 3.200z" />
    </Icon>
  );
}

export function PlusIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 5v14M5 12h14" />
    </Icon>
  );
}

export function MinusIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M5 12h14" />
    </Icon>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 6l12 12M18 6L6 18" />
    </Icon>
  );
}

export function PinIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 21s7-6.300 7-11.500a7 7 0 1 0-14 0C5 14.700 12 21 12 21z" />
      <circle cx="12" cy="9.500" r="2.500" />
    </Icon>
  );
}

export function ReceiptIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 3.500h12v17l-3-1.800-3 1.800-3-1.800-3 1.800v-17z" />
      <path d="M9 8.500h6M9 12.500h6" />
    </Icon>
  );
}

export function WalletIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4 7.500a2 2 0 0 1 2-2h11.500v3" />
      <path d="M4 7.500v10a2 2 0 0 0 2 2h13v-11H6a2 2 0 0 1-2-1z" />
      <path d="M15.500 14h1" />
    </Icon>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M5 12.500l4.500 4.500L19 7.500" />
    </Icon>
  );
}

export function LogoutIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M14 4.500h3.500a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H14" />
      <path d="M10 16l4-4-4-4M14 12H4.500" />
    </Icon>
  );
}

export function UserIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="8.500" r="3.500" />
      <path d="M5 20a7 7 0 0 1 14 0" />
    </Icon>
  );
}

export function ArrowLeftIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M19 12H5M11 6l-6 6 6 6" />
    </Icon>
  );
}
