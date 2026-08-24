import { useMutation, useQuery } from '@tanstack/react-query';
import client from './client';
import type { AuthResponse, ApiResponse, PagedResponse, Ride, Driver, User } from './types';

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
