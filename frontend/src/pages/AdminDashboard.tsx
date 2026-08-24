import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useGetAllRides, useGetAllUsers, useGetAvailableDrivers, useAiQuery } from '../api/hooks';

type Tab = 'overview' | 'rides' | 'users' | 'drivers' | 'ai';

export default function AdminDashboard() {
  const { user, logout } = useAuth();
  const [tab, setTab] = useState<Tab>('overview');
  const [aiQuestion, setAiQuestion] = useState('');

  const { data: rides } = useGetAllRides();
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
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm px-6 py-4 flex justify-between items-center">
        <h1 className="text-xl font-bold text-gray-900">IntelliMove - Admin</h1>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-600">{user?.firstName} {user?.lastName}</span>
          <button onClick={logout} className="text-sm text-red-600 hover:underline">Logout</button>
        </div>
      </nav>

      <div className="max-w-7xl mx-auto p-6">
        {/* Tab bar */}
        <div className="flex gap-1 mb-6 border-b">
          {tabs.map((t) => (
            <button key={t.key} onClick={() => setTab(t.key)}
              className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
                tab === t.key ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}>{t.label}</button>
          ))}
        </div>

        {tab === 'overview' && (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="bg-white rounded-lg shadow p-4 text-center">
              <p className="text-3xl font-bold text-blue-600">{rides?.totalElements ?? 0}</p>
              <p className="text-sm text-gray-500">Total Rides</p>
            </div>
            <div className="bg-white rounded-lg shadow p-4 text-center">
              <p className="text-3xl font-bold text-green-600">{users?.totalElements ?? 0}</p>
              <p className="text-sm text-gray-500">Total Users</p>
            </div>
            <div className="bg-white rounded-lg shadow p-4 text-center">
              <p className="text-3xl font-bold text-purple-600">{drivers?.length ?? 0}</p>
              <p className="text-sm text-gray-500">Active Drivers</p>
            </div>
            <div className="bg-white rounded-lg shadow p-4 text-center">
              <p className="text-3xl font-bold text-orange-600">Online</p>
              <p className="text-sm text-gray-500">System Status</p>
            </div>
          </div>
        )}

        {tab === 'rides' && (
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Ride ID</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Status</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Type</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Fare</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {rides?.content?.map((ride) => (
                  <tr key={ride.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs">{ride.id.substring(0, 8)}...</td>
                    <td className="px-4 py-3"><span className="px-2 py-1 bg-gray-100 rounded text-xs">{ride.status}</span></td>
                    <td className="px-4 py-3">{ride.rideType}</td>
                    <td className="px-4 py-3">{ride.finalFare ? `$${ride.finalFare.toFixed(2)}` : ride.estimatedFare ? `~$${ride.estimatedFare.toFixed(2)}` : '-'}</td>
                    <td className="px-4 py-3 text-gray-500">{new Date(ride.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {tab === 'users' && (
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Name</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Email</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Role</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Joined</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {users?.content?.map((u) => (
                  <tr key={u.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium">{u.firstName} {u.lastName}</td>
                    <td className="px-4 py-3 text-gray-500">{u.email}</td>
                    <td className="px-4 py-3"><span className="px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs">{u.role}</span></td>
                    <td className="px-4 py-3 text-gray-500">{new Date(u.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {tab === 'drivers' && (
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold mb-4">Available Drivers</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {drivers?.map((d) => (
                <div key={d.id} className="border rounded-lg p-4">
                  <p className="font-medium">{d.vehicleYear} {d.vehicleMake} {d.vehicleModel}</p>
                  <p className="text-sm text-gray-500">{d.vehicleColor} • {d.licensePlate}</p>
                  <p className="text-sm mt-2">⭐ {d.rating.toFixed(1)} • {d.totalTrips} trips</p>
                  <span className={`inline-block mt-2 px-2 py-1 rounded text-xs ${
                    d.available ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
                  }`}>{d.status}</span>
                </div>
              ))}
              {drivers?.length === 0 && <p className="text-gray-500">No active drivers</p>}
            </div>
          </div>
        )}

        {tab === 'ai' && (
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold mb-4">AI Operations Assistant</h2>
            <div className="flex gap-2 mb-4">
              <input value={aiQuestion} onChange={(e) => setAiQuestion(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAiQuery()}
                placeholder="Ask about operations... (e.g., Why did cancellations increase today?)"
                className="flex-1 px-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500" />
              <button onClick={handleAiQuery} disabled={aiMutation.isPending}
                className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                {aiMutation.isPending ? 'Thinking...' : 'Ask'}
              </button>
            </div>
            {aiMutation.data && (
              <div className="mt-4 p-4 bg-gray-50 rounded-lg">
                <pre className="whitespace-pre-wrap text-sm">{aiMutation.data.analysis}</pre>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
