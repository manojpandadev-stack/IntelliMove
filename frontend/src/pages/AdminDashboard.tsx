import { useState } from 'react';
import { useGetAllRides, useGetAllUsers, useGetAvailableDrivers, useAiQuery } from '../api/hooks';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';

type Tab = 'overview' | 'rides' | 'users' | 'drivers' | 'ai';

const TAB_ICONS: Record<Tab, 'activity' | 'route' | 'users' | 'car' | 'sparkles'> = {
  overview: 'activity',
  rides: 'route',
  users: 'users',
  drivers: 'car',
  ai: 'sparkles',
};

export default function AdminDashboard() {
  const [tab, setTab] = useState<Tab>('overview');
  const [aiQuestion, setAiQuestion] = useState('');

  const { data: rides, isLoading: ridesLoading } = useGetAllRides();
  const { data: users } = useGetAllUsers();
  const { data: drivers } = useGetAvailableDrivers();
  const aiMutation = useAiQuery();

  const handleAiQuery = () => {
    if (aiQuestion.trim()) aiMutation.mutateAsync(aiQuestion);
  };

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'rides', label: 'Rides' },
    { key: 'users', label: 'Users' },
    { key: 'drivers', label: 'Drivers' },
    { key: 'ai', label: 'AI Assistant' },
  ];

  return (
    <AppShell title="Operations">
      <div>
        {/* Tab bar */}
        <div className="mb-6 flex gap-1 overflow-x-auto border-b" style={{ borderColor: 'var(--im-border)' }} role="tablist">
          {tabs.map((t) => (
            <button
              key={t.key}
              role="tab"
              aria-selected={tab === t.key}
              onClick={() => setTab(t.key)}
              className={`im-nav-item ${tab === t.key ? 'active !rounded-b-none !border-b-2' : ''}`}
              style={tab === t.key ? { borderBottom: '2px solid var(--im-brand-600)', borderRadius: '10px 10px 0 0' } : undefined}
            >
              <Icon name={TAB_ICONS[t.key]} size={16} />
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'overview' && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="im-card im-card-pad">
              <p className="flex items-center gap-2 text-3xl font-bold text-[var(--im-bright)]">
                {ridesLoading ? <span className="im-skeleton h-8 w-14 inline-block" /> : (rides?.totalElements ?? 0)}
              </p>
              <p className="text-sm text-[var(--im-text-muted)]">Total Rides</p>
            </div>
            <div className="im-card im-card-pad">
              <p className="text-3xl font-bold text-emerald-600">{users?.totalElements ?? 0}</p>
              <p className="text-sm text-[var(--im-text-muted)]">Registered Users</p>
            </div>
            <div className="im-card im-card-pad">
              <p className="text-3xl font-bold text-violet-600">{drivers?.length ?? 0}</p>
              <p className="text-sm text-[var(--im-text-muted)]">Active Drivers</p>
            </div>
            <div className="im-card im-card-pad">
              <p className="flex items-center gap-2 text-lg font-bold text-emerald-600">
                <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full bg-emerald-500" /> Online
              </p>
              <p className="text-sm text-[var(--im-text-muted)]">System Status</p>
            </div>
          </div>
        )}

        {tab === 'rides' && (
          <div className="rounded-lg shadow overflow-hidden border border-[var(--im-border)] bg-[var(--im-surface)]">
            <table className="min-w-full divide-y divide-[var(--im-border)] text-sm">
              <thead className="bg-[var(--im-bg-alt)]">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Ride ID</th>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Status</th>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Type</th>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Fare</th>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--im-border)]">
                {rides?.content?.map((ride) => (
                  <tr key={ride.id} className="hover:bg-[var(--im-elevated)]">
                    <td className="px-4 py-3 font-mono text-xs">{ride.id.substring(0, 8)}...</td>
                    <td className="px-4 py-3"><span className="px-2 py-1 rounded text-xs bg-[var(--im-elevated)]">{ride.status}</span></td>
                    <td className="px-4 py-3">{ride.rideType}</td>
                    <td className="px-4 py-3">{ride.finalFare ? `$${ride.finalFare.toFixed(2)}` : ride.estimatedFare ? `~$${ride.estimatedFare.toFixed(2)}` : '-'}</td>
                    <td className="px-4 py-3 text-[var(--im-text-muted)]">{new Date(ride.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {tab === 'users' && (
          <div className="rounded-lg shadow overflow-hidden border border-[var(--im-border)] bg-[var(--im-surface)]">
            <table className="min-w-full divide-y divide-[var(--im-border)] text-sm">
              <thead className="bg-[var(--im-bg-alt)]">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Name</th>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Email</th>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Role</th>
                  <th className="px-4 py-3 text-left font-medium text-[var(--im-text-muted)]">Joined</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--im-border)]">
                {users?.content?.map((u) => (
                  <tr key={u.id} className="hover:bg-[var(--im-elevated)]">
                    <td className="px-4 py-3 font-medium">{u.firstName} {u.lastName}</td>
                    <td className="px-4 py-3 text-[var(--im-text-muted)]">{u.email}</td>
                    <td className="px-4 py-3"><span className="px-2 py-1 bg-[rgb(225_29_104/0.16)] text-[#FB7185] rounded text-xs">{u.role}</span></td>
                    <td className="px-4 py-3 text-[var(--im-text-muted)]">{new Date(u.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {tab === 'drivers' && (
          <div className="rounded-lg shadow p-6 border border-[var(--im-border)] bg-[var(--im-surface)]">
            <h2 className="text-lg font-semibold mb-4">Available Drivers</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {drivers?.map((d) => (
                <div key={d.id} className="border rounded-lg p-4">
                  <p className="font-medium">{d.vehicleYear} {d.vehicleMake} {d.vehicleModel}</p>
                  <p className="text-sm text-[var(--im-text-muted)]">{d.vehicleColor} • {d.licensePlate}</p>
                  <p className="text-sm mt-2">⭐ {d.rating.toFixed(1)} • {d.totalTrips} trips</p>
                  <span className={`inline-block mt-2 px-2 py-1 rounded text-xs ${
                    d.available ? 'bg-[rgb(34_197_94/0.16)] text-[#86EFAC]' : 'bg-[var(--im-elevated)] text-[var(--im-text-secondary)]'
                  }`}>{d.status}</span>
                </div>
              ))}
              {drivers?.length === 0 && <p className="text-[var(--im-text-muted)]">No active drivers</p>}
            </div>
          </div>
        )}

        {tab === 'ai' && (
          <div className="rounded-lg shadow p-6 border border-[var(--im-border)] bg-[var(--im-surface)]">
            <h2 className="text-lg font-semibold mb-4">AI Operations Assistant</h2>
            <div className="flex gap-2 mb-4">
              <input value={aiQuestion} onChange={(e) => setAiQuestion(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAiQuery()}
                placeholder="Ask about operations... (e.g., Why did cancellations increase today?)"
                className="flex-1 px-4 py-2 border border-[var(--im-input-border)] rounded-md focus:outline-none focus:ring-2 focus:ring-[var(--im-focus-ring)]" />
              <button onClick={handleAiQuery} disabled={aiMutation.isPending}
                className="px-6 py-2 bg-[var(--im-brand-600)] text-[#FFFFFF] rounded-md hover:bg-[var(--im-brand-700)] disabled:opacity-50">
                {aiMutation.isPending ? 'Thinking...' : 'Ask'}
              </button>
            </div>
            {aiMutation.data && (
              <div className="mt-4 p-4 bg-[var(--im-bg-alt)] rounded-lg">
                <pre className="whitespace-pre-wrap text-sm">{aiMutation.data.analysis}</pre>
              </div>
            )}
          </div>
        )}
      </div>
    </AppShell>
  );
}
