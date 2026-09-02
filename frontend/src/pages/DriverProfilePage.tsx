import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import AppShell from '../components/AppShell';
import { SkeletonList } from '../components/ui';
import { useGetDriver, useGetUser } from '../api/hooks';

export default function DriverProfilePage() {
  const { user, logout } = useAuth();
  const { data: driver, isLoading } = useGetDriver(user?.id ?? '');
  const { data: profile } = useGetUser(user?.id ?? '');

  return (
    <AppShell>
      <div className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight">Driver profile</h1>
        <p className="text-sm text-[var(--im-text-muted)]">Your account and vehicle details on file with IntelliMove.</p>
      </div>

      {isLoading && <SkeletonList rows={3} />}

      {!isLoading && (
        <div className="grid gap-6 lg:grid-cols-2">
          {/* Account */}
          <section className="im-card im-card-pad im-fade-up" aria-labelledby="acct-h">
            <div className="mb-4 flex items-center gap-4">
              <span aria-hidden="true" className="grid h-14 w-14 place-items-center rounded-full text-[#FFFFFF] text-xl font-bold"
                style={{ background: 'linear-gradient(135deg,#E11D68,#BE185D)' }}>
                {(profile?.firstName?.[0] ?? user?.firstName?.[0] ?? 'D').toUpperCase()}
              </span>
              <div>
                <h2 id="acct-h" className="font-semibold text-lg">
                  {profile ? `${profile.firstName} ${profile.lastName}` : (user?.firstName ?? 'Driver')}
                </h2>
                <p className="text-sm text-[var(--im-text-muted)]">{profile?.email ?? user?.email}</p>
              </div>
            </div>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-[var(--im-text-muted)]">Phone</dt>
                <dd className="font-medium text-[var(--im-text)]">{profile?.phoneNumber || 'Not provided'}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-[var(--im-text-muted)]">Member since</dt>
                <dd className="font-medium text-[var(--im-text)]">
                  {profile ? new Date(profile.createdAt).toLocaleDateString() : '—'}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-[var(--im-text-muted)]">Verification</dt>
                <dd>
                  {driver?.verified ? (
                    <span className="im-badge" style={{ background: 'rgba(34, 197, 94, 0.16)', color: '#86EFAC' }}>
                      <Icon name="shield" size={12} /> Verified
                    </span>
                  ) : (
                    <span className="im-badge" style={{ background: 'rgba(245, 158, 11, 0.18)', color: '#FCD34D' }}>Pending</span>
                  )}
                </dd>
              </div>
            </dl>
          </section>

          {/* Vehicle */}
          <section className="im-card im-card-pad im-fade-up" aria-labelledby="veh-h">
            <h2 id="veh-h" className="mb-4 flex items-center gap-2 font-semibold text-lg">
              <Icon name="car" size={18} /> Vehicle
            </h2>
            {driver ? (
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between gap-4">
                  <dt className="text-[var(--im-text-muted)]">Vehicle</dt>
                  <dd className="font-medium text-[var(--im-text)]">
                    {driver.vehicleYear} {driver.vehicleMake} {driver.vehicleModel}
                  </dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-[var(--im-text-muted)]">Color</dt>
                  <dd className="font-medium text-[var(--im-text)] capitalize">{driver.vehicleColor}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-[var(--im-text-muted)]">License plate</dt>
                  <dd className="font-mono font-semibold text-[var(--im-text)]">{driver.licensePlate}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-[var(--im-text-muted)]">License number</dt>
                  <dd className="font-mono text-[var(--im-text)]">{driver.licenseNumber}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-[var(--im-text-muted)]">Rating</dt>
                  <dd className="inline-flex items-center gap-1 font-semibold text-amber-500">
                    <Icon name="star" size={14} /> {driver.rating.toFixed(1)}
                    <span className="ml-1 text-xs font-normal text-[var(--im-text-muted)]">({driver.totalTrips} trips)</span>
                  </dd>
                </div>
              </dl>
            ) : (
              <p className="text-sm text-[var(--im-text-muted)]">Vehicle details unavailable.</p>
            )}
          </section>

          {/* Security */}
          <section className="im-card im-card-pad lg:col-span-2" aria-labelledby="sec-h">
            <h2 id="sec-h" className="mb-3 flex items-center gap-2 font-semibold text-lg">
              <Icon name="shield" size={18} /> Security
            </h2>
            <p className="mb-4 text-sm text-[var(--im-text-muted)]">
              Sessions are protected with JWT access &amp; refresh tokens. Logging out invalidates your local session.
            </p>
            <button className="im-btn im-btn-danger" onClick={logout} data-testid="logout">
              <Icon name="logout" size={16} /> Log out
            </button>
          </section>
        </div>
      )}
    </AppShell>
  );
}
