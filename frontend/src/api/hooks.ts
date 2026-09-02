import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client from './client';
import type {
  AuthResponse,
  ApiResponse,
  PagedResponse,
  Ride,
  Driver,
  User,
  Notification,
  Payment,
      FareEstimate,
  SavedPlace,
  UserPreferences,
  SupportTicket,
  RideEta,
} from './types';

// Auth hooks
export const useLogin = () =>
  useMutation({
    mutationFn: (data: { email: string; password: string }) =>
      client.post<ApiResponse<AuthResponse>>('/api/v1/auth/login', data).then((r) => r.data.data),
  });

export const useRegister = () =>
  useMutation({
    mutationFn: (data: { email: string; password: string; firstName: string; lastName: string }) =>
      client.post<ApiResponse<AuthResponse>>('/api/v1/auth/register', data).then((r) => r.data.data),
  });

// Ride hooks
export const useRequestRide = () =>
  useMutation({
    mutationFn: (data: {
      rideType: string;
      pickupLatitude: number;
      pickupLongitude: number;
      dropoffLatitude: number;
      dropoffLongitude: number;
      pickupAddress?: string;
      dropoffAddress?: string;
    }) => client.post<ApiResponse<Ride>>('/api/v1/rides', data).then((r) => r.data.data),
  });

export const useGetRide = (id: string) =>
  useQuery({
    queryKey: ['ride', id],
    queryFn: () => client.get<ApiResponse<Ride>>(`/api/v1/rides/${id}`).then((r) => r.data.data),
    enabled: !!id,
  });

export const useGetCustomerRides = (customerId: string, page = 0) =>
  useQuery({
    queryKey: ['customerRides', customerId, page],
    queryFn: () =>
      client
        .get<ApiResponse<PagedResponse<Ride>>>(`/api/v1/rides/customer/${customerId}`, {
          params: { page, size: 20 },
        })
        .then((r) => r.data.data),
    enabled: !!customerId,
    // Light polling so live ride status changes (matching, driver assigned,
    // trip started/completed) appear without manual refresh.
    refetchInterval: 5000,
  });

export const useGetAllRides = (page = 0) =>
  useQuery({
    queryKey: ['allRides', page],
    queryFn: () =>
      client
        .get<ApiResponse<PagedResponse<Ride>>>('/api/v1/rides', { params: { page, size: 20 } })
        .then((r) => r.data.data),
  });

export const useCancelRide = () =>
  useMutation({
    mutationFn: ({ rideId, reason }: { rideId: string; reason: string }) =>
      client
        .post<ApiResponse<Ride>>(`/api/v1/rides/${rideId}/cancel`, { reason })
        .then((r) => r.data.data),
  });

// Driver hooks
export const useGetDriver = (userId: string) =>
  useQuery({
    queryKey: ['driver', userId],
    queryFn: () => client.get<ApiResponse<Driver>>(`/api/v1/drivers/user/${userId}`).then((r) => r.data.data),
    enabled: !!userId,
  });

export const useGetAvailableDrivers = () =>
  useQuery({
    queryKey: ['availableDrivers'],
    queryFn: () =>
      client.get<ApiResponse<Driver[]>>('/api/v1/drivers/available').then((r) => r.data.data),
  });

export const useUpdateDriverStatus = () =>
  useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      client.patch<ApiResponse<Driver>>(`/api/v1/drivers/${id}/status`, { status }).then((r) => r.data.data),
  });

/** Driver lifecycle actions on an assigned ride — identity comes from the JWT. */
export const useDriverRideAction = () =>
  useMutation({
    mutationFn: ({ rideId, action }: { rideId: string; action: 'accept' | 'start' | 'complete' }) =>
      client.post<ApiResponse<Ride>>(`/api/v1/rides/${rideId}/${action}`).then((r) => r.data.data),
  });

/**
 * Driver rejects an incoming ride request (POST /api/v1/rides/:id/reject).
 * The ride returns to REQUESTED so matching can offer it to another driver.
 */
