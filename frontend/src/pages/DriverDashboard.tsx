import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useQueryClient } from '@tanstack/react-query';
import {
  useGetDriver,
  useUpdateDriverStatus,
  useUpdateDriverLocation,
  useGetDriverRides,
  useDriverRideAction,
  useRejectRide,
  useRideEta,
  useGetUser,
} from '../api/hooks';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';
import { StatusBadge } from '../components/ui';
import type { Ride } from '../api/types';
import RideMap from '../components/RideMap';
import { useRideWebSocket } from '../hooks/useRideWebSocket';

const STATUS_STYLES: Record<string, { bg: string; fg: string }> = {
  ONLINE: { bg: 'rgba(34, 197, 94, 0.16)', fg: '#86EFAC' },
  AVAILABLE: { bg: 'rgba(34, 197, 94, 0.16)', fg: '#86EFAC' },
  ON_TRIP: { bg: 'rgba(225, 29, 104, 0.20)', fg: '#FB7185' },
  SUSPENDED: { bg: 'rgba(239, 68, 68, 0.16)', fg: '#FCA5A5' },
  OFFLINE: { bg: 'rgba(217, 168, 183, 0.12)', fg: '#D1A8B7' },
};

const ACTIVE_RIDE_STATUSES = ['DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING', 'TRIP_STARTED'];

/**
 * Client-side acceptance window for an incoming request, in seconds.
 * PURELY visual behaviour: the backend state machine stays the single
 * authority (a late accept is still rejected server-side; there is no
 * fabricated backend expiration timer).
 */
const REQUEST_WINDOW_SECONDS = 45;

/** Backend-provided value or an explicit unavailable state — nothing fabricated. */
function orUnavailable(value: string | null | undefined): string {
  return value != null && value !== '' ? value : 'Unavailable';
}

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

/**
 * Countdown anchored to the backend-provided assignment timestamp
 * (falls back to panel mount). Ticks once per second via role="timer"
 * with aria-live off so screen readers are not spammed every second.
 */
