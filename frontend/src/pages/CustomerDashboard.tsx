import { useAuth } from '../context/AuthContext';
import { useGetCustomerRides, useRequestRide } from '../api/hooks';
import { useState } from 'react';

export default function CustomerDashboard() {
  const { user, logout } = useAuth();
  const [rideForm, setRideForm] = useState({
    rideType: 'ECONOMY',
    pickupLatitude: 40.7128,
    pickupLongitude: -74.006,
    dropoffLatitude: 40.7580,
    dropoffLongitude: -73.9855,
    pickupAddress: 'New York, NY',
    dropoffAddress: 'Times Square, NY',
  });

  const { data: rides, isLoading } = useGetCustomerRides(user?.id || '');
  const requestRide = useRequestRide();

  const handleRequest = async () => {
    try {
      await requestRide.mutateAsync(rideForm);
      alert('Ride requested successfully!');
    } catch {
      alert('Failed to request ride');
    }
  };

  const statusColor = (s: string) => {
    switch (s) {
      case 'TRIP_COMPLETED': return 'bg-green-100 text-green-800';
      case 'CANCELLED': return 'bg-red-100 text-red-800';
      case 'TRIP_STARTED': return 'bg-blue-100 text-blue-800';
      default: return 'bg-yellow-100 text-yellow-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm px-6 py-4 flex justify-between items-center">
        <h1 className="text-xl font-bold text-gray-900">IntelliMove - Rider</h1>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-600">{user?.firstName} {user?.lastName}</span>
          <button onClick={logout} className="text-sm text-red-600 hover:underline">Logout</button>
        </div>
      </nav>

      <div className="max-w-4xl mx-auto p-6 space-y-6">
        {/* Request Ride Card */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">Request a Ride</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">Ride Type</label>
              <select
                value={rideForm.rideType}
                onChange={(e) => setRideForm({ ...rideForm, rideType: e.target.value })}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              >
                <option value="ECONOMY">Economy</option>
                <option value="COMFORT">Comfort</option>
                <option value="PREMIUM">Premium</option>
                <option value="XL">XL</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Pickup Address</label>
              <input value={rideForm.pickupAddress}
                onChange={(e) => setRideForm({ ...rideForm, pickupAddress: e.target.value })}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Destination</label>
              <input value={rideForm.dropoffAddress}
                onChange={(e) => setRideForm({ ...rideForm, dropoffAddress: e.target.value })}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2" />
            </div>
          </div>
          <button onClick={handleRequest} disabled={requestRide.isPending}
            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 font-medium">
            {requestRide.isPending ? 'Requesting...' : 'Request Ride'}
          </button>
        </div>

        {/* Ride History */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">Ride History</h2>
          {isLoading ? <p className="text-gray-500">Loading...</p> :
            rides?.content?.length === 0 ? <p className="text-gray-500">No rides yet.</p> :
            <div className="space-y-3">
              {rides?.content.map((ride) => (
                <div key={ride.id} className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">{ride.pickupAddress || 'Pickup'} → {ride.dropoffAddress || 'Destination'}</p>
                    <p className="text-sm text-gray-500">{new Date(ride.createdAt).toLocaleString()}</p>
                  </div>
                  <div className="text-right">
                    <span className={`inline-block px-2 py-1 rounded text-xs font-medium ${statusColor(ride.status)}`}>
                      {ride.status}
                    </span>
                    {ride.finalFare && <p className="text-sm font-semibold mt-1">${ride.finalFare.toFixed(2)}</p>}
                  </div>
                </div>
              ))}
            </div>
          }
        </div>
      </div>
    </div>
  );
}
