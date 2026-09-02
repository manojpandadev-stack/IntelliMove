import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../../components/Icon';
import RideMap from '../../components/RideMap';
import Avatar from '../../components/Avatar';
import { StatusBadge } from '../../components/ui';
import type { Ride } from '../../api/types';
import {
  useCancelRide,
  useDriverLiveLocation,
  useGetDriver,
  useGetDriverById,
  useGetUser,
  useRideEta,
  useRidePayment,
} from '../../api/hooks';
import { useRideWebSocket } from '../../hooks/useRideWebSocket';

/** Live status card for the rider's active trip, driven by real ride data. */

/**
 * Stepper states mirror the REAL backend ride state machine 1:1 (no invented
 * states). DRIVER_ACCEPTED renders in the "Driver assigned" stage alongside
 * DRIVER_ASSIGNED — it is the same assignment phase from the rider's view.
 */
const STEPS = [
  { label: 'Requested', icon: 'check', statuses: ['REQUESTED', 'MATCHING'] },
  { label: 'Driver assigned', icon: 'car', statuses: ['DRIVER_ASSIGNED', 'DRIVER_ACCEPTED'] },
  { label: 'Arriving', icon: 'navigation', statuses: ['DRIVER_ARRIVING'] },
  { label: 'On trip', icon: 'route', statuses: ['TRIP_STARTED'] },
  { label: 'Completed', icon: 'flag', statuses: ['TRIP_COMPLETED'] },
] as const;

/** Ride states in which the assigned driver's live position is shown. */
const TRACKING_STATUSES = ['DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING', 'TRIP_STARTED'];

function stepIndexOf(status: string): number {
  const idx = STEPS.findIndex((s) => (s.statuses as readonly string[]).includes(status));
  return idx >= 0 ? idx : STEPS.length - 1;
}