function useAcceptanceCountdown(assignedAt: string | undefined, rideId: string): number {
  const [secondsLeft, setSecondsLeft] = useState(() => {
    if (!assignedAt) return REQUEST_WINDOW_SECONDS;
    const elapsed = Math.floor((Date.now() - new Date(assignedAt).getTime()) / 1000);
    return Math.max(0, REQUEST_WINDOW_SECONDS - elapsed);
  });

  useEffect(() => {
    if (!assignedAt) return;
    const start = new Date(assignedAt).getTime();
    const tick = () => {
      const elapsed = Math.floor((Date.now() - start) / 1000);
      setSecondsLeft(Math.max(0, REQUEST_WINDOW_SECONDS - elapsed));
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
    // rideId re-anchors the timer when a different request arrives
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assignedAt, rideId]);

  return secondsLeft;
}

/**
 * Uber-style incoming ride request panel. Shows ONLY backend-provided data:
 * ride category, addresses, estimated fare, trip distance/duration (ride
 * record), pickup ETA/distance (Location Service ETA endpoint) and customer
 * name when available — otherwise an explicit "Unavailable" state.
 */
function IncomingRequestPanel({ ride }: { ride: Ride }) {
  const queryClient = useQueryClient();
  const acceptAction = useDriverRideAction();
  const rejectAction = useRejectRide();
  // Real pickup ETA + distance from the driver's live Redis-GEO position.
  const { data: eta } = useRideEta(ride.id);
  // Real customer record from the user service (may be unavailable).
  const { data: customerUser } = useGetUser(ride.customerId);

  const secondsLeft = useAcceptanceCountdown(ride.driverAssignedAt, ride.id);
  const expired = secondsLeft <= 0;

  // Double-acceptance guards: rapid clicking, duplicate WS events and
  // multiple browser events all funnel through these refs + isPending.
  const acceptingRef = useRef(false);
  const rejectingRef = useRef(false);

  // When the countdown hits zero, refresh the driver's request state once.
  const expiredRefreshedRef = useRef(false);
  useEffect(() => {
    if (expired && !expiredRefreshedRef.current) {
      expiredRefreshedRef.current = true;
      queryClient.invalidateQueries({ queryKey: ['driverRides'] });
    }
  }, [expired, queryClient]);

  const accept = () => {
    if (expired || acceptingRef.current || acceptAction.isPending) return;
    acceptingRef.current = true;
    acceptAction.mutate(
      { rideId: ride.id, action: 'accept' },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: ['driverRides'] });
          queryClient.invalidateQueries({ queryKey: ['allRides'] });
        },
        onSettled: () => {
          acceptingRef.current = false;
        },
      },
    );
  };

  const reject = () => {
    if (rejectingRef.current || rejectAction.isPending) return;
    rejectingRef.current = true;
    rejectAction.mutate(
      { rideId: ride.id },
      {
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['driverRides'] }),
        onSettled: () => {
          rejectingRef.current = false;
        },
      },
    );
  };

  const customerName = customerUser
    ? `${customerUser.firstName} ${customerUser.lastName}`.trim()
    : null;

  return (
    <div data-testid="driver-request-card">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-sm font-semibold" style={{ color: '#FDA4AF' }}>
          <Icon name="bell" size={16} /> New ride request — respond now
        </p>
        {expired ? (
          <span
            className="im-badge"
            data-testid="request-expired"
            style={{ background: 'rgba(217, 168, 183, 0.12)', color: '#D1A8B7' }}
          >
            <Icon name="alert" size={12} /> Request expired
          </span>
        ) : (
          <span
            className="im-badge font-mono"
            data-testid="request-countdown"
            role="timer"
            aria-live="off"
            aria-label={`Time left to accept: ${formatCountdown(secondsLeft)}`}
            style={{ background: 'rgba(225, 29, 104, 0.16)', color: '#FB7185' }}
          >
            <Icon name="clock" size={12} /> {formatCountdown(secondsLeft)}
          </span>
        )}
      </div>
      {/* PANEL_FIELDS */}
      <dl className="mb-4 grid gap-3 sm:grid-cols-2">
        <div className="rounded-xl p-3" style={{ background: 'rgba(225, 29, 104, 0.10)' }}>
          <dt className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FB7185' }}>Ride category</dt>
          <dd className="text-sm font-medium capitalize text-[var(--im-text)]">
            {orUnavailable(ride.rideType?.toLowerCase())}
          </dd>
        </div>
        <div className="rounded-xl p-3" style={{ background: 'rgba(251, 113, 133, 0.10)' }}>
          <dt className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FDA4AF' }}>Estimated fare</dt>
          <dd className="text-sm font-medium text-[var(--im-text)]">
            {ride.estimatedFare != null ? `${ride.currency ?? 'USD'} ${ride.estimatedFare.toFixed(2)}` : 'Unavailable'}
          </dd>
        </div>
        <div className="rounded-xl p-3" style={{ background: 'rgba(225, 29, 104, 0.10)' }}>
          <dt className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FB7185' }}>Pickup</dt>
          <dd className="text-sm font-medium text-[var(--im-text)]">{orUnavailable(ride.pickupAddress)}</dd>
        </div>
        <div className="rounded-xl p-3" style={{ background: 'rgba(251, 113, 133, 0.10)' }}>
          <dt className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FDA4AF' }}>Drop-off</dt>
          <dd className="text-sm font-medium text-[var(--im-text)]">{orUnavailable(ride.dropoffAddress)}</dd>
        </div>
        <div className="rounded-xl p-3" style={{ background: 'rgba(225, 29, 104, 0.10)' }}>
          <dt className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FB7185' }}>Pickup ETA / distance</dt>
          <dd className="text-sm font-medium text-[var(--im-text)]" data-testid="request-pickup-eta">
            {eta ? `${eta.etaMinutes} min · ${eta.distanceKm} km` : 'Unavailable'}
          </dd>
        </div>
        <div className="rounded-xl p-3" style={{ background: 'rgba(251, 113, 133, 0.10)' }}>
          <dt className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FDA4AF' }}>Trip distance / duration</dt>
          <dd className="text-sm font-medium text-[var(--im-text)]">
            {ride.distanceKm > 0 || ride.durationMinutes > 0
              ? `${ride.distanceKm > 0 ? `${ride.distanceKm} km` : 'Distance unavailable'}${
                  ride.durationMinutes > 0 ? ` · ~${ride.durationMinutes} min` : ''
                }`
              : 'Unavailable'}
          </dd>
        </div>
        <div className="rounded-xl p-3 sm:col-span-2" style={{ background: 'rgba(225, 29, 104, 0.06)' }}>
          <dt className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FB7185' }}>Customer</dt>
          <dd className="text-sm font-medium text-[var(--im-text)]" data-testid="request-customer-name">
            {customerName ?? 'Unavailable'}
          </dd>
        </div>
      </dl>
      {/* PANEL_ACTIONS */}
      {expired ? (
        <p className="im-alert-info" role="alert" data-testid="request-expired-note">
          <Icon name="alert" size={16} />
          This request expired. Checking for the next one…
        </p>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <button
            className="im-btn im-btn-success !min-h-[48px] w-full"
            disabled={acceptAction.isPending}
            onClick={accept}
            data-testid="accept-ride"
          >
            <Icon name="check" size={16} /> {acceptAction.isPending ? 'Accepting…' : 'Accept request'}
          </button>
          <button
            className="im-btn im-btn-danger !min-h-[48px] w-full"
            disabled={rejectAction.isPending}
            onClick={reject}
            data-testid="reject-ride"
          >
            <Icon name="x" size={16} /> {rejectAction.isPending ? 'Rejecting…' : 'Reject'}
          </button>
        </div>
      )}

      {(acceptAction.isError || rejectAction.isError) && (
        <p className="im-alert-error mt-3" role="alert" data-testid="request-action-error">
          <Icon name="alert" size={16} />
          {(acceptAction.error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message ??
            (rejectAction.error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message ??
            'Unable to update this request. It may have changed state — refresh and try again.'}
        </p>
      )}
    </div>
  );
}

/**
 * Active driver-trip card. While the ride is DRIVER_ASSIGNED the body is the
 * Uber-style incoming request panel (accept/reject with countdown); after
 * acceptance the same card becomes the active trip view. Actions are strictly
 * gated by the REAL backend status — Start/Complete are never exposed before
 * the backend state machine permits them.
 */
function ActiveTripCard({ ride }: { ride: Ride }) {
  const queryClient = useQueryClient();
  const action = useDriverRideAction();
  const isIncoming = ride.status === 'DRIVER_ASSIGNED';
  // The backend has no separate "arrived" action/status for drivers — no
  // Arrived button is invented; Start trip covers the transition.
  const isPreTrip = ride.status === 'DRIVER_ACCEPTED' || ride.status === 'DRIVER_ARRIVING';
  // Live pickup ETA is only meaningful while the driver heads to the pickup.
  const { data: eta } = useRideEta(isPreTrip ? ride.id : '');
  const { data: customerUser } = useGetUser(ride.customerId);

  const run = (act: 'start' | 'complete') =>
    action.mutate(
      { rideId: ride.id, action: act },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: ['driverRides'] });
          queryClient.invalidateQueries({ queryKey: ['allRides'] });
        },
      },
    );

  const customerName = customerUser
    ? `${customerUser.firstName} ${customerUser.lastName}`.trim()
    : null;

  return (
    <section className="im-card im-fade-up p-5" data-testid="driver-active-trip">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <Icon name="navigation" size={19} className="text-[var(--im-bright)]" />
          {isIncoming ? 'New ride request' : 'Current trip'}
        </h2>
        <StatusBadge status={ride.status} />
      </div>

      {isIncoming ? (
        <IncomingRequestPanel ride={ride} />
      ) : (
        <>
          <div className="mb-4 grid gap-3 sm:grid-cols-2">
            <div className="flex items-start gap-3 rounded-xl p-3" style={{ background: 'rgba(225, 29, 104, 0.10)' }}>
              <Icon name="map-pin" size={18} style={{ color: '#FB7185', marginTop: 2 }} />
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FB7185' }}>Pickup</p>
                <p className="truncate text-sm font-medium text-[var(--im-text)]">{ride.pickupAddress ?? 'Pickup point'}</p>
              </div>
            </div>
            <div className="flex items-start gap-3 rounded-xl p-3" style={{ background: 'rgba(251, 113, 133, 0.10)' }}>
              <Icon name="flag" size={18} style={{ color: '#FDA4AF', marginTop: 2 }} />
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-wide" style={{ color: '#FDA4AF' }}>Destination</p>
                <p className="truncate text-sm font-medium text-[var(--im-text)]">{ride.dropoffAddress ?? 'Drop-off point'}</p>
              </div>
            </div>
          </div>

          <RideMap
            height={180}
            points={[
              { lat: ride.pickupLatitude, lng: ride.pickupLongitude, label: 'Pickup', kind: 'pickup' },
              { lat: ride.dropoffLatitude, lng: ride.dropoffLongitude, label: 'Drop-off', kind: 'dropoff' },
            ]}
          />

          <dl className="mt-4 grid grid-cols-2 gap-3 text-sm md:grid-cols-4">
            <div>
              <dt className="text-[var(--im-text-muted)]">Customer</dt>
              <dd className="font-medium text-[var(--im-text)]" data-testid="trip-customer-name">
                {customerName ?? 'Unavailable'}
              </dd>
            </div>
            <div>
              <dt className="text-[var(--im-text-muted)]">Ride type</dt>
              <dd className="font-medium capitalize text-[var(--im-text)]">
                {ride.rideType ? ride.rideType.toLowerCase() : 'Unavailable'}
              </dd>
            </div>
            <div>
              <dt className="text-[var(--im-text-muted)]">Estimated fare</dt>
              <dd className="font-medium text-[var(--im-text)]">
                {ride.currency ?? 'USD'} {ride.estimatedFare != null ? ride.estimatedFare.toFixed(2) : '—'}
              </dd>
            </div>
            {isPreTrip && (
              <div>
                <dt className="text-[var(--im-text-muted)]">Pickup ETA</dt>
                <dd className="font-medium text-[var(--im-text)]" data-testid="trip-pickup-eta">
                  {eta ? `${eta.etaMinutes} min · ${eta.distanceKm} km` : 'Unavailable'}
                </dd>
              </div>
            )}
          </dl>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              {isPreTrip && (
                <button
                  className="im-btn im-btn-primary !min-h-[48px]"
                  disabled={action.isPending}
                  onClick={() => run('start')}
                  data-testid="start-trip"
                >
                  <Icon name="navigation" size={16} /> {action.isPending ? 'Starting…' : 'Start trip'}
                </button>
              )}
              {ride.status === 'TRIP_STARTED' && (
                <button
                  className="im-btn im-btn-primary !min-h-[48px]"
                  disabled={action.isPending}
                  onClick={() => run('complete')}
                  data-testid="complete-trip"
                >
                  <Icon name="check-circle" size={16} /> {action.isPending ? 'Completing…' : 'Complete trip'}
                </button>
              )}
            </div>
            {/* Navigate with real backend coordinates (pickup pre-trip, drop-off on trip). */}
            {(isPreTrip || ride.status === 'TRIP_STARTED') && (
              <a
                className="im-btn im-btn-secondary !min-h-[48px]"
                href={`https://www.google.com/maps/dir/?api=1&destination=${
                  isPreTrip ? ride.pickupLatitude : ride.dropoffLatitude
                },${isPreTrip ? ride.pickupLongitude : ride.dropoffLongitude}`}
                target="_blank"
                rel="noreferrer"
                data-testid="navigate-action"
              >
                <Icon name="map-pin" size={16} />
                {isPreTrip ? 'Navigate to pickup' : 'Navigate to drop-off'}
              </a>
            )}
          </div>
        </>
      )}
      {action.isError && (
        <p className="im-alert-error mt-3" role="alert">
          <Icon name="alert" size={16} />
          Unable to update this trip. It may have changed state — refresh and try again.
        </p>
      )}
    </section>
  );
}

