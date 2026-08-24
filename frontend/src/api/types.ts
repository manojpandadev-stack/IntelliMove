export interface UserInfo {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Ride {
  id: string;
  customerId: string;
  driverId?: string;
  status: string;
  rideType: string;
  pickupLatitude: number;
  pickupLongitude: number;
  pickupAddress?: string;
  dropoffLatitude: number;
  dropoffLongitude: number;
  dropoffAddress?: string;
  estimatedFare?: number;
  finalFare?: number;
  currency?: string;
  distanceKm: number;
  durationMinutes: number;
  createdAt: string;
}

export interface Driver {
  id: string;
  userId: string;
  licenseNumber: string;
  status: string;
  vehicleMake: string;
  vehicleModel: string;
  vehicleYear: number;
  vehicleColor: string;
  licensePlate: string;
  vehicleType: string;
  rating: number;
  totalTrips: number;
  verified: boolean;
  available: boolean;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}
