import { useEffect, useState } from 'react';
import Icon from './Icon';

export interface MapPoint {
  lat: number;
  lng: number;
  label: string;
  kind: 'pickup' | 'dropoff' | 'driver' | 'current';
}

const KIND_STYLE: Record<MapPoint['kind'], { color: string; letter: string }> = {
  pickup: { color: '#E11D68', letter: 'A' },
  dropoff: { color: '#BE185D', letter: 'B' },
  driver: { color: '#16A34A', letter: 'D' },
  current: { color: '#FB7185', letter: '' },
};

/**
 * Lightweight live-ride map built from real coordinates only.
 *
 * Renders an SVG "map canvas": graticule + stylized street grid backdrop,
 * a route line between the actual points, and labeled markers for
 * pickup / dropoff / driver / current location. All positions come from real
 * backend coordinates (equirectangular projection) — nothing is fabricated.
 * The optional locate-me control uses the browser Geolocation API (real
 * device position). Markers animate smoothly when coordinates change, a
 * skeleton shimmer shows while loading, and an empty state shows when there
 * is nothing to plot yet.
 */
export default function RideMap({
  points,
  height = 260,
  loading = false,
  enableLocate = false,
}: {
  points: MapPoint[];
  height?: number;
  /** Skeleton shimmer overlay while data is loading. */
  loading?: boolean;
  /** Adds a "center on my location" control using the browser Geolocation API. */
  enableLocate?: boolean;
}) {
  const W = 640;
  const H = 320;
  const [mePoint, setMePoint] = useState<MapPoint | null>(null);
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState(false);
  /** Viewport zoom around the plotted points' centroid (1 = fit all). */
  const [zoom, setZoom] = useState(1);

  const allPoints: MapPoint[] = mePoint ? [...points, mePoint] : points;

  const lats = allPoints.map((p) => p.lat);
  const lngs = allPoints.map((p) => p.lng);
  const pad = Math.max(0.008, Math.max(...lngs, 0) - Math.min(...lngs, 0), Math.max(...lats, 0) - Math.min(...lats, 0)) * 0.35 + 0.004;
  const minLat = Math.min(...lats) - pad;
  const maxLat = Math.max(...lats) + pad;
  const minLng = Math.min(...lngs) - pad;
  const maxLng = Math.max(...lngs) + pad;
  // Zoom shrinks the visible span around the centroid of the real points.
  const cLat = (minLat + maxLat) / 2;
  const cLng = (minLng + maxLng) / 2;
  const spanLat = Math.max((maxLat - minLat) / zoom, 1e-6);
  const spanLng = Math.max((maxLng - minLng) / zoom, 1e-6);
  const viewMinLat = cLat - spanLat / 2;
  const viewMinLng = cLng - spanLng / 2;

  const project = (lat: number, lng: number) => ({
    x: ((lng - viewMinLng) / spanLng) * (W - 80) + 40,
    y: H - (((lat - viewMinLat) / spanLat) * (H - 80) + 40),
  });

  const zoomIn = () => setZoom((z) => Math.min(4, +(z * 1.4).toFixed(2)));
  const zoomOut = () => setZoom((z) => Math.max(1, +(z / 1.4).toFixed(2)));
  /** Re-fit the route: clear device position and any zoom. */
  const resetRoute = () => {
    setZoom(1);
    setMePoint(null);
  };

  // Drop the device position when this map instance no longer has ride points
  // (e.g. trip finished and card unmounted its booking state).
  useEffect(() => {
    if (!mePoint && points.length === 0) return;
    if (points.length === 0) setMePoint(null);
  }, [points.length, mePoint]);

  const projected = allPoints.map((p) => ({ ...p, ...project(p.lat, p.lng) }));
  const routePoints = projected.filter((p) => p.kind !== 'current');
  const routeD =
    routePoints.length >= 2
      ? routePoints.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
      : '';

  const locate = () => {
    if (!('geolocation' in navigator)) {
      setLocateError(true);
      return;
    }
    setLocating(true);
    setLocateError(false);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        setMePoint({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          label: 'Your location',
          kind: 'current',
        });
      },
      () => {
        setLocating(false);
        setLocateError(true);
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30_000 },
    );
  };


  return (
    <div className="relative overflow-hidden rounded-xl border" style={{ height, borderColor: 'var(--im-border)' }} role="img" aria-label="Ride map">
      <svg viewBox={`0 0 ${W} ${H}`} className="h-full w-full" preserveAspectRatio="xMidYMid slice" style={{ background: 'var(--im-input-bg)' }}>
        {/* Stylized city grid */}
        <g stroke="rgba(58, 29, 43, 0.7)" strokeWidth="1">
          {Array.from({ length: 9 }).map((_, i) => (
            <line key={`v${i}`} x1={(W / 9) * (i + 0.5)} y1={0} x2={(W / 9) * (i + 0.5)} y2={H} />
          ))}
          {Array.from({ length: 6 }).map((_, i) => (
            <line key={`h${i}`} x1={0} y1={(H / 6) * (i + 0.5)} x2={W} y2={(H / 6) * (i + 0.5)} />
          ))}
        </g>
        <g stroke="#2B1521" strokeWidth="6" strokeLinecap="round">
          <path d={`M0 ${H * 0.28} Q ${W * 0.35} ${H * 0.2} ${W} ${H * 0.34}`} fill="none" />
          <path d={`M${W * 0.22} 0 Q ${W * 0.32} ${H * 0.55} ${W * 0.24} ${H}`} fill="none" />
          <path d={`M0 ${H * 0.72} L ${W} ${H * 0.66}`} fill="none" />
        </g>

        {/* Route between real points */}
        {routeD && (
          <path d={routeD} fill="none" stroke="var(--im-brand-600)" strokeWidth="3.5" strokeDasharray="7 6" strokeLinecap="round" opacity="0.85" />
        )}

        {/* Markers — driver & current-location markers glide smoothly between
            real coordinate updates; others drop in on appearance */}
        {projected.map((p, i) => {
          const st = KIND_STYLE[p.kind];
          const key = `${p.kind}-${i}`;
          const glides = p.kind === 'current' || p.kind === 'driver';
          return (
            <g
              key={key}
              className={glides ? 'im-marker-move' : 'im-marker-drop'}
              style={{ transform: `translate(${p.x}px, ${p.y}px)` }}
              data-testid={`map-marker-${p.kind}`}
              data-coords={`${p.lat},${p.lng}`}
            >
              {(p.kind === 'driver' || p.kind === 'current') && (
                <circle r="16" fill={st.color} opacity="0.25" className="im-searching-ring" />
              )}
              {p.kind === 'current' ? (
                <>
                  <circle r="9" fill={st.color} stroke="#ffffff" strokeWidth="2.5" className="im-pulse-dot" />
                  <circle r="3.5" fill="#ffffff" />
                </>
              ) : p.kind === 'driver' ? (
                <>
                  {/* Clear car glyph for the live driver — coordinates stay real. */}
                  <circle r="14" fill="#ffffff" stroke={st.color} strokeWidth="2.5" />
                  <g transform="translate(-8.28,-9.72) scale(0.72)" fill="none" stroke={st.color} strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M5 16l1.2-4.8A2 2 0 0 1 8.1 9.6h7.8a2 2 0 0 1 1.9 1.6L19 16 M4 16h16v3h-2.5 M4 16v3h2.5 M7.5 19a1.5 1.5 0 1 0 3 0 M13.5 19a1.5 1.5 0 1 0 3 0 M6 12h12" />
                  </g>
                </>
              ) : (
                <>
                  <circle r="13" fill="#ffffff" stroke={st.color} strokeWidth="2.5" />
                  <text x="0" y="4.5" textAnchor="middle" fontSize="12" fill={st.color}>
                    {st.letter}
                  </text>
                </>
              )}
            </g>
          );
        })}
      </svg>

      {/* Skeleton shimmer while loading */}
      {loading && (
        <div className="absolute inset-0 grid place-items-center bg-[rgb(15_8_13/0.68)]" aria-hidden="true">
          <div className="w-56 space-y-3 px-4">
            <div className="im-skeleton mx-auto h-4 w-3/4" />
            <div className="im-skeleton h-24 w-full" />
            <div className="im-skeleton mx-auto h-3 w-1/2" />
          </div>
        </div>
      )}
      {!loading && projected.length === 0 && (
        <div className="absolute inset-0 grid place-items-center p-6 text-center text-sm text-[var(--im-text-muted)]">
          <span>
            <Icon name="route" size={26} className="mx-auto mb-2" />
            Your route appears here once pickup and destination are set.
          </span>
        </div>
      )}

      {/* Legend */}
      {projected.length > 0 && (
        <div className="absolute bottom-2 left-2 flex flex-wrap gap-2 rounded-lg border border-[var(--im-border)] bg-[rgb(26_13_20/0.92)] px-2.5 py-1.5 text-[11px] text-[var(--im-text-secondary)] font-medium shadow-sm">
          {projected.map((p, i) => (
            <span key={`${p.kind}-${i}-legend`} className="inline-flex items-center gap-1">
              <span aria-hidden="true" className="h-2 w-2 rounded-full" style={{ background: KIND_STYLE[p.kind].color }} />
              {p.label}
            </span>
          ))}
        </div>
      )}

      {/* Map controls (real browser geolocation only) */}
      {enableLocate && (
        <div className="absolute right-2 top-2 flex flex-col gap-1.5">
          <button
            type="button"
            onClick={locate}
            disabled={locating}
            aria-label="Center map on my current location"
            title="Center on my location"
            className="grid h-8 w-8 place-items-center rounded-lg border border-[var(--im-border)] bg-[var(--im-elevated)] shadow-sm text-[var(--im-text-muted)] transition hover:text-[var(--im-text)] hover:shadow"
          >
            <Icon name="navigation" size={15} className={locating ? 'im-spin' : ''} />
          </button>
          <button
            type="button"
            onClick={zoomIn}
            disabled={zoom >= 4}
            aria-label="Zoom in"
            title="Zoom in"
            className="grid h-8 w-8 place-items-center rounded-lg border border-[var(--im-border)] bg-[var(--im-elevated)] shadow-sm text-[var(--im-text-muted)] transition hover:text-[var(--im-text)] hover:shadow disabled:opacity-40"
          >
            <span aria-hidden="true" className="text-base leading-none font-bold">+</span>
          </button>
          <button
            type="button"
            onClick={zoomOut}
            disabled={zoom <= 1}
            aria-label="Zoom out"
            title="Zoom out"
            className="grid h-8 w-8 place-items-center rounded-lg border border-[var(--im-border)] bg-[var(--im-elevated)] shadow-sm text-[var(--im-text-muted)] transition hover:text-[var(--im-text)] hover:shadow disabled:opacity-40"
          >
            <span aria-hidden="true" className="text-base leading-none font-bold">−</span>
          </button>
          <button
            type="button"
            onClick={resetRoute}
            disabled={zoom === 1 && !mePoint}
            aria-label="Reset route view"
            title="Reset route view"
            className="grid h-8 w-8 place-items-center rounded-lg border border-[var(--im-border)] bg-[var(--im-elevated)] shadow-sm text-[var(--im-text-muted)] transition hover:text-[var(--im-text)] hover:shadow disabled:opacity-40"
          >
            <Icon name="route" size={15} />
          </button>
        </div>
      )}
      {!enableLocate && zoom > 1 && (
        <button
          type="button"
          onClick={resetRoute}
          aria-label="Reset route view"
          className="absolute right-2 top-2 grid h-8 w-8 place-items-center rounded-lg border border-[var(--im-border)] bg-[var(--im-elevated)] shadow-sm text-[var(--im-text-muted)] transition hover:text-[var(--im-text)]"
        >
          <Icon name="route" size={15} />
        </button>
      )}
      {locateError && (
        <span role="status" className="absolute right-2 top-[168px] max-w-[120px] rounded-lg border border-[var(--im-border)] bg-[var(--im-elevated)] px-2 py-1 text-[11px] text-[var(--im-text-muted)] shadow-sm">
          Location unavailable
        </span>
      )}
    </div>
  );
}