export const useRejectRide = () =>
  useMutation({
    mutationFn: ({ rideId }: { rideId: string }) =>
      client.post<ApiResponse<Ride>>(`/api/v1/rides/${rideId}/reject`).then((r) => r.data.data),
  });

// User hooks
export const useGetAllUsers = (page = 0) =>
  useQuery({
    queryKey: ['allUsers', page],
    queryFn: () =>
      client
        .get<ApiResponse<PagedResponse<User>>>('/api/v1/users', { params: { page, size: 20 } })
        .then((r) => r.data.data),
  });

// AI Ops
export const useAiQuery = () =>
  useMutation({
    mutationFn: (query: string) =>
      client
        .post<ApiResponse<{ analysis: string; toolResults: Record<string, unknown>; toolsUsed: string[] }>>(
          '/api/v1/ai/ops/query',
          { query },
          { headers: { 'X-Session-Id': 'admin-session' } }
        )
        .then((r) => r.data.data),
  });

// ─── Driver location reporting (Redis GEO contract: JWT user ID) ────

export const useUpdateDriverLocation = () =>
  useMutation({
    // `rideId` is an optional body field of UpdateLocationRequest; when present
    // the Location Service broadcasts this GPS update to the ride's WS subscribers.
    mutationFn: (coords: { latitude: number; longitude: number; rideId?: string }) =>
      client
        .post<ApiResponse<{ success: boolean }>>('/api/v1/location/update', coords)
        .then((r) => r.data.data),
  });

/** Live shape returned by GET /api/v1/location/driver/:driverId (Redis GEO). */
export interface DriverLiveLocation {
  driverId: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  metadata: Record<string, string>;
}

/**
 * Polling fallback for the rider's active-ride map: reads the assigned
 * driver's REAL current position from Redis GEO via the existing REST
 * endpoint. Used while the WebSocket is unavailable (or as an initial fix)
 * — never fabricates coordinates.
 */
export const useDriverLiveLocation = (
  driverId: string | null | undefined,
  enabled: boolean,
  pollIntervalMs: number | false,
) =>
  useQuery({
    queryKey: ['driverLocation', driverId],
    queryFn: () =>
      client
        .get<ApiResponse<DriverLiveLocation>>(`/api/v1/location/driver/${driverId}`)
        .then((r) => r.data.data),
    enabled: enabled && !!driverId,
    refetchInterval: pollIntervalMs,
    staleTime: 0,
  });

// ─── Fare estimation ────────────────────────────────────────────────

export const useFareEstimate = (
  params: { pickupLat: number; pickupLng: number; dropoffLat: number; dropoffLng: number } | null
) =>
  useQuery({
    queryKey: ['fareEstimate', params],
    queryFn: () =>
      client
        .get<ApiResponse<FareEstimate>>('/api/v1/rides/estimate', {
          params: {
            pickupLat: params!.pickupLat,
            pickupLng: params!.pickupLng,
            dropoffLat: params!.dropoffLat,
            dropoffLng: params!.dropoffLng,
          },
        })
        .then((r) => r.data.data),
    enabled: !!params,
    staleTime: 30_000,
  });

// ─── Payments ────────────────────────────────────────────────────────

/** Payment record for a ride (existing GET /api/v1/payments/ride/:id). */
export const useRidePayment = (rideId: string) =>
  useQuery({
    queryKey: ['ridePayment', rideId],
    queryFn: () => client.get<ApiResponse<Payment>>(`/api/v1/payments/ride/${rideId}`).then((r) => r.data.data),
    enabled: !!rideId,
    staleTime: 30_000,
  });

export const usePayments = (page = 0) =>
  useQuery({
    queryKey: ['payments', page],
    queryFn: () =>
      client.get<ApiResponse<PagedResponse<Payment>>>('/api/v1/payments', { params: { page, size: 20 } }).then((r) => r.data.data),
  });

