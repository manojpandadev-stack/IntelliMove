import { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import AppShell from '../components/AppShell';
import { EmptyState, SkeletonList, StatusBadge } from '../components/ui';
import { useGetDriver, useGetDriverRides } from '../api/hooks';
import type { Ride } from '../api/types';

const DAY = 86_400_000;

function bucket(rides: Ride[], fromMs: number) {
  const from = Date.now() - fromMs;
  const completed = rides.filter(
    (r) => r.status === 'TRIP_COMPLETED' && r.finalFare != null && new Date(r.createdAt).getTime() >= from
  );
  const gross = completed.reduce((sum, r) => sum + (r.finalFare ?? 0), 0);
  return { trips: completed.length, gross };
}

export default function DriverEarningsPage() {
  const { user } = useAuth();
  const { data: driver, isLoading: driverLoading } = useGetDriver(user?.id ?? '');
  const { data: ridesPage, isLoading: ridesLoading } = useGetDriverRides(driver?.id ?? '', 0);

  // Real earnings derived from the driver's actual completed rides (finalFare).
  const rides = useMemo(() => ridesPage?.content ?? [], [ridesPage]);
  const today = useMemo(() => bucket(rides, DAY), [rides]);
  const week = useMemo(() => bucket(rides, 7 * DAY), [rides]);
  const month = useMemo(() => bucket(rides, 30 * DAY), [rides]);

  const cards = [
    { label: 'Today', icon: 'clock' as const, ...today },
    { label: 'This week', icon: 'trend' as const, ...week },
    { label: 'This month', icon: 'calendar' as const, ...month },
  ];

  return (
    <AppShell>
      <div className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight">Earnings</h1>
        <p className="text-sm text-[var(--im-text-muted)]">
          Calculated from your completed trips. Platform fees are applied by operators where configured.
        </p>
      </div>

      {(driverLoading || ridesLoading) && <SkeletonList rows={3} />}

      {!driverLoading && !ridesLoading && (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-3" data-testid="earnings-summary">
            {cards.map((c) => (
              <div key={c.label} className="im-card im-card-pad im-fade-up">
                <div className="mb-2 flex items-center gap-2 text-sm font-medium text-[var(--im-text-muted)]">
                  <Icon name={c.icon === 'calendar' ? 'clock' : c.icon} size={16} /> {c.label}
                </div>
                <p className="text-2xl font-bold text-[var(--im-text)]" data-testid={`earnings-${c.label.replace(/\s/g, '-').toLowerCase()}`}>
                  ${c.gross.toFixed(2)}
                </p>
                <p className="mt-1 text-xs text-[var(--im-text-muted)]">{c.trips} trip{c.trips === 1 ? '' : 's'} completed</p>
              </div>
            ))}
          </div>

          <section>
            <h2 className="mb-3 font-semibold text-lg">Completed trips</h2>
            {rides.filter((r) => r.status === 'TRIP_COMPLETED').length === 0 ? (
              <EmptyState
                icon="wallet"
                title="No earnings yet."
                body="Go online, accept your first ride and your completed trip fares will appear here."
              />
            ) : (
              <ul className="space-y-3">
                {rides
                  .filter((r) => r.status === 'TRIP_COMPLETED')
                  .slice(0, 20)
                  .map((r) => (
                    <li key={r.id} className="im-card im-card-pad flex items-center gap-4">
                      <span aria-hidden="true" className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-600">
                        <Icon name="check-circle" size={18} />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium text-[var(--im-text)]">
                          {r.pickupAddress ?? 'Pickup'} → {r.dropoffAddress ?? 'Destination'}
                        </span>
                        <span className="block text-xs text-[var(--im-text-muted)]">{new Date(r.createdAt).toLocaleString()}</span>
                      </span>
                      <span className="shrink-0 text-right">
                        <span className="block font-semibold text-[var(--im-text)]">${(r.finalFare ?? 0).toFixed(2)}</span>
                        <StatusBadge status={r.status} />
                      </span>
                    </li>
                  ))}
              </ul>
            )}
          </section>
        </>
      )}
    </AppShell>
  );
}