export default function DriverDashboard() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const { data: driver, isLoading } = useGetDriver(user?.id || '');
  const updateStatus = useUpdateDriverStatus();
  const updateLocation = useUpdateDriverLocation();
  const { data: driverRides, isLoading: ridesLoading } = useGetDriverRides(user?.id || '');
  const [locationNote, setLocationNote] = useState<string | null>(null);

  // ─── Real-time incoming assignment push (existing WS infrastructure) ───
  // The dashboard subscribes to its OWN personal channel (identity derived
  // from the JWT at the WS handshake). A 'ride_assigned' message triggers a
  // refetch of the REAL ride data via the existing REST contract — nothing is
  // taken from the WS payload, so duplicates/stale events cannot fabricate UI.
  const [liveAnnouncement, setLiveAnnouncement] = useState('');
  const announcedRidesRef = useRef<Set<string>>(new Set());
  const handleWsMessage = useCallback(
    (data: unknown) => {
      const msg = data as { type?: string; rideId?: string };
      if (msg?.type === 'ride_assigned' && msg.rideId) {
        queryClient.invalidateQueries({ queryKey: ['driverRides'] });
        // Announce at most once per ride (duplicate WS events / reconnects).
        if (!announcedRidesRef.current.has(msg.rideId)) {
          announcedRidesRef.current.add(msg.rideId);
          setLiveAnnouncement('New ride request received. Review and respond.');
        }
      }
    },
    [queryClient],
  );
  // useRideWebSocket auto-reconnects with backoff and re-sends the
  // subscribe_user message after every reconnect; the 5s driverRides polling
  // above remains the existing REST fallback for disconnects.
  useRideWebSocket('/location', handleWsMessage, { type: 'subscribe_user' });

  const activeRide =
    driverRides?.content?.find((r) => ACTIVE_RIDE_STATUSES.includes(r.status)) ?? null;

  // While ONLINE, report the driver's GPS position to the location service so
  // Redis GEO matching can find them (server derives the driver ID from the JWT).
  // While an assigned trip is in progress, the ride ID is attached so the
  // Location Service broadcasts this GPS update to the rider's live map.
  const status = driver?.status ?? 'OFFLINE';
  const isOnline = status !== 'OFFLINE';
  const trackingRideId =
    activeRide && ['DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING', 'TRIP_STARTED'].includes(activeRide.status)
      ? activeRide.id
      : null;

  useEffect(() => {
    if (!isOnline || !('geolocation' in navigator)) return;
    let cancelled = false;
    const report = () =>
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          if (cancelled) return;
          updateLocation.mutate(
            {
              latitude: pos.coords.latitude,
              longitude: pos.coords.longitude,
              ...(trackingRideId ? { rideId: trackingRideId } : {}),
            },
            { onSuccess: () => setLocationNote(null) },
          );
        },
        () => {
          if (!cancelled) setLocationNote('Location access denied — you will not appear in nearby ride searches.');
        },
        { enableHighAccuracy: true, timeout: 8000 },
      );
    report();
    const id = window.setInterval(report, 30000);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOnline, trackingRideId]);

  const toggleOnline = () => {
    if (!driver) return;
    const newStatus = driver.status === 'OFFLINE' ? 'ONLINE' : 'OFFLINE';
    updateStatus.mutate(
      { id: driver.id, status: newStatus },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: ['driver', user?.id] });
          queryClient.invalidateQueries({ queryKey: ['availableDrivers'] });
        },
      },
    );
  };

  const isOffline = status === 'OFFLINE';
  const style = STATUS_STYLES[status] ?? STATUS_STYLES.OFFLINE;

  return (
    <AppShell title="Drive">
      {/* Screen-reader announcements for incoming requests / status changes.
          Polite + change-driven (not per second) to avoid SR spam. */}
      <p role="status" aria-live="polite" className="sr-only">
        {liveAnnouncement}
      </p>
      <div className="space-y-6">
        {/* Active trip — accept / start / complete */}
        {activeRide && <ActiveTripCard ride={activeRide} />}
        {!activeRide && !ridesLoading && status !== 'OFFLINE' && (
          <div className="im-card im-fade-up flex items-center gap-4 p-5" data-testid="no-active-trip">
            <span aria-hidden="true" className="grid h-11 w-11 shrink-0 place-items-center rounded-full" style={{ background: 'rgba(251, 113, 133, 0.14)', color: '#FDA4AF' }}>
              <Icon name="bell" size={20} />
            </span>
            <div>
              <p className="font-semibold text-[var(--im-text)]">Waiting for ride requests</p>
              <p className="text-sm text-[var(--im-text-muted)]">Stay online and visible — new trip requests will appear here instantly.</p>
            </div>
          </div>
        )}

        {locationNote && isOnline && (
          <div className="im-alert-info" role="status">
            <Icon name="alert" size={16} />
            <span>{locationNote}</span>
          </div>
        )}

        {/* Status card */}
        <section className="im-card im-fade-up p-5" data-testid="driver-status-card">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold">Driver Status</h2>
              <span
                className="im-badge mt-2"
                style={{ background: style.bg, color: style.fg }}
                data-testid="driver-status-badge"
              >
                {status}
              </span>
              <p className="mt-2 text-sm text-[var(--im-text-muted)]">
                {isOffline
                  ? 'You are offline. Go online to receive ride requests.'
                  : 'You are visible to nearby riders.'}
              </p>
            </div>
            <button
              onClick={toggleOnline}
              disabled={!driver || updateStatus.isPending}
              className={`im-btn ${isOffline ? 'im-btn-success' : 'im-btn-danger'} !px-6 !py-3`}
              data-testid="toggle-status"
            >
              <Icon name={isOffline ? 'navigation' : 'logout'} size={18} />
              {updateStatus.isPending ? 'Updating…' : isOffline ? 'Go Online' : 'Go Offline'}
            </button>
          </div>
          {updateStatus.isError && (
            <p className="im-alert-error mt-4" role="alert">
              <Icon name="alert" size={16} /> Unable to change your status right now. Please try again.
            </p>
          )}
        </section>


        {/* Stats */}
        {isLoading ? (
          <div className="grid grid-cols-3 gap-4">
            {[0, 1, 2].map((i) => (
              <div key={i} className="im-skeleton h-24" />
            ))}
          </div>
        ) : (
          driver && (
            <section className="grid grid-cols-3 gap-4">
              <div className="im-card im-card-pad text-center">
                <p className="flex items-center justify-center gap-1 text-2xl font-bold text-amber-500">
                  <Icon name="star" size={20} /> {driver.rating.toFixed(1)}
                </p>
                <p className="text-sm text-[var(--im-text-muted)]">Rating</p>
              </div>
              <div className="im-card im-card-pad text-center">
                <p className="text-2xl font-bold" style={{ color: 'var(--im-success)' }}>
                  {driver.totalTrips}
                </p>
                <p className="text-sm text-[var(--im-text-muted)]">Total Trips</p>
              </div>
              <div className="im-card im-card-pad text-center">
                <p
                  className="mx-auto grid h-7 w-7 place-items-center rounded-full font-bold text-[#FFFFFF]"
                  style={{ background: driver.verified ? 'var(--im-success)' : '#9F7183' }}
                  aria-label={driver.verified ? 'Verified driver' : 'Not yet verified'}
                >
                  {driver.verified ? <Icon name="check" size={15} strokeWidth={2.5} /> : <Icon name="x" size={14} strokeWidth={2.5} />}
                </p>
                <p className="mt-1 text-sm text-[var(--im-text-muted)]">Verified</p>
              </div>
            </section>
          )
        )}

        {/* Vehicle */}
        {driver && (
          <section className="im-card im-fade-up p-5">
            <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
              <Icon name="car" size={20} className="text-[var(--im-bright)]" /> Vehicle
            </h2>
            <dl className="grid grid-cols-2 gap-4 text-sm md:grid-cols-4">
              <div>
                <dt className="text-[var(--im-text-muted)]">Vehicle</dt>
                <dd className="font-medium text-[var(--im-text)]">
                  {driver.vehicleYear} {driver.vehicleMake} {driver.vehicleModel}
                </dd>
              </div>
              <div>
                <dt className="text-[var(--im-text-muted)]">Color</dt>
                <dd className="font-medium text-[var(--im-text)]">{driver.vehicleColor}</dd>
              </div>
              <div>
                <dt className="text-[var(--im-text-muted)]">Plate</dt>
                <dd className="font-mono font-semibold text-[var(--im-text)]">{driver.licensePlate}</dd>
              </div>
              <div>
                <dt className="text-[var(--im-text-muted)]">Type</dt>
                <dd className="font-medium capitalize text-[var(--im-text)]">{driver.vehicleType.toLowerCase()}</dd>
              </div>
            </dl>
          </section>
        )}

        {!isLoading && !driver && (
          <div className="im-card im-card-pad text-center text-sm text-[var(--im-text-muted)]">
            No driver profile found for your account yet. Please contact support to complete driver onboarding.
          </div>
        )}
      </div>
    </AppShell>
  );
}