// ─── Notifications ──────────────────────────────────────────────────

export const useNotifications = (page = 0) =>
  useQuery({
    queryKey: ['notifications', page],
    queryFn: () =>
      client
        .get<ApiResponse<PagedResponse<Notification>>>('/api/v1/notifications', {
          params: { page, size: 20 },
        })
        .then((r) => r.data.data),
  });

export const useUnreadNotificationCount = () =>
  useQuery({
    queryKey: ['unreadNotifications'],
    queryFn: () =>
      client
        .get<ApiResponse<{ unreadCount: number }>>('/api/v1/notifications/unread-count')
        .then((r) => r.data.data.unreadCount),
    refetchInterval: 15_000,
  });

export const useMarkNotificationRead = () =>
  useMutation({
    mutationFn: (id: string) =>
      client.post(`/api/v1/notifications/${id}/read`).then((r) => r.data),
  });

export const useMarkAllNotificationsRead = () =>
  useMutation({
    mutationFn: () => client.post('/api/v1/notifications/read-all').then((r) => r.data),
  });

// ─── Payments ───────────────────────────────────────────────────────

export const useCustomerPayments = (customerId: string, page = 0) =>
  useQuery({
    queryKey: ['customerPayments', customerId, page],
    queryFn: () =>
      client
        .get<ApiResponse<PagedResponse<Payment>>>(`/api/v1/payments/customer/${customerId}`, {
          params: { page, size: 20 },
        })
        .then((r) => r.data.data),
    enabled: !!customerId,
  });

// ─── Driver detail / rating ─────────────────────────────────────────

export const useGetDriverById = (driverId: string) =>
  useQuery({
    queryKey: ['driverById', driverId],
    queryFn: () => client.get<ApiResponse<Driver>>(`/api/v1/drivers/${driverId}`).then((r) => r.data.data),
    enabled: !!driverId,
  });

export const useGetDriverRides = (driverId: string, page = 0) =>
  useQuery({
    queryKey: ['driverRides', driverId, page],
    queryFn: () =>
      client
        .get<ApiResponse<PagedResponse<Ride>>>(`/api/v1/rides/driver/${driverId}`, {
          params: { page, size: 50 },
        })
        .then((r) => r.data.data),
    enabled: !!driverId,
    // Light polling so incoming ride assignments appear without manual refresh
    // (complements the WebSocket channel; 5s keeps load negligible).
    refetchInterval: 5000,
  });

export const useRateDriver = () =>
  useMutation({
    mutationFn: ({ id, rating }: { id: string; rating: number }) =>
      client.post<ApiResponse<unknown>>(`/api/v1/drivers/${id}/rating`, { rating }).then((r) => r.data),
  });

// ─── Users / profile (re-added after driver hooks) ──────────────────

export const useGetUser = (userId: string) =>
  useQuery({
    queryKey: ['user', userId],
    queryFn: () => client.get<ApiResponse<User>>(`/api/v1/users/${userId}`).then((r) => r.data.data),
    enabled: !!userId,
  });

/**
 * Live pickup ETA — reads the Location Service endpoint that computes ETA
 * from the assigned driver's real Redis-GEO position + pickup coordinates.
 *
 * Polled (5s) only while the ride is in a pre-trip "driver heading to
 * pickup" state. The value is also refreshed opportunistically from the
 * WebSocket driver-location stream when it arrives, so the rider sees
 * near-real-time updates without additional HTTP traffic.
 */
export const useRideEta = (rideId: string) =>
  useQuery({
    queryKey: ['rideEta', rideId],
    queryFn: () =>
      client
        .get<ApiResponse<RideEta>>(`/api/v1/location/ride/${rideId}/eta`)
        .then((r) => r.data.data),
    enabled: !!rideId,
    refetchInterval: 5000,
    staleTime: 3000,
    retry: 1,
    gcTime: 0,
  });


