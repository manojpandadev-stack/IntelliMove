import { useAuth } from '../context/AuthContext';
import { useQueryClient } from '@tanstack/react-query';
import { useGetDriver, useUpdateDriverStatus } from '../api/hooks';

export default function DriverDashboard() {
  const { user, logout } = useAuth();
  const queryClient = useQueryClient();
  const { data: driver } = useGetDriver(user?.id || '');
  const updateStatus = useUpdateDriverStatus();

  const toggleOnline = () => {
    if (!driver) return;
    const newStatus = driver.status === 'OFFLINE' ? 'ONLINE' : 'OFFLINE';
    updateStatus.mutate(
      { id: driver.id, status: newStatus },
      { onSuccess: () => queryClient.invalidateQueries({ queryKey: ['driver', user?.id] }) },
    );
  };

  const statusColor = (s: string) => {
    switch (s) {
      case 'ONLINE': case 'AVAILABLE': return 'bg-green-100 text-green-800';
      case 'ON_TRIP': return 'bg-blue-100 text-blue-800';
      case 'SUSPENDED': return 'bg-red-100 text-red-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm px-6 py-4 flex justify-between items-center">
        <h1 className="text-xl font-bold text-gray-900">IntelliMove - Driver</h1>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-600">{user?.firstName}</span>
          <button onClick={logout} className="text-sm text-red-600 hover:underline">Logout</button>
        </div>
      </nav>

      <div className="max-w-4xl mx-auto p-6 space-y-6">
        {/* Status Card */}
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold">Driver Status</h2>
              <span className={`inline-block mt-2 px-3 py-1 rounded-full text-sm font-medium ${statusColor(driver?.status || 'OFFLINE')}`}>
                {driver?.status || 'OFFLINE'}
              </span>
            </div>
            <button onClick={toggleOnline}
              className={`px-6 py-3 rounded-lg font-medium text-white ${
                driver?.status === 'OFFLINE' ? 'bg-green-600 hover:bg-green-700' : 'bg-red-600 hover:bg-red-700'
              }`}>
              {driver?.status === 'OFFLINE' ? 'Go Online' : 'Go Offline'}
            </button>
          </div>
        </div>

        {/* Vehicle Info */}
        {driver && (
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold mb-4">Vehicle</h2>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <div><span className="text-gray-500">Vehicle</span><p className="font-medium">{driver.vehicleYear} {driver.vehicleMake} {driver.vehicleModel}</p></div>
              <div><span className="text-gray-500">Color</span><p className="font-medium">{driver.vehicleColor}</p></div>
              <div><span className="text-gray-500">Plate</span><p className="font-medium">{driver.licensePlate}</p></div>
              <div><span className="text-gray-500">Type</span><p className="font-medium">{driver.vehicleType}</p></div>
            </div>
          </div>
        )}

        {/* Stats */}
        {driver && (
          <div className="grid grid-cols-3 gap-4">
            <div className="bg-white rounded-lg shadow p-4 text-center">
              <p className="text-2xl font-bold text-blue-600">{driver.rating.toFixed(1)}</p>
              <p className="text-sm text-gray-500">Rating</p>
            </div>
            <div className="bg-white rounded-lg shadow p-4 text-center">
              <p className="text-2xl font-bold text-green-600">{driver.totalTrips}</p>
              <p className="text-sm text-gray-500">Total Trips</p>
            </div>
            <div className="bg-white rounded-lg shadow p-4 text-center">
              <p className="text-2xl font-bold text-purple-600">{driver.verified ? '✓' : '✗'}</p>
              <p className="text-sm text-gray-500">Verified</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
