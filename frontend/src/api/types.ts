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
  driverAssignedAt?: string;
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
  /** Server-stored avatar pointer (e.g. /api/v1/users/:id/photo?v=…), null when unset. */
  profileImageUrl?: string | null;
  createdAt: string;
}

/* ─── Notifications ─────────────────────────────────────────────── */

export interface Notification {
  id: string;
  recipientId: string;
  title: string;
  message: string;
  channel?: string;
  type?: string;
  read: boolean;
  createdAt: string;
  readAt?: string;
}

/* ─── Payments ──────────────────────────────────────────────────── */

export interface Payment {
  id: string;
  rideId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: 'INITIATED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REFUNDED' | string;
  method?: string;
  failureReason?: string;
  createdAt: string;
  completedAt?: string;
}

/* ─── Fare estimation ───────────────────────────────────────────── */

export interface RideOptionEstimate {
  rideType: 'ECONOMY' | 'COMFORT' | 'PREMIUM' | 'XL';
  estimatedFare: number;
  etaMinutes: number;
  capacity: number;
  /** Category description from the pricing engine (additive, may be absent on older backends). */
  description?: string;
  /** Demand multiplier actually applied to estimatedFare; 1.0 means no surge. */
  surgeMultiplier?: number;
}

export interface FareEstimate {
  distanceKm: number;
  estimatedMinutes: number;
  currency: string;
  options: RideOptionEstimate[];
}

/* ─── Live pickup ETA ────────────────────────────────────────────── */

/** Live pickup ETA from the Location Service, derived from real driver coordinates. */
export interface RideEta {
  etaMinutes: number;
  distanceKm: number;
  calculatedAt: string;
  source: string;
}

/* ─── Saved places ─────────────────────────────────────────────── */

export interface SavedPlace {
  id: string;
  userId: string;
  label: string;
  address: string;
  latitude: number;
  longitude: number;
  type: 'HOME' | 'WORK' | 'OTHER';
}

/* ─── Preferences ──────────────────────────────────────────────── */

export interface UserPreferences {
  id: string;
  userId: string;
  emailNotifications: boolean;
  smsNotifications: boolean;
  pushNotifications: boolean;
  darkMode: boolean;
  currency: string;
}

/* ─── Support ──────────────────────────────────────────────────── */

export interface SupportTicket {
  id: string;
  userId: string;
  subject: string;
  category: string;
  message: string;
  status: 'OPEN' | 'ANSWERED' | 'CLOSED';
  createdAt: string;
}