export default function ActiveRideCard({ ride }: { ride: Ride }) {
  const cancelRide = useCancelRide();
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [tripElapsedSec, setTripElapsedSec] = useState(0);
  const [wsPosition, setWsPosition] = useState<{ lat: number; lng: number } | null>(null);
  const tripStartRef = useRef<number | null>(null);

  // The assigned driver is identified from the real ride record. Ride.driverId
  // is the driver USER id (Redis GEO member / matching contract); older admin
  // assignments may carry a profile id, so fall back to the by-id lookup only
  // when the user-id lookup returns nothing.
  const { data: driverByUser } = useGetDriver(ride.driverId ?? '');
  const { data: driverByProfileId } = useGetDriverById(
    !driverByUser && ride.driverId ? ride.driverId : '',
  );
  const driver = driverByUser ?? driverByProfileId;
  const { data: driverUser } = useGetUser(driver?.userId ?? '');

      // Cancellation is offered only while the backend still accepts it.
  const canCancel = ['REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED'].includes(ride.status);
  const step = stepIndexOf(ride.status);

  // ─── Real-time driver location ────────────────────────────────────
  const isTracking = ride.driverId != null && TRACKING_STATUSES.includes(ride.status);

  // WebSocket channel: subscribe to THIS ride (authorization: this customer).
  // Only messages for exactly this ride and its assigned driver are accepted.
  const handleLocationMessage = useCallback(
    (data: unknown) => {
      const msg = data as {
        type?: string;
        rideId?: string;
        driverId?: string;
        latitude?: number;
        longitude?: number;
      };
      if (
        msg?.type === 'driver_location_update' &&
        msg.rideId === ride.id &&
        (!ride.driverId || msg.driverId === ride.driverId) &&
        typeof msg.latitude === 'number' &&
        typeof msg.longitude === 'number'
      ) {
        setWsPosition({ lat: msg.latitude, lng: msg.longitude });
      }
    },
    [ride.id, ride.driverId],
  );
  const connection = useRideWebSocket(
    isTracking ? '/location' : null,
    handleLocationMessage,
    isTracking ? { type: 'subscribe_ride', rideId: ride.id } : undefined,
  );
  const wsConnected = connection === 'open';

  // Live-connection indicator: purely reflects the real WebSocket state.
  // REST/GEO polling fallback (below) keeps positions flowing while the
  // socket reconnects, so this is informational and never blocks controls.
  const liveStatus: 'live' | 'reconnecting' | null = !isTracking
    ? null
    : wsConnected
      ? 'live'
      : 'reconnecting';

  // Real payment record (existing GET /api/v1/payments/ride/:id). Undefined
  // until the backend creates one (typically on completion) — never fabricated.
  const { data: payment } = useRidePayment(ride.id);


      // Forget stale WS positions when tracking stops or the ride/driver changes.
  useEffect(() => {
    if (!isTracking) setWsPosition(null);
  }, [isTracking, ride.id, ride.driverId]);

  // Polling fallback (existing REST endpoint backed by Redis GEO): polls
  // continuously while the WebSocket is not open, otherwise just once so a
  // real initial fix appears immediately.
  const { data: polledLocation } = useDriverLiveLocation(
    isTracking ? ride.driverId : null,
    isTracking,
    wsConnected ? false : 5000,
  );

  // WS updates are push-fresh; the polled GEO fix is the fallback baseline.
  const driverPosition =
    wsPosition ??
    (polledLocation && isTracking
      ? { lat: polledLocation.latitude, lng: polledLocation.longitude }
      : null);

  // Approximate live progress once the trip has started: measured from when
  // this browser observed TRIP_STARTED against the pricing engine's duration
  // estimate. Clearly labelled approximate — the rider API does not expose
  // precise vehicle positions, so nothing here is fabricated beyond elapsed
  // wall-clock time.
  useEffect(() => {
    if (ride.status !== 'TRIP_STARTED') {
      tripStartRef.current = null;
      return;
    }
    if (tripStartRef.current == null) tripStartRef.current = Date.now();
    const tick = () =>
      setTripElapsedSec(Math.floor((Date.now() - (tripStartRef.current ?? Date.now())) / 1000));
    tick();
    const t = window.setInterval(tick, 1000);
    return () => window.clearInterval(t);
  }, [ride.status]);

  const progressPct =
    ride.status === 'TRIP_STARTED' && ride.durationMinutes > 0
      ? Math.min(95, Math.round((tripElapsedSec / (ride.durationMinutes * 60)) * 100))
      : null;

    const mmss = (sec: number) =>
    `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;

  // Live pickup ETA: real driver Redis-GEO position + pickup coordinates.
  // Computed in the Location Service from real data when a driver is assigned
  // and heading to pickup; the backend refuses 409 for other ride states, so
  // no ETA is shown outside the pre-trip window. Refreshed opportunistically
  // when a new WebSocket driver-location update lands (no extra polling).
  const { data: eta, refetch: refetchEta } = useRideEta(ride.id);
  useEffect(() => {
    if (isTracking && driverPosition) refetchEta();
  }, [driverPosition, isTracking]);

  return (
    <div className="im-card im-fade-up im-elevate p-5 !rounded-2xl text-[var(--im-text)]" data-testid="active-ride-card">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold">Current trip</h2>
        <div className="flex flex-col items-end gap-1">
          <StatusBadge status={ride.status} />
          {/* Non-blocking live-location indicator: mirrors the real WebSocket
              connection state only. REST/GEO polling keeps positions flowing
              while reconnecting, so ride controls are never blocked. */}
          {liveStatus && (
            <p
              data-testid="live-location-status"
              role="status"
              aria-live="polite"
              className={`flex items-center gap-1.5 text-[0.68rem] font-medium ${
                liveStatus === 'live' ? 'text-emerald-600' : 'text-[var(--im-warning)]'
              }`}
            >
              <span
                aria-hidden="true"
                className={`im-pulse-dot inline-block h-1.5 w-1.5 rounded-full ${
                  liveStatus === 'live' ? 'bg-emerald-500' : 'bg-[var(--im-warning)]'
                }`}
              />
              {liveStatus === 'live' ? 'Live location connected' : 'Live location reconnecting…'}
            </p>
          )}
        </div>
      </div>

      {/* Screen-reader live region: announces real status transitions. */}
      <p role="status" aria-live="polite" className="sr-only">
        {`Trip status: ${STEPS[step].label}`}
      </p>

      {/* Two-panel layout: info left, large live map right (desktop);
          single-column mobile-first stack below lg. */}
      <div className="lg:grid lg:grid-cols-5 lg:gap-6">
      <div className="min-w-0 lg:col-span-3">
      {/* Animated status stepper — mirrors the real backend state machine */}
      <div className="mb-5" aria-label={`Trip status: ${ride.status.replaceAll('_', ' ').toLowerCase()}`}>
        <ol className="im-step">
          {STEPS.map((s, i) => (
            <li
              key={s.label}
              className="flex min-w-0 flex-1 items-center last:flex-none"
              aria-current={i === step ? 'step' : undefined}
            >
              <span
                aria-hidden="true"
                className={`im-step-dot ${i < step ? 'done' : i === step ? 'current' : ''}`}
              >
                {i < step ? <Icon name={s.icon} size={12} strokeWidth={3} /> : i + 1}
              </span>
              <span
                className={`ml-1.5 hidden whitespace-nowrap text-xs font-medium lg:block ${
                  i === step ? 'text-[var(--im-soft)]' : 'text-[var(--im-text-muted)]'
                }`}
              >
                {s.label}
              </span>
              {i < STEPS.length - 1 && (
                <span aria-hidden="true" className={`im-step-line mx-2 ${i < step ? 'done' : ''}`} />
              )}
            </li>
          ))}
        </ol>
        {/* Current-step caption for compact screens where labels collapse */}
        <p className="mt-2 text-xs font-medium text-[var(--im-soft)] lg:hidden">
          Step {step + 1} of {STEPS.length} · {STEPS[step].label}
        </p>
      </div>

      {(ride.status === 'REQUESTED' || ride.status === 'MATCHING') && (
        <div className="im-fade-up mb-4 flex items-center gap-4 rounded-xl border border-[rgb(225_29_104/0.30)] bg-[rgb(225_29_104/0.12)] p-4">
          <span className="relative grid h-12 w-12 place-items-center">
            <span aria-hidden="true" className="im-searching-ring absolute inset-0 rounded-full" style={{ background: '#F43F7F', opacity: 0.35 }} />
            <Icon name="search" size={20} style={{ color: '#F43F7F' }} />
          </span>
          <div>
            <p className="font-semibold text-[var(--im-text)]">Finding your driver…</p>
            <p className="text-sm text-[var(--im-text-secondary)]">We are matching you with a nearby driver.</p>
          </div>
        </div>
      )}

      {driver && ['DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING'].includes(ride.status) && (
        <div className="im-fade-up mb-4 flex items-center gap-4 rounded-xl border border-[var(--im-border)] bg-[var(--im-canvas)]/80 p-4">
          <span className="relative grid h-12 w-12 shrink-0 place-items-center">
            <span aria-hidden="true" className="im-searching-ring absolute inset-0 rounded-full" style={{ background: '#22C55E', opacity: 0.35 }} />
            {/* Real driver profile photo via the existing authenticated Avatar
                component (GET /api/v1/users/:id/photo); falls back to initials
                when the driver has not uploaded one. */}
            <Avatar
              userId={driver.userId}
              firstName={driverUser?.firstName}
              lastName={driverUser?.lastName}
              size={48}
              testId="driver-avatar"
            />
          </span>
          <div className="min-w-0 flex-1">
            <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-600">
              <Icon name="check-circle" size={13} /> Driver on the way
            </p>
                        <p className="truncate font-semibold">
              {driverUser ? `${driverUser.firstName} ${driverUser.lastName}` : `Driver ${ride.driverId?.slice(0, 8)}`}
            </p>
            {/* Real live pickup ETA from the Location Service (haversine + documented
                urban speed assumption). Only shown when the backend returns a value;
                never fabricated. */
            eta ? (
              <p
                aria-live="polite"
                className="mt-1 flex flex-wrap items-center gap-2 text-sm"
              >
                <span className="inline-flex items-center gap-1 font-semibold text-[var(--im-text)]">
                  <Icon name="clock" size={14} /> Driver arriving
                </span>
                <span className="font-mono text-lg font-extrabold text-[var(--im-brand-600)]">
                  ~{eta.etaMinutes} min
                </span>
                <span className="text-[var(--im-text-muted)]">
                  • {eta.distanceKm.toFixed(1)} km away
                </span>
              </p>
            ) : (
              <p className="mt-1 flex items-center gap-1 text-sm text-[var(--im-text-muted)]">
                <Icon name="clock" size={14} /> ETA unavailable
              </p>
            )}
            <p className="flex flex-wrap items-center gap-2 truncate text-sm text-[var(--im-text-muted)]">
              <Icon name="car" size={13} />
              {driver.vehicleColor} {driver.vehicleMake} {driver.vehicleModel}
              <span className="rounded bg-[var(--im-elevated)] px-1.5 py-0.5 font-mono text-xs font-semibold text-[var(--im-text-secondary)]">
                {driver.licensePlate}
              </span>
            </p>
          </div>
          <div className="shrink-0 text-right">
            <span className="inline-flex items-center gap-1 text-sm font-semibold text-amber-500">
              <Icon name="star" size={14} /> {driver.rating.toFixed(1)}
            </span>
            <p className="text-xs text-[var(--im-text-muted)]">{driver.totalTrips} trips</p>
          </div>
        </div>
      )}

      {ride.status === 'TRIP_STARTED' && (
        <div className="im-fade-up mb-4">
          <div className="im-alert-info mb-3 items-center border-[rgb(225_29_104/0.30)] bg-[rgb(225_29_104/0.12)]">
            <Icon name="navigation" size={18} />
            <span>
              You're on your way to <strong>{ride.dropoffAddress ?? 'your destination'}</strong>.
            </span>
          </div>

          {/* Approximate trip progress — elapsed wall clock vs. estimated duration */}
          {progressPct != null && (
            <div className="rounded-xl border border-[var(--im-border)] bg-[var(--im-surface)] p-3">
              <div className="mb-2 flex items-center justify-between text-xs font-medium text-[var(--im-text-muted)]">
                <span>Trip progress (approximate)</span>
                <span className="font-mono">{mmss(tripElapsedSec)} · est. {ride.durationMinutes} min</span>
              </div>
              <div
                className="im-progress im-progress-sheen"
                role="progressbar"
                aria-label="Trip progress"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={progressPct}
              >
                <div className="im-progress-bar" style={{ width: `${progressPct}%` }} />
              </div>
            </div>
          )}
        </div>
      )}

      {/* Route summary — real pickup → destination from the ride record */}
      <div className="mb-3 space-y-1.5 rounded-xl border border-[var(--im-border)] bg-[var(--im-surface)] p-3">
        <p className="flex items-start gap-2.5 text-sm">
          <span aria-hidden="true" className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full ring-4 ring-[rgb(244_63_127/0.18)]" style={{ background: '#F43F7F' }} />
          <span className="min-w-0 flex-1 truncate text-[var(--im-text-secondary)]">{ride.pickupAddress ?? `Pickup (${ride.pickupLatitude.toFixed(4)}, ${ride.pickupLongitude.toFixed(4)})`}</span>
        </p>
        <p aria-hidden="true" className="ml-[4px] h-3 w-px bg-[var(--im-input-border)]" />
        <p className="flex items-start gap-2.5 text-sm">
          <span aria-hidden="true" className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full ring-4 ring-[rgb(225_29_104/0.18)]" style={{ background: '#E11D68' }} />
          <span className="min-w-0 flex-1 truncate font-medium text-[var(--im-text)]">{ride.dropoffAddress ?? 'Destination'}</span>
        </p>
      </div>

      {/* Smoothly expanding trip details */}
      <button
        type="button"
        className="mt-3 flex w-full items-center justify-between rounded-lg px-2 py-1.5 text-sm font-medium text-[var(--im-text-muted)] transition hover:bg-[rgb(244_63_127/0.08)] hover:text-[var(--im-text-secondary)]"
        aria-expanded={detailsOpen}
        aria-controls="trip-details"
        onClick={() => setDetailsOpen((o) => !o)}
      >
        Trip details
        <Icon name="chevron-right" size={15} className={`transition-transform duration-300 ${detailsOpen ? 'rotate-90' : ''}`} />
      </button>
      <div id="trip-details" className={`im-collapse ${detailsOpen ? 'open' : ''}`}>
        <div>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 px-2 pb-1 pt-1.5 text-sm">
            <dt className="text-[var(--im-text-muted)]">Ride type</dt>
            <dd className="text-right font-medium text-[var(--im-text-secondary)]">{ride.rideType}</dd>
            <dt className="text-[var(--im-text-muted)]">Distance</dt>
            <dd className="text-right font-medium text-[var(--im-text-secondary)]">{ride.distanceKm != null ? `${Number(ride.distanceKm).toFixed(1)} km` : '—'}</dd>
            <dt className="text-[var(--im-text-muted)]">Estimated duration</dt>
            <dd className="text-right font-medium text-[var(--im-text-secondary)]">{ride.durationMinutes != null ? `${ride.durationMinutes} min` : '—'}</dd>
            <dt className="text-[var(--im-text-muted)]">Booked</dt>
            <dd className="text-right font-medium text-[var(--im-text-secondary)]">{new Date(ride.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</dd>
            {driver && (
              <>
                <dt className="text-[var(--im-text-muted)]">Vehicle</dt>
                <dd className="text-right font-medium text-[var(--im-text-secondary)]">{driver.vehicleColor} {driver.vehicleMake}</dd>
              </>
            )}
          </dl>
        </div>
      </div>

      {/* Fare & payment — every value shown is real: estimated/final fare come
          from the ride record, payment status from GET /api/v1/payments/ride/:id.
          Each row renders only when the backend actually supplies the value. */}
      <div className="mt-3 rounded-xl border border-[var(--im-border)] bg-[var(--im-surface)] p-3">
        <div className="flex items-baseline justify-between gap-3">
          <span className="text-sm text-[var(--im-text-muted)]">Estimated fare</span>
          <span className="text-base font-extrabold text-[var(--im-text)]">
            {ride.currency ?? 'USD'} {ride.estimatedFare != null ? ride.estimatedFare.toFixed(2) : '—'}
          </span>
        </div>
        {ride.finalFare != null && (
          <div className="mt-1.5 flex items-baseline justify-between gap-3 border-t border-dashed border-[var(--im-border)] pt-1.5">
            <span className="text-sm text-[var(--im-text-muted)]">Final fare</span>
            <span className="text-base font-extrabold text-[var(--im-success)]">
              {ride.currency ?? 'USD'} {ride.finalFare.toFixed(2)}
            </span>
          </div>
        )}
        {payment && (() => {
          const status = String(payment.status ?? '').toUpperCase();
          const label = status ? `${status.charAt(0)}${status.slice(1).toLowerCase()}` : 'Pending';
          const tone =
            status === 'COMPLETED'
              ? 'var(--im-success)'
              : status === 'FAILED' || status === 'REFUNDED'
                ? 'var(--im-danger)'
                : 'var(--im-warning)';
          return (
            <p className="mt-2 flex items-center gap-1.5 text-xs font-medium" style={{ color: tone }}>
              <Icon name="credit-card" size={12} />
              Payment {label}
            </p>
          );
        })()}
      </div>
        {(canCancel || ride.status === 'TRIP_COMPLETED') && (
        <div className="mt-3 flex items-center justify-end gap-3 pt-1">
          {canCancel && (
            <button
              className="im-btn im-btn-danger !min-h-[48px] !px-3 !py-1.5 !text-sm"
              disabled={cancelRide.isPending}
              onClick={() => cancelRide.mutate({ rideId: ride.id, reason: 'Cancelled by rider' })}
              data-testid="cancel-ride"
            >
              {cancelRide.isPending ? 'Cancelling…' : 'Cancel ride'}
            </button>
          )}
          {ride.status === 'TRIP_COMPLETED' && (
            <Link className="im-btn im-btn-primary !min-h-[48px] !px-3 !py-1.5 !text-sm" to={`/rides/${ride.id}`}>
              View receipt
            </Link>
          )}
        </div>
      )}
      {cancelRide.isError && (
        <p className="im-alert-error mt-3" role="alert">Unable to cancel this trip at its current status.</p>
      )}
      </div>{/* /left panel: status · driver · trip · fare · actions */}

      {/* Right panel: large live map (desktop two-panel layout; full-width,
          map-below-info on mobile). Same real WebSocket/GEO position feed. */}
      <div className="min-w-0 lg:col-span-2">
        <RideMap
          height={200}
          enableLocate
          points={[
            { lat: ride.pickupLatitude, lng: ride.pickupLongitude, label: ride.pickupAddress ?? 'Pickup', kind: 'pickup' },
            { lat: ride.dropoffLatitude, lng: ride.dropoffLongitude, label: ride.dropoffAddress ?? 'Dropoff', kind: 'dropoff' },
            // Real assigned-driver position (WebSocket push / Redis GEO fallback)
            ...(isTracking && driverPosition
              ? [{ lat: driverPosition.lat, lng: driverPosition.lng, label: 'Driver', kind: 'driver' as const }]
              : []),
          ]}
        />
      </div>
      </div>{/* /two-panel grid */}
    </div>
  );
}