export const useUpdateUser = () =>
  useMutation({
    mutationFn: (data: { id: string; firstName?: string; lastName?: string; phoneNumber?: string }) => {
      const { id, ...body } = data;
      return client.put<ApiResponse<User>>(`/api/v1/users/${id}`, body).then((r) => r.data.data);
    },
  });

// ─── Profile photo (server-stored avatar) ────────────────────────

/**
 * Fetches the authenticated user's avatar bytes (error ⇒ no photo set).
 * Also serves as the "has photo" signal for profile controls.
 */
export const useProfilePhoto = (userId?: string) =>
  useQuery({
    queryKey: ['profilePhoto', userId],
    enabled: !!userId,
    retry: false,
    staleTime: Infinity,
    queryFn: () =>
      client
        .get<Blob>(`/api/v1/users/${userId}/photo`, { responseType: 'blob' })
        .then((r) => r.data),
  });

export const useUploadProfilePhoto = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, file }: { userId: string; file: File }) => {
      const formData = new FormData();
      formData.append('file', file);
      // The axios instance defaults to application/json; explicitly unsetting
      // Content-Type lets the browser generate the multipart boundary.
      return client.request<void>({
        method: 'PUT',
        url: `/api/v1/users/${userId}/photo`,
        data: formData,
        headers: { 'Content-Type': undefined },
      });
    },
    // Refresh cached queries so header/menu avatars update immediately.
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['profilePhoto', vars.userId] });
      queryClient.invalidateQueries({ queryKey: ['user', vars.userId] });
    },
  });
};

export const useRemoveProfilePhoto = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => client.delete(`/api/v1/users/${userId}/photo`),
    onSuccess: (_data, userId) => {
      queryClient.invalidateQueries({ queryKey: ['profilePhoto', userId] });
      queryClient.invalidateQueries({ queryKey: ['user', userId] });
    },
  });
};

// ─── Saved places ───────────────────────────────────────────────

export const useSavedPlaces = () =>
  useQuery({
    queryKey: ['savedPlaces'],
    queryFn: () => client.get<ApiResponse<SavedPlace[]>>('/api/v1/users/saved-places').then((r) => r.data.data),
  });

export const useCreateSavedPlace = () =>
  useMutation({
    mutationFn: (place: Omit<SavedPlace, 'id'>) =>
      client.post<ApiResponse<SavedPlace>>('/api/v1/users/saved-places', place).then((r) => r.data.data),
  });

export const useUpdateSavedPlace = () =>
  useMutation({
    mutationFn: ({ id, ...place }: SavedPlace) =>
      client.put<ApiResponse<SavedPlace>>(`/api/v1/users/saved-places/${id}`, place).then((r) => r.data.data),
  });

export const useDeleteSavedPlace = () =>
  useMutation({
    mutationFn: (id: string) => client.delete(`/api/v1/users/saved-places/${id}`).then((r) => r.data),
  });

// ─── Preferences ────────────────────────────────────────────────

export const usePreferences = () =>
  useQuery({
    queryKey: ['preferences'],
    queryFn: () => client.get<ApiResponse<UserPreferences>>('/api/v1/users/preferences').then((r) => r.data.data),
  });

export const useUpdatePreferences = () =>
  useMutation({
    mutationFn: (prefs: Partial<UserPreferences>) =>
      client.put<ApiResponse<UserPreferences>>('/api/v1/users/preferences', prefs).then((r) => r.data.data),
  });

// ─── Support ────────────────────────────────────────────────────

export const useCreateSupportTicket = () =>
  useMutation({
    mutationFn: (ticket: { subject: string; category: string; message: string }) =>
      client.post<ApiResponse<SupportTicket>>('/api/v1/ai/support/tickets', ticket).then((r) => r.data.data),
  });

export const useSupportTickets = () =>
  useQuery({
    queryKey: ['supportTickets'],
    queryFn: () => client.get<ApiResponse<SupportTicket[]>>('/api/v1/ai/support/tickets').then((r) => r.data.data),
  });
