import { useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import AppShell from '../components/AppShell';
import RideMap from '../components/RideMap';
import LocationPicker, { type PlacePoint } from '../components/LocationPicker';
import { EmptyState, SkeletonList, StatusBadge } from '../components/ui';
import { ToastStack, useToastStack } from '../components/Toasts';
import type { RideOptionEstimate, SavedPlace } from '../api/types';
import {
  useGetCustomerRides as useCustomerRides,
  useFareEstimate,
  useRequestRide,
  useRateDriver,
  useRidePayment,
  useSavedPlaces,
  useUnreadNotificationCount,
} from '../api/hooks';
import { rideRequestError } from '../api/errors';
import ActiveRideCard from './customer/ActiveRideCard';

const ACTIVE_STATUSES = ['REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING', 'TRIP_STARTED'];

/**
 * Per-category presentation metadata (icon + subtle badge). UI-only: the ride
 * description, fare, ETA, capacity and surge multiplier shown on each card all
 * come from the real estimate API and are never overridden by this map.
 */
const RIDE_TYPE_META: Record<
  RideOptionEstimate['rideType'],
  { icon: 'car' | 'user' | 'star' | 'users'; badge: string; fallbackDesc: string; tile: string }
> = {
  ECONOMY: { icon: 'car', badge: 'Best value', fallbackDesc: 'Affordable everyday rides', tile: '' },
  COMFORT: { icon: 'user', badge: 'More comfort', fallbackDesc: 'Newer cars, extra legroom', tile: '' },
  // Luxury tier: subtle always-on rose glow reusing the existing brand glow token.
  PREMIUM: { icon: 'star', badge: 'Premium', fallbackDesc: 'Top-rated drivers, luxury cars', tile: 'shadow-[var(--im-glow-sm)]' },
  XL: { icon: 'users', badge: 'More space', fallbackDesc: 'Room for larger groups', tile: '' },
};


const titleCase = (t: string) => t.charAt(0) + t.slice(1).toLowerCase();
/** "XL" stays uppercase — Uber-style category labels. */
const rideTypeLabel = (t: string) => (t === 'XL' ? 'XL' : titleCase(t));

/** Reverse-geocode real device coordinates into a readable address label
 * via the existing key-free /geocode proxy. Falls back to raw coordinates. */
async function reverseGeocode(lat: number, lng: number): Promise<string> {
  try {
    const r = await fetch(`/geocode/reverse?format=jsonv2&lat=${lat}&lon=${lng}`, {
      headers: { Accept: 'application/json' },
    });
    if (!r.ok) throw new Error(String(r.status));
    const data = (await r.json()) as { display_name?: string };
    return data.display_name ?? `Current location (${lat.toFixed(4)}, ${lng.toFixed(4)})`;
  } catch {
    return `Current location (${lat.toFixed(4)}, ${lng.toFixed(4)})`;
  }
}

export default function CustomerDashboard() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [pickup, setPickup] = useState<PlacePoint | null>(null);
  const [dropoff, setDropoff] = useState<PlacePoint | null>(null);
  const [selectedType, setSelectedType] = useState<RideOptionEstimate['rideType']>('ECONOMY');
  const [bookingError, setBookingError] = useState<string | null>(null);
  const [locatingMe, setLocatingMe] = useState(false);
  const requestRide = useRequestRide();
  const rateDriver = useRateDriver();
  const [ratedIds, setRatedIds] = useState<Set<string>>(() => new Set());
  const bookingRef = useRef<HTMLElement>(null);
  const { toasts, push, dismiss } = useToastStack();
  const { data: savedPlaces = [] } = useSavedPlaces();
  const { data: unread = 0 } = useUnreadNotificationCount();

  // Light polling keeps trip status live (WebSocket pushes also feed this key).
  const { data: rides, isLoading, isError: ridesError, refetch: refetchRides } = useCustomerRides(user?.id ?? '');

  const activeRide = useMemo(
    () => rides?.content?.find((r) => ACTIVE_STATUSES.includes(r.status)) ?? null,
    [rides],
  );

  // Most recent completed trip (shown as a "Last trip" summary once no ride is active).
  const lastCompleted = useMemo(
    () => (!activeRide && rides?.content?.[0]?.status === 'TRIP_COMPLETED' ? rides.content[0] : null),
    [activeRide, rides],
  );
  const { data: lastPayment, isLoading: paymentLoading } = useRidePayment(lastCompleted?.id ?? '');

  /** Smooth-scrolls to the booking hero unless reduced motion is requested. */
  const scrollToBooking = () => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    bookingRef.current?.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
  };

  const estimateParams =
    pickup && dropoff
      ? { pickupLat: pickup.lat, pickupLng: pickup.lng, dropoffLat: dropoff.lat, dropoffLng: dropoff.lng }
      : null;
  const { data: estimate, isFetching: estimating } = useFareEstimate(estimateParams);

  const recentDestinations = useMemo(() => {
    const seen = new Set<string>();
    const out: PlacePoint[] = [];
    for (const r of rides?.content ?? []) {
      if (r.dropoffAddress && !seen.has(r.dropoffAddress)) {
        seen.add(r.dropoffAddress);
        out.push({ lat: r.dropoffLatitude, lng: r.dropoffLongitude, address: r.dropoffAddress });
      }
      if (out.length >= 2) break;
    }
    return out;
  }, [rides]);

  // …plus Home / Work from the real Saved Places API.
  const savedSuggestions = useMemo(() => {
    const toPoint = (p: SavedPlace): PlacePoint => ({ lat: p.latitude, lng: p.longitude, address: p.address });
    const home = savedPlaces.find((p) => p.type === 'HOME');
    const work = savedPlaces.find((p) => p.type === 'WORK');
    return [
      home ? { place: home, point: toPoint(home), tag: 'Home' } : null,
      work ? { place: work, point: toPoint(work), tag: 'Work' } : null,
    ].filter(Boolean) as { place: SavedPlace; point: PlacePoint; tag: string }[];
  }, [savedPlaces]);

  const selectedOption = estimate?.options.find((o) => o.rideType === selectedType);

  /**
   * UI-only "Recommended" badge — deterministic rule, no fabricated data:
   * recommend the option with the LOWEST API-provided estimatedFare for the
   * current trip (ties broken by backend option order, ECONOMY first).
   * The badge never overrides fares, ETAs or descriptions from the API.
   */
  const recommendedType: RideOptionEstimate['rideType'] | null = useMemo(() => {
    const opts = estimate?.options ?? [];
    if (opts.length === 0) return null;
    return opts.reduce(
      (best, o) => (Number(o.estimatedFare) < Number(best.estimatedFare) ? o : best),
      opts[0],
    ).rideType;
  }, [estimate]);

  const rideOptionsRef = useRef<HTMLDivElement>(null);

  /** Roving-focus selection for the radiogroup: selection follows focus. */
  const moveRideSelection = (offset: number | 'first' | 'last') => {
    const opts = estimate?.options ?? [];
    if (opts.length === 0) return;
    const idx = opts.findIndex((o) => o.rideType === selectedType);
    const next =
      offset === 'first'
        ? 0
        : offset === 'last'
          ? opts.length - 1
          : (idx + offset + opts.length) % opts.length;
    setSelectedType(opts[next].rideType);
    rideOptionsRef.current?.querySelectorAll<HTMLButtonElement>('[role="radio"]')[next]?.focus();
  };

  const onRideOptionKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowRight') {
      e.preventDefault();
      moveRideSelection(1);
    } else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') {
      e.preventDefault();
      moveRideSelection(-1);
    } else if (e.key === 'Home') {
      e.preventDefault();
      moveRideSelection('first');
    } else if (e.key === 'End') {
      e.preventDefault();
      moveRideSelection('last');
    }
  };


  /** Uses the browser's REAL Geolocation API for the pickup point. */
  const useMyLocation = () => {
    if (!('geolocation' in navigator)) {
      setBookingError('Your browser does not support location detection — please enter your pickup manually.');
      return;
    }
    setLocatingMe(true);
    setBookingError(null);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const { latitude, longitude } = pos.coords;
        const address = await reverseGeocode(latitude, longitude);
        setPickup({ lat: latitude, lng: longitude, address });
        setLocatingMe(false);
      },
      () => {
        setLocatingMe(false);
        setBookingError('We could not access your location. Please allow location access or type your pickup.');
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30_000 },
    );
  };

  const swapPoints = () => {
    if (!pickup && !dropoff) return;
    setBookingError(null);
    setPickup(dropoff);
    setDropoff(pickup);
  };

  const handleRequest = async () => {
    setBookingError(null);
    if (!pickup || !dropoff) {
      setBookingError('Please choose both a pickup location and a destination.');
      return;
    }
    if (activeRide) {
      setBookingError('You already have an active trip. Complete or cancel it before booking a new ride.');
      return;
    }
    if (requestRide.isPending) return;
    try {
      await requestRide.mutateAsync({
        rideType: selectedType,
        pickupLatitude: pickup.lat,
        pickupLongitude: pickup.lng,
        dropoffLatitude: dropoff.lat,

        dropoffLongitude: dropoff.lng,
        pickupAddress: pickup.address,
        dropoffAddress: dropoff.address,
      });
      if (user?.id) queryClient.invalidateQueries({ queryKey: ['customerRides', user.id] });
      push('success', 'Ride requested! Matching you with a nearby driver…');
    } catch (err) {
      setBookingError(rideRequestError(err));
    }
  };

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

  return (
    <AppShell>
      {/* Hero / booking */}
      <section
        ref={bookingRef}
        className="im-fade-up mb-6 scroll-mt-20 rounded-2xl p-5 text-[#FFFFFF] shadow-xl md:p-6"
        style={{ background: 'linear-gradient(120deg,#170C13 0%,#2A1420 42%,#57132E 100%)' }}
      >
        <p className="text-sm font-medium opacity-80">{greeting}, {user?.firstName}</p>
        <h2 className="mb-1 mt-1 text-2xl font-bold tracking-tight md:text-3xl">Where are you going today?</h2>
        <p className="mb-5 text-sm opacity-70">Set your route, compare ride types and get moving in seconds.</p>

        {activeRide ? (
          <ActiveRideCard ride={activeRide} />
        ) : (
          <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
            <div className="im-elevate rounded-2xl border border-[var(--im-border)] bg-[var(--im-elevated)] p-5 text-[var(--im-text)] shadow-lg">
              <div className="space-y-4" data-testid="booking-panel">
                <LocationPicker label="CURRENT LOCATION" icon="map-pin" suggestions={[]} value={pickup} onPick={setPickup} />

                {/* Pickup utilities: detect real device location + swap endpoints */}
                <div className="-my-1 flex items-center justify-between gap-2">
                  <button
                    type="button"
                    onClick={useMyLocation}
                    disabled={locatingMe}
                    className="inline-flex items-center gap-1.5 rounded-full border border-[var(--im-border)] bg-[var(--im-canvas)] px-3 py-1 text-xs font-semibold text-[var(--im-text-secondary)] transition hover:border-[var(--im-focus-ring)] hover:bg-[rgb(225_29_104/0.12)] hover:text-[var(--im-soft)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-60"
                    style={{ outlineColor: 'var(--im-brand-600)' }}
                    aria-label="Use my current location as pickup"
                  >
                    <Icon name="navigation" size={13} className={locatingMe ? 'im-spin' : ''} />
                    {locatingMe ? 'Locating…' : 'Use my location'}
                  </button>
                  <button
                    type="button"
                    onClick={swapPoints}
                    disabled={!pickup && !dropoff}
                    className="inline-flex items-center gap-1.5 rounded-full border border-[var(--im-border)] bg-[var(--im-canvas)] px-3 py-1 text-xs font-semibold text-[var(--im-text-secondary)] transition hover:border-[var(--im-focus-ring)] hover:bg-[rgb(225_29_104/0.12)] hover:text-[var(--im-soft)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-40"
                    style={{ outlineColor: 'var(--im-brand-600)' }}
                    aria-label="Swap pickup and destination"
                    title="Swap pickup and destination"
                  >
                    <Icon name="route" size={13} /> Swap
                  </button>
                </div>

                <LocationPicker label="WHERE TO?" icon="flag" suggestions={[...savedSuggestions.map((s) => s.point), ...recentDestinations]} value={dropoff} onPick={setDropoff} />

                {/* Home / Work shortcuts from the real Saved Places API */}
                {savedSuggestions.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {savedSuggestions.map(({ place, point, tag }) => (
                      <button
                        key={place.id}
                        type="button"
                        onClick={() => setDropoff(point)}
                        title={place.address}
                        className="inline-flex items-center gap-1.5 rounded-full border border-[rgb(225_29_104/0.30)] bg-[rgb(225_29_104/0.12)] px-3 py-1 text-xs font-semibold text-[var(--im-soft)] transition hover:bg-[rgb(225_29_104/0.18)]"
                      >
                        <Icon name={tag === 'Home' ? 'home' : 'wallet'} size={12} /> {tag}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {estimating && !estimate && (
                <div className="mt-5" aria-hidden="true">
                  <div className="grid gap-2.5 sm:grid-cols-2">
                    {[0, 1, 2, 3].map((i) => (
                      <div key={i} className="flex items-center gap-3 rounded-xl border border-[var(--im-border)] bg-[var(--im-surface)] p-3">
                        <div className="im-skeleton h-12 w-12 rounded-lg" />
                        <div className="flex-1 space-y-1.5">
                          <div className="im-skeleton h-3.5 w-24" />
                          <div className="im-skeleton h-3 w-36" />
                        </div>
                        <div className="im-skeleton h-4 w-12" />
                      </div>
                    ))}
                  </div>
                </div>
              )}


              {estimate && (
                <div className="mt-5" data-testid="ride-options">
                  <p className="im-label !mb-1">Choose a ride</p>
                  <div
                    ref={rideOptionsRef}
                    role="radiogroup"
                    aria-label="Choose a ride"
                    onKeyDown={onRideOptionKeyDown}
                    className="grid gap-2.5 sm:grid-cols-2"
                  >
                  {estimate.options.map((o) => {
                    const meta = RIDE_TYPE_META[o.rideType] ?? RIDE_TYPE_META.ECONOMY;
                    const active = selectedType === o.rideType;
                    const recommended = recommendedType === o.rideType;
                    // Real backend demand multiplier; only surfaced when it actually surges.
                    const surge = Number(o.surgeMultiplier ?? 1);
                    const description = o.description ?? meta.fallbackDesc;
                    const fare = Number(o.estimatedFare).toFixed(2);
                    return (
                      <button
                        key={o.rideType}
                        type="button"
                        role="radio"
                        aria-checked={active}
                        tabIndex={active ? 0 : -1}
                        onClick={() => setSelectedType(o.rideType)}
                        aria-label={`${rideTypeLabel(o.rideType)}. ${description} Pickup in about ${o.etaMinutes} minutes, ${o.capacity} seats, estimated fare $${fare}.`}
                        data-testid={`option-${o.rideType}`}
                        className={`im-ride-option relative flex w-full items-center gap-3 rounded-xl border p-3 text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 ${
                          active
                            ? 'scale-[1.015] border-[var(--im-brand-600)] bg-[rgb(225_29_104/0.16)] shadow-[0_0_0_1px_var(--im-brand-600),0_10px_28px_rgb(225_29_104/0.28)]'
                            : 'border-[var(--im-border)] bg-[var(--im-surface)] hover:border-[var(--im-focus-ring)] hover:bg-[rgb(244_63_127/0.08)]'
                        }`}
                        style={{ outlineColor: 'var(--im-brand-600)' }}
                      >

                        <span aria-hidden="true" className={`grid h-12 w-12 shrink-0 place-items-center rounded-lg transition-colors ${meta.tile} ${active ? 'bg-[rgb(225_29_104/0.20)] text-[var(--im-bright)]' : 'bg-[var(--im-elevated)] text-[var(--im-text-muted)]'}`}>
                          <Icon name={meta.icon} size={22} />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex flex-wrap items-center gap-1.5">
                            <span className="font-semibold text-[var(--im-text)]">{rideTypeLabel(o.rideType)}</span>
                            {recommended && (
                              <span className="inline-flex items-center gap-0.5 rounded-full border border-[rgb(225_29_104/0.35)] bg-[rgb(225_29_104/0.16)] px-1.5 py-px text-[0.6rem] font-bold uppercase tracking-wide text-[var(--im-bright)]">
                                <Icon name="sparkles" size={10} /> Recommended
                              </span>
                            )}
                            <span className="rounded-full border border-[var(--im-border)] bg-[var(--im-elevated)] px-1.5 py-px text-[0.6rem] font-bold uppercase tracking-wide text-[var(--im-text-muted)]">
                              {meta.badge}
                            </span>
                          </span>
                          <span className="block truncate text-xs text-[var(--im-text-muted)]">{description}</span>
                          <span className="mt-0.5 flex items-center gap-2.5 text-xs font-medium text-[var(--im-text-secondary)]">
                            <span className="flex items-center gap-1">
                              <Icon name="clock" size={11} /> pickup in ~{o.etaMinutes} min
                            </span>
                            <span className="flex items-center gap-1">
                              <Icon name="users" size={11} /> {o.capacity} seats
                            </span>
                          </span>
                        </span>
                        <span className="shrink-0 text-right">
                          <span className="block text-base font-extrabold leading-tight text-[var(--im-text)]">${Number(o.estimatedFare).toFixed(2)}</span>
                          {surge > 1 && (
                            <span className="mt-0.5 inline-flex items-center gap-0.5 text-[0.68rem] font-semibold" style={{ color: 'var(--im-warning)' }}>
                              <Icon name="trend" size={10} /> {surge.toFixed(1)}× demand
                            </span>
                          )}
                        </span>
                        {active && (
                          <span aria-hidden="true" className="im-pop absolute -top-2 right-3 grid h-5 w-5 place-items-center rounded-full bg-[var(--im-brand-600)] text-[#FFFFFF] shadow">
                            <Icon name="check" size={11} strokeWidth={3} />
                          </span>
                        )}
                      </button>
                    );
                  })}
                  </div>
                  <p className="flex items-center gap-1.5 px-1 pt-1 text-xs text-[var(--im-text-muted)]">
                    <Icon name="activity" size={12} />
                    {estimate.distanceKm} km trip · about {estimate.estimatedMinutes} min · live pricing engine estimates
                  </p>
                </div>
              )}

              {bookingError && (
                <p className="im-alert-error mt-4" role="alert" data-testid="booking-error">
                  <Icon name="alert" size={16} /> {bookingError}
                </p>
              )}

              <button
                className="im-btn im-btn-primary mt-5 w-full !py-3 text-base"
                onClick={handleRequest}
                disabled={requestRide.isPending || !pickup || !dropoff}
                data-testid="request-ride"
              >
                {requestRide.isPending ? (
                  <>
                    <Icon name="search" size={18} className="im-spin" /> Finding your ride…
                  </>
                ) : (
                  <>
                    Request {titleCase(selectedType)}
                    {selectedOption && (
                      <span data-testid="selected-fare" className="font-extrabold">
                        {' '}· ${Number(selectedOption.estimatedFare).toFixed(2)}
                      </span>
                    )}
                  </>
                )}
              </button>
            </div>

            {/* Map — a major visual element at every breakpoint, driven by
                real coordinates only; skeleton shimmer while estimating. */}
            <div>
              <RideMap
                height={340}
                loading={estimating}
                enableLocate
                points={[
                  ...(pickup ? [{ lat: pickup.lat, lng: pickup.lng, label: 'Pickup', kind: 'pickup' as const }] : []),
                  ...(dropoff ? [{ lat: dropoff.lat, lng: dropoff.lng, label: 'Destination', kind: 'dropoff' as const }] : []),
                ]}
              />
              {estimate && (
                <div className="mt-3 flex items-center justify-between gap-3 rounded-xl bg-white/10 px-4 py-2.5 text-sm backdrop-blur">
                  <span className="flex items-center gap-2 font-medium">
                    <Icon name="route" size={15} /> {estimate.distanceKm} km
                  </span>
                  <span className="flex items-center gap-2 font-medium">
                    <Icon name="clock" size={15} /> ~{estimate.estimatedMinutes} min
                  </span>
                  <span className="flex items-center gap-2 font-semibold">
                    <Icon name="wallet" size={15} /> {estimate.currency ?? 'USD'}{' '}
                    {selectedOption ? Number(selectedOption.estimatedFare).toFixed(2) : '—'}
                  </span>
                </div>
              )}
            </div>
          </div>
        )}
      </section>

      {/* Quick actions */}
      <section aria-label="Quick actions" className="im-fade-up mb-8">
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-6">
          {([
            { to: '/dashboard', label: 'Book a ride', icon: 'car' },
            { to: '/rides', label: 'My rides', icon: 'route' },
            { to: '/payments', label: 'Payments', icon: 'wallet' },
            { to: '/notifications', label: 'Notifications', icon: 'bell', badge: unread },
            { to: '/profile', label: 'Profile', icon: 'user' },
            { to: '/saved-places', label: 'Saved places', icon: 'map-pin' },
          ] as { to: string; label: string; icon: 'car' | 'route' | 'wallet' | 'bell' | 'user' | 'map-pin'; badge?: number }[]).map((a) => (
            <Link
              key={a.to}
              to={a.to}
              className="im-card im-elevate relative flex flex-col items-center gap-2 px-2 py-4 text-center text-xs font-semibold text-[var(--im-text-secondary)] transition-colors hover:text-[var(--im-soft)]"
            >
              <span aria-hidden="true" className="grid h-10 w-10 place-items-center rounded-xl bg-[rgb(225_29_104/0.12)] text-[var(--im-bright)]">
                <Icon name={a.icon} size={19} />
              </span>
              {a.label}
              {!!a.badge && a.badge > 0 && (
                <span
                  data-testid="quick-action-unread"
                  className="absolute right-2 top-2 grid h-5 min-w-5 place-items-center rounded-full px-1 text-[10px] font-bold text-[#FFFFFF]"
                  style={{ background: 'var(--im-danger)' }}
                  aria-label={`${a.badge} unread notifications`}
                >
                  {a.badge > 99 ? '99+' : a.badge}
                </span>
              )}
            </Link>
          ))}
        </div>
      </section>

      {/* Last trip — completed ride summary with real payment status + rating */}
      {lastCompleted && (
        <section aria-label="Last trip" className="im-fade-up im-card im-elevate mb-8 p-5" data-testid="last-trip-card">
          <div className="mb-4 flex items-center justify-between gap-3">
            <h3 className="text-lg font-semibold">Last trip</h3>
            <StatusBadge status={lastCompleted.status} />
          </div>

          <div className="grid gap-5 lg:grid-cols-[1fr_260px]">
            <div>
              {/* Route summary from the real ride record */}
              <div className="mb-3 space-y-1.5 rounded-xl border border-[var(--im-border)] bg-[var(--im-canvas)]/60 p-3">
                <p className="flex items-start gap-2.5 text-sm">
                  <span aria-hidden="true" className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full ring-4 ring-[rgb(244_63_127/0.18)]" style={{ background: '#F43F7F' }} />
                  <span className="min-w-0 flex-1 truncate text-[var(--im-text-secondary)]">{lastCompleted.pickupAddress ?? 'Pickup'}</span>
                </p>
                <p aria-hidden="true" className="ml-[4px] h-3 w-px bg-[var(--im-input-border)]" />
                <p className="flex items-start gap-2.5 text-sm">
                  <span aria-hidden="true" className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full ring-4 ring-[rgb(225_29_104/0.18)]" style={{ background: '#E11D68' }} />
                  <span className="min-w-0 flex-1 truncate font-medium text-[var(--im-text)]">{lastCompleted.dropoffAddress ?? 'Destination'}</span>
                </p>
              </div>

              <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm sm:grid-cols-4">
                <dt className="text-[var(--im-text-muted)]">Final fare</dt>
                <dd className="font-bold text-[var(--im-text)]">
                  {lastCompleted.finalFare != null
                    ? `${lastCompleted.currency ?? 'USD'} ${lastCompleted.finalFare.toFixed(2)}`
                    : '—'}
                </dd>
                <dt className="text-[var(--im-text-muted)]">Payment</dt>
                <dd>
                  {paymentLoading ? (
                    <span className="im-skeleton inline-block h-4 w-16 align-middle" aria-hidden="true" />
                  ) : lastPayment?.status ? (
                    <StatusBadge status={lastPayment.status} />
                  ) : (
                    <span className="text-xs text-[var(--im-text-muted)]">Processing…</span>
                  )}
                </dd>
                <dt className="text-[var(--im-text-muted)]">Distance</dt>
                <dd className="font-medium text-[var(--im-text-secondary)]">{Number(lastCompleted.distanceKm).toFixed(1)} km</dd>
                <dt className="text-[var(--im-text-muted)]">Duration</dt>
                <dd className="font-medium text-[var(--im-text-secondary)]">{lastCompleted.durationMinutes} min</dd>
              </dl>

              {/* Rating — uses the existing driver rating API once per trip */}
              {lastCompleted.driverId && (
                <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50/70 p-3">
                  {ratedIds.has(lastCompleted.id) ? (
                    <p className="flex items-center gap-2 text-sm font-medium text-amber-800">
                      <Icon name="check-circle" size={16} /> Thanks for rating your driver!
                    </p>
                  ) : (
                    <>
                      <p className="mb-1.5 text-sm font-semibold text-amber-900">How was your trip?</p>
                      <div className="flex gap-1" role="group" aria-label="Rate your driver from 1 to 5 stars">
                        {[1, 2, 3, 4, 5].map((n) => (
                          <button
                            key={n}
                            type="button"
                            aria-label={`Rate ${n} star${n > 1 ? 's' : ''}`}
                            disabled={rateDriver.isPending}
                            onClick={() =>
                              rateDriver.mutate(
                                { id: lastCompleted.driverId!, rating: n },
                                {
                                  onSuccess: () => {
                                    setRatedIds((s) => new Set(s).add(lastCompleted.id));
                                    push('success', 'Thanks for rating your driver!');
                                  },
                                  onError: () => push('error', 'Could not submit the rating right now.'),
                                },
                              )
                            }
                            className="rounded p-0.5 text-amber-400 transition hover:scale-110 hover:text-amber-500 disabled:opacity-50"
                          >
                            <Icon name="star" size={22} style={{ fill: 'currentColor' }} />
                          </button>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              )}
            </div>

            <RideMap
              height={180}
              points={[
                { lat: lastCompleted.pickupLatitude, lng: lastCompleted.pickupLongitude, label: 'Pickup', kind: 'pickup' },
                { lat: lastCompleted.dropoffLatitude, lng: lastCompleted.dropoffLongitude, label: 'Destination', kind: 'dropoff' },
              ]}
            />
          </div>

          <div className="mt-4 flex justify-end">
            <Link to={`/rides/${lastCompleted.id}`} className="im-btn im-btn-secondary !px-3 !py-1.5 !text-sm">
              View receipt <Icon name="chevron-right" size={14} />
            </Link>
          </div>
        </section>
      )}

      {/* Saved places — Home / Work / recent, backed by the existing APIs */}
      <section aria-label="Saved places" className="im-fade-up mb-8">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-lg font-semibold">Saved places</h3>
          <Link to="/saved-places" className="im-nav-item !py-1 !px-2 text-sm" style={{ color: 'var(--im-brand-600)' }}>
            View all <Icon name="chevron-right" size={15} />
          </Link>
        </div>
        {savedSuggestions.length > 0 || recentDestinations.length > 0 ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {savedSuggestions.map(({ place, point, tag }) => (
              <button
                key={place.id}
                type="button"
                onClick={() => setDropoff(point)}
                className="im-card im-elevate flex min-h-[64px] items-center gap-3 p-3 text-left"
                title={`Set ${point.address} as destination`}
                data-testid={`saved-${tag.toLowerCase()}`}
              >
                <span aria-hidden="true" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[rgb(225_29_104/0.12)] text-[var(--im-bright)]">
                  <Icon name={tag === 'Home' ? 'home' : 'wallet'} size={18} />
                </span>
                <span className="min-w-0">
                  <span className="block font-semibold text-[var(--im-text)]">{tag}</span>
                  <span className="block truncate text-xs text-[var(--im-text-muted)]">{place.address}</span>
                </span>
              </button>
            ))}
            {recentDestinations.slice(0, Math.max(0, 4 - savedSuggestions.length)).map((r) => (
              <button
                key={r.address}
                type="button"
                onClick={() => setDropoff(r)}
                className="im-card im-elevate flex min-h-[64px] items-center gap-3 p-3 text-left"
                title={`Set ${r.address} as destination`}
                data-testid="recent-place"
              >
                <span aria-hidden="true" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[var(--im-elevated)] text-[var(--im-text-muted)]">
                  <Icon name="clock" size={18} />
                </span>
                <span className="min-w-0">
                  <span className="block font-semibold text-[var(--im-text)]">Recent</span>
                  <span className="block truncate text-xs text-[var(--im-text-muted)]">{r.address}</span>
                </span>
              </button>
            ))}
          </div>
        ) : (
          <div className="im-card flex flex-col items-center gap-2 px-6 py-8 text-center sm:flex-row sm:text-left">
            <span aria-hidden="true" className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-[rgb(225_29_104/0.12)] text-[var(--im-bright)]">
              <Icon name="map-pin" size={22} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-semibold">No saved places yet</p>
              <p className="text-sm text-[var(--im-text-muted)]">Save Home and Work to book your usual trips in one tap.</p>
            </div>
            <Link to="/saved-places" className="im-btn im-btn-primary !px-4 !py-2 !text-sm shrink-0">
              Add a place
            </Link>
          </div>
        )}
      </section>

      {/* Recent rides */}
      <section data-testid="recent-rides" className="im-fade-up">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-lg font-semibold">Recent rides</h3>
          <Link to="/rides" className="im-nav-item !py-1 !px-2 text-sm" style={{ color: 'var(--im-brand-600)' }}>
            View all <Icon name="chevron-right" size={15} />
          </Link>
        </div>
        {isLoading ? (
          <SkeletonList rows={2} />
        ) : ridesError ? (
          <div className="im-card flex flex-col items-center gap-3 px-6 py-10 text-center" role="alert">
            <span aria-hidden="true" className="grid h-12 w-12 place-items-center rounded-full bg-rose-50 text-rose-500">
              <Icon name="alert" size={22} />
            </span>
            <p className="text-sm text-[var(--im-text-secondary)]">We couldn't load your rides just now. Check your connection and try again.</p>
            <button type="button" className="im-btn im-btn-secondary !px-4 !py-2 !text-sm" onClick={() => refetchRides()}>
              Retry
            </button>
          </div>
        ) : !rides?.content?.length ? (
          <EmptyState
            icon="car"
            title="You haven't taken a ride yet."
            body="Book your first trip with IntelliMove — pick a destination above and we'll match you with a nearby driver."
          />
        ) : (
          <ul className="grid gap-3 md:grid-cols-2">
            {rides.content.slice(0, 4).map((ride) => (
              <li key={ride.id}>
                <Link
                  to={`/rides/${ride.id}`}
                  className="im-card im-elevate im-card-pad group flex items-center gap-4 transition-shadow hover:shadow-md"
                  aria-label={`View details for ride from ${ride.pickupAddress ?? 'pickup'} to ${ride.dropoffAddress ?? 'destination'}`}
                >
                  {/* Mini route glyph */}
                  <span aria-hidden="true" className="flex shrink-0 flex-col items-center justify-center gap-1 self-stretch py-1">
                    <span className="h-2.5 w-2.5 rounded-full ring-4 ring-[rgb(244_63_127/0.18)]" style={{ background: '#F43F7F' }} />
                    <span className="w-px flex-1 border-l border-dashed border-[var(--im-border)]" />
                    <span className="h-2.5 w-2.5 rounded-full ring-4 ring-[rgb(225_29_104/0.18)]" style={{ background: '#E11D68' }} />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-semibold text-[var(--im-text)]">
                      {ride.pickupAddress ?? 'Pickup'} → {ride.dropoffAddress ?? 'Destination'}
                    </span>
                    <span className="mt-0.5 block text-xs text-[var(--im-text-muted)]">
                      {new Date(ride.createdAt).toLocaleString([], {
                        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
                      })} · {titleCase(ride.rideType)}
                    </span>
                    <span className="mt-1.5 inline-flex items-center gap-2">
                      <StatusBadge status={ride.status} />
                      {ride.finalFare != null && (
                        <span className="text-sm font-bold text-[var(--im-text)]">{`$${ride.finalFare.toFixed(2)}`}</span>
                      )}
                    </span>
                  </span>
                  <span className="flex shrink-0 flex-col items-end gap-1 text-[var(--im-text-muted)] transition-colors group-hover:text-[var(--im-bright)]">
                    <span className="hidden text-xs font-medium sm:block">View details</span>
                    <Icon name="chevron-right" size={16} className="transition-transform duration-200 group-hover:translate-x-0.5" />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Help & safety — real existing pages only */}
      <section aria-label="Help and safety" className="im-fade-up mb-8">
        <h3 className="mb-3 text-lg font-semibold">Help &amp; safety</h3>
        <div className="grid gap-3 sm:grid-cols-3">
          <Link to="/help-center" className="im-card im-elevate flex min-h-[64px] items-center gap-3 p-4 text-left">
            <span aria-hidden="true" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[rgb(225_29_104/0.12)] text-[var(--im-bright)]">
              <Icon name="help" size={18} />
            </span>
            <span>
              <span className="block font-semibold text-[var(--im-text)]">Help Center</span>
              <span className="block text-xs text-[var(--im-text-muted)]">Answers to common questions</span>
            </span>
          </Link>
          <Link to="/safety" className="im-card im-elevate flex min-h-[64px] items-center gap-3 p-4 text-left">
            <span aria-hidden="true" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-600">
              <Icon name="shield" size={18} />
            </span>
            <span>
              <span className="block font-semibold text-[var(--im-text)]">Ride Safety</span>
              <span className="block text-xs text-[var(--im-text-muted)]">How every trip stays protected</span>
            </span>
          </Link>
          <Link to="/contact-support" className="im-card im-elevate flex min-h-[64px] items-center gap-3 p-4 text-left">
            <span aria-hidden="true" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[rgb(225_29_104/0.12)] text-[var(--im-bright)]">
              <Icon name="message" size={18} />
            </span>
            <span>
              <span className="block font-semibold text-[var(--im-text)]">Report an issue</span>
              <span className="block text-xs text-[var(--im-text-muted)]">Contact our support team</span>
            </span>
          </Link>
        </div>
      </section>

      {/* Mobile floating "Book a ride" action */}
      {!activeRide && (
        <button
          type="button"
          onClick={scrollToBooking}
          data-testid="fab-book"
          aria-label="Book a ride"
          className="im-fab"
        >
          <Icon name="car" size={18} /> Book a ride
        </button>
      )}

      <ToastStack toasts={toasts} onDismiss={dismiss} />
    </AppShell>
  );
}
