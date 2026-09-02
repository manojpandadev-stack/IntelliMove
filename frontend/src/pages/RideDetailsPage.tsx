import { useParams, Link } from 'react-router-dom';
import AppShell from '../components/AppShell';
import RideMap from '../components/RideMap';
import { SkeletonList, StatusBadge, ErrorState } from '../components/ui';
import Icon from '../components/Icon';
import { useGetRide, useGetDriverById, useGetUser } from '../api/hooks';

const titleCase = (t: string) => t.charAt(0) + t.slice(1).toLowerCase();

function Row({ label, value, testid }: { label: string; value?: React.ReactNode; testid?: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5" data-testid={testid}>
      <span className="text-sm text-[var(--im-text-muted)]">{label}</span>
      <span className="text-right text-sm font-medium">{value ?? '—'}</span>
    </div>
  );
}

export default function RideDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const { data: ride, isLoading, isError } = useGetRide(id ?? '');
  const { data: driver } = useGetDriverById(ride?.driverId ?? '');
  const { data: driverUser } = useGetUser(driver?.userId ?? '');

  if (isError) {
    return (
      <AppShell title="Ride details">
        <ErrorState message="We couldn't load this ride. It may not exist or belong to another account." />
      </AppShell>
    );
  }

  if (isLoading || !ride) {
    return (
      <AppShell title="Ride details">
        <SkeletonList rows={2} />
      </AppShell>
    );
  }

  const fare = ride.finalFare ?? ride.estimatedFare;

  return (
    <AppShell title="Ride details">
      <Link to="/rides" className="im-nav-item mb-3 w-fit !px-0" style={{ color: 'var(--im-brand-600)' }}>
        <Icon name="chevron-right" size={15} style={{ transform: 'rotate(180deg)' }} /> Back to My Rides
      </Link>

      <div className="grid gap-5 lg:grid-cols-[1fr_360px]">
        <div className="im-card im-fade-up p-5" data-testid="ride-details">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold">Trip #{ride.id.slice(0, 8)}</h2>
            <StatusBadge status={ride.status} />
          </div>

          <div className="mb-5 space-y-3 rounded-xl bg-[var(--im-canvas)] p-4">
            <div className="flex gap-3">
              <span aria-hidden="true" className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: 'var(--im-brand-600)' }} />
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--im-text-muted)]">Pickup</p>
                <p className="text-sm font-medium">{ride.pickupAddress ?? `${ride.pickupLatitude.toFixed(5)}, ${ride.pickupLongitude.toFixed(5)}`}</p>
              </div>
            </div>
            <div className="ml-1 h-4 border-l-2 border-dashed border-[var(--im-border)]" aria-hidden="true" />
            <div className="flex gap-3">
              <span aria-hidden="true" className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: '#F43F7F' }} />
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--im-text-muted)]">Destination</p>
                <p className="text-sm font-medium">{ride.dropoffAddress ?? `${ride.dropoffLatitude.toFixed(5)}, ${ride.dropoffLongitude.toFixed(5)}`}</p>
              </div>
            </div>
          </div>

          <RideMap
            height={220}
            points={[
              { lat: ride.pickupLatitude, lng: ride.pickupLongitude, label: 'Pickup', kind: 'pickup' },
              { lat: ride.dropoffLatitude, lng: ride.dropoffLongitude, label: 'Dropoff', kind: 'dropoff' },
            ]}
          />

          <dl className="mt-4 divide-y divide-[var(--im-border)]">
            <Row label="Requested at" value={new Date(ride.createdAt).toLocaleString()} testid="detail-created" />
            <Row label="Distance" value={`${Number(ride.distanceKm).toFixed(1)} km`} />
            <Row label="Duration" value={Number(ride.durationMinutes) > 0 ? `${Math.round(Number(ride.durationMinutes))} min` : undefined} />
            <Row label="Ride type" value={titleCase(ride.rideType)} />
          </dl>
        </div>

        <div className="space-y-5">
          {/* Driver card */}
          <div className="im-card p-5">
            <h3 className="mb-3 font-semibold">Driver</h3>
            {!ride.driverId ? (
              <p className="text-sm text-[var(--im-text-muted)]">A driver has not been assigned to this trip.</p>
            ) : driver ? (
              <div className="flex items-center gap-4">
                <span
                  aria-hidden="true"
                  className="grid h-12 w-12 shrink-0 place-items-center rounded-full text-lg font-bold text-[#FFFFFF]"
                  style={{ background: 'linear-gradient(135deg,#E11D68,#BE185D)' }}
                >
                  {(driverUser?.firstName?.[0] ?? 'D').toUpperCase()}
                </span>
                <div className="min-w-0">
                  <p className="truncate font-semibold" data-testid="driver-name">
                    {driverUser ? `${driverUser.firstName} ${driverUser.lastName}` : `#${ride.driverId.slice(0, 8)}`}
                  </p>
                  <p className="truncate text-xs text-[var(--im-text-muted)]" data-testid="vehicle-info">
                    {driver.vehicleColor} {driver.vehicleMake} {driver.vehicleModel}
                  </p>
                  <p className="mt-0.5 flex items-center gap-2 text-xs">
                    <span className="rounded bg-[var(--im-elevated)] px-1.5 py-0.5 font-mono font-bold text-[var(--im-text-secondary)]" data-testid="license-plate">
                      {driver.licensePlate}
                    </span>
                    <span className="inline-flex items-center gap-1 font-semibold text-amber-500">
                      <Icon name="star" size={12} /> {Number(driver.rating).toFixed(1)}
                    </span>
                  </p>
                </div>
              </div>
            ) : (
              <SkeletonList rows={1} />
            )}
          </div>

          {/* Fare / receipt */}
          <div className="im-card p-5" data-testid="fare-breakdown">
            <h3 className="mb-2 font-semibold">Fare</h3>
            <p className="mb-3 text-3xl font-bold tracking-tight" data-testid="fare-total">
              ${fare != null ? Number(fare).toFixed(2) : '—'}
              <span className="ml-1 text-sm font-normal text-[var(--im-text-muted)]">{ride.currency ?? 'USD'}</span>
            </p>
            <dl className="divide-y divide-[var(--im-border)]">
              <Row label="Type" value={titleCase(ride.rideType)} />
              {Number(ride.distanceKm) > 0 && (
                <Row label={`Distance (${Number(ride.distanceKm).toFixed(1)} km)`} value="included in fare" />
              )}
              <Row
                label={ride.finalFare != null ? 'Final fare' : 'Estimated fare'}
                value={<StatusBadge status={ride.status === 'TRIP_COMPLETED' ? 'COMPLETED' : ride.status} />}
                testid="detail-fare-status"
              />
            </dl>
          </div>
        </div>
      </div>
    </AppShell>
  );
}

