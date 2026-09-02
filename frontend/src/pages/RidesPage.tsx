import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import AppShell from '../components/AppShell';
import { EmptyState, SkeletonList, StatusBadge, ErrorState } from '../components/ui';
import Icon from '../components/Icon';
import { useAuth } from '../context/AuthContext';
import { useGetCustomerRides } from '../api/hooks';
import type { Ride } from '../api/types';

const titleCase = (t: string) => t.charAt(0) + t.slice(1).toLowerCase();

const TABS = ['Active', 'Completed', 'Cancelled'] as const;

function isTabStatus(tab: (typeof TABS)[number], status: string) {
  if (tab === 'Active')
    return ['REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING', 'TRIP_STARTED'].includes(status);
  if (tab === 'Completed') return status === 'TRIP_COMPLETED';
  return status === 'CANCELLED';
}

export default function RidesPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [tab, setTab] = useState<(typeof TABS)[number]>('Active');
  const { data: rides, isLoading, isError } = useGetCustomerRides(user?.id ?? '');

  if (isError) {
    return (
      <AppShell title="My Rides">
        <ErrorState message="We couldn't load your rides. Please try again later." />
      </AppShell>
    );
  }

  const filtered = (rides?.content ?? []).filter((r) => isTabStatus(tab, r.status));

  return (
    <AppShell title="My Rides">
      <div className="mb-5 flex flex-wrap gap-2" role="tablist" aria-label="Ride filter">
        {TABS.map((t) => {
          const count = (rides?.content ?? []).filter((r) => isTabStatus(t, r.status)).length;
          return (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`im-btn !py-1.5 !px-4 !text-sm ${tab === t ? 'im-btn-primary' : 'im-btn-secondary'}`}
            >
              {t} <span className="opacity-70">({count})</span>
            </button>
          );
        })}
      </div>

      {isLoading ? (
        <SkeletonList rows={3} />
      ) : filtered.length === 0 ? (
        tab === 'Active' ? (
          <EmptyState
            icon="route"
            title="No trips right now."
            body="When you book a ride it will appear here with live status updates."
            action={
              <button className="im-btn im-btn-primary mt-2" onClick={() => navigate('/dashboard')}>
                Book a ride
              </button>
            }
          />
        ) : (
          <EmptyState
            icon="clock"
            title={`No ${tab.toLowerCase()} rides yet.`}
            body="Your ride history will build up here after your first completed trip."
          />
        )
      ) : (
        <ul className="space-y-3">
          {filtered.map((ride) => (
            <RideRow key={ride.id} ride={ride} onOpen={() => navigate(`/rides/${ride.id}`)} />
          ))}
        </ul>
      )}
    </AppShell>
  );
}

function RideRow({ ride, onOpen }: { ride: Ride; onOpen: () => void }) {
  return (
    <li>
      <button
        onClick={onOpen}
        className="im-card im-card-pad flex w-full items-center gap-4 text-left hover:shadow-md transition-shadow"
        data-testid="ride-row"
      >
        <span aria-hidden="true" className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--im-elevated)] text-[var(--im-text-muted)]">
          <Icon name="route" size={19} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium text-[var(--im-text)]">
            {ride.pickupAddress ?? 'Pickup'} → {ride.dropoffAddress ?? 'Destination'}
          </span>
          <span className="block text-xs text-[var(--im-text-muted)]">
            {new Date(ride.createdAt).toLocaleString()} · {titleCase(ride.rideType)}
            {ride.distanceKm ? ` · ${Number(ride.distanceKm).toFixed(1)} km` : ''}
          </span>
        </span>
        <span className="flex shrink-0 flex-col items-end gap-1">
          {ride.finalFare != null ? (
            <span className="text-sm font-semibold text-[var(--im-text)]">${ride.finalFare.toFixed(2)}</span>
          ) : ride.estimatedFare != null ? (
            <span className="text-sm font-medium text-[var(--im-text-muted)]">est. ${ride.estimatedFare.toFixed(2)}</span>
          ) : null}
          <StatusBadge status={ride.status} />
        </span>
        <Icon name="chevron-right" size={16} className="shrink-0 text-[var(--im-text-muted)]" />
      </button>
    </li>
  );
}
