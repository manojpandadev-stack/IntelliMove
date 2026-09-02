import type { CSSProperties } from 'react';

/**
 * IntelliMove inline SVG icon set (original, open-license-free).
 * Icons are stroke-based 24x24 glyphs rendered via currentColor.
 */
const PATHS: Record<string, string> = {
  'map-pin': 'M12 21s-7-5.1-7-11a7 7 0 1 1 14 0c0 5.9-7 11-7 11Z M12 10m-2.5 0a2.5 2.5 0 1 0 5 0 2.5 2.5 0 1 0 -5 0',
  flag: 'M5 3v18 M5 4h12l-2.5 4L17 12H5',
  car: 'M5 16l1.2-4.8A2 2 0 0 1 8.1 9.6h7.8a2 2 0 0 1 1.9 1.6L19 16 M4 16h16v3h-2.5 M4 16v3h2.5 M7.5 19a1.5 1.5 0 1 0 3 0 M13.5 19a1.5 1.5 0 1 0 3 0 M6 12h12',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5',
  users: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z M2.5 20c1.2-3 3.8-4.5 6.5-4.5s5.3 1.5 6.5 4.5 M16 4.5a3.5 3.5 0 0 1 0 7 M17.5 15.7c1.9.5 3.3 1.8 4 4.3',
  wallet: 'M3 7.5A2.5 2.5 0 0 1 5.5 5H18a1 1 0 0 1 1 1v2 M3 7.5V17a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2H5.5A2.5 2.5 0 0 1 3 7.5Z M16 13.5h.01',
  bell: 'M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6 M10 19a2 2 0 0 0 4 0',
  home: 'M4 11l8-7 8 7 M6 9.5V20h4.5v-5h3v5H18V9.5',
  help: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M9.5 9.3A2.6 2.6 0 0 1 12 7.5c1.4 0 2.5 1 2.5 2.3 0 1.7-2.5 2-2.5 3.7 M12 17.2h.01',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3 M10 8l-4 4 4 4 M6 12h10',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  x: 'M6 6l12 12 M18 6L6 18',
  star: 'M12 3l2.7 5.6 6.1.8-4.5 4.2 1.1 6-5.4-3-5.4 3 1.1-6L3.2 9.4l6.1-.8L12 3Z',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M12 7v5l3.5 2',
  'chevron-right': 'M9 6l6 6-6 6',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  'check-circle': 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M8 12.5l2.8 2.8L16.5 9.5',
  alert: 'M12 9v4 M12 16.5h.01 M10.3 3.9 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14Z M16 16l5 5',
  shield: 'M12 3l8 3v6c0 4.5-3 8.3-8 9.5C7 20.3 4 16.5 4 12V6l8-3Z M9 12l2.2 2.2L15.5 10',
  'credit-card': 'M3 10h18 M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z M6.5 15h3',
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  navigation: 'M12 3l7 18-7-4-7 4 7-18Z',
  phone: 'M5 4h4l1.5 4.5L8 10a12 12 0 0 0 6 6l1.5-2.5L20 15v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z',
  message: 'M21 12a8 8 0 0 1-8 8H4l2-3.5A8 8 0 1 1 21 12Z',
  sparkles: 'M12 4l1.7 4.6L18 10l-4.3 1.4L12 16l-1.7-4.6L6 10l4.3-1.4L12 4Z M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z',
  route: 'M6.5 8a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z M17.5 21a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z M6.5 8v6a4 4 0 0 0 4 4h7',
  trend: 'M3 17l6-6 4 4 8-8 M15 7h6v6',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z M12 12m-3 0a3 3 0 1 0 6 0 3 3 0 1 0 -6 0',
};

export type IconName = keyof typeof PATHS;

interface IconProps {
  name: IconName;
  size?: number;
  className?: string;
  style?: CSSProperties;
  strokeWidth?: number;
}

export default function Icon({ name, size = 20, className = '', style, strokeWidth = 1.8 }: IconProps) {
  const d = PATHS[name];
  if (!d) return null;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
      style={style}
    >
      {d.split(' M').map((seg, i) => (
        <path key={i} d={i === 0 ? seg : `M${seg}`} />
      ))}
    </svg>
  );
}
