import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

const client = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

// Request interceptor: attach JWT + forward identity
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  const userId = localStorage.getItem('userId');
  if (userId) {
    config.headers['X-User-Id'] = userId;
  }
  return config;
});

// Response interceptor: handle 401
client.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken && !error.config._retry) {
        error.config._retry = true;
        try {
          const res = await axios.post(`${API_BASE}/api/v1/auth/refresh`, { refreshToken });
          const { accessToken, refreshToken: newRefresh } = res.data.data;
          localStorage.setItem('accessToken', accessToken);
          localStorage.setItem('refreshToken', newRefresh);
          error.config.headers.Authorization = `Bearer ${accessToken}`;
          return client(error.config);
        } catch {
          localStorage.clear();
          window.location.href = '/login';
        }
      } else {
        localStorage.clear();
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export const api = client;

export type ApiResponse<T = unknown> = {
  success: boolean;
  message?: string;
  data: T;
};

export type PagedData<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

/**
 * Extracts the backend's safe message for display.
 * Only reads `response.data.message` — never throws on shape changes.
 */
export function safeMessage(err: unknown): string {
  const e = err as { response?: { data?: { message?: string; error?: string } } };
  const msg = e?.response?.data?.message || e?.response?.data?.error || 'Unexpected error. Please try again.';
  return msg;
}

export async function apiCall<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH',
  url: string,
  body?: unknown
): Promise<T> {
  const res = await client.request<ApiResponse<T>>({ method, url, data: body });
  return res.data.data;
}

// ─── Saved places ───────────────────────────────────────────────
export interface SavedPlace {
  id?: string;
  userId: string;
  label: string;
  address: string;
  latitude: number;
  longitude: number;
  type: string;
}
export const getSavedPlaces = () => apiCall<SavedPlace[]>('GET', '/api/v1/users/saved-places');
export const createSavedPlace = (place: Omit<SavedPlace, 'id'>) =>
  apiCall<SavedPlace>('POST', '/api/v1/users/saved-places', place);
export const updateSavedPlace = (id: string, place: Partial<SavedPlace>) =>
  apiCall<SavedPlace>('PUT', `/api/v1/users/saved-places/${id}`, place);
export const deleteSavedPlace = (id: string) => apiCall<void>('DELETE', `/api/v1/users/saved-places/${id}`);

// ─── Preferences ────────────────────────────────────────────────
export interface UserPreferences {
  id?: string;
  userId: string;
  emailNotifications: boolean;
  smsNotifications: boolean;
  pushNotifications: boolean;
  darkMode: boolean;
  currency: string;
}
export const getPreferences = () => apiCall<UserPreferences>('GET', '/api/v1/users/preferences');
export const updatePreferences = (prefs: Partial<UserPreferences>) =>
  apiCall<UserPreferences>('PUT', '/api/v1/users/preferences', prefs);

// ─── Payments ───────────────────────────────────────────────────
export interface Payment {
  id: string;
  rideId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';
  provider: string;
  paymentMethod: string;
  createdAt: string;
  completedAt?: string;
  failureReason?: string;
}
export const getCustomerPayments = (customerId: string, page = 0, size = 20) =>
  apiCall<PagedData<Payment>>('GET', `/api/v1/payments/customer/${customerId}?page=${page}&size=${size}`);
export const getPaymentByRide = (rideId: string) =>
  apiCall<Payment>('GET', `/api/v1/payments/ride/${rideId}`);

// ─── Notifications ──────────────────────────────────────────────
export interface Notification {
  id: string;
  recipientId: string;
  title: string;
  message: string;
  channel: string;
  read: boolean;
  createdAt: string;
  readAt?: string;
}
export const getNotifications = (page = 0, size = 20) =>
  apiCall<PagedData<Notification>>('GET', `/api/v1/notifications?page=${page}&size=${size}`);
export const getUnreadCount = () => apiCall<{ unreadCount: number }>('GET', '/api/v1/notifications/unread-count');
export const markNotificationRead = (id: string) => apiCall<void>('POST', `/api/v1/notifications/${id}/read`);
export const markAllNotificationsRead = () => apiCall<{ updated: number }>('POST', '/api/v1/notifications/read-all');

// ─── Support ────────────────────────────────────────────────────
export interface SupportTicket {
  id?: string;
  userId: string;
  subject: string;
  message: string;
  category: string;
  status?: string;
  createdAt?: string;
}
export const createSupportTicket = (ticket: Omit<SupportTicket, 'id' | 'status' | 'createdAt'>) =>
  apiCall<SupportTicket>('POST', '/api/v1/ai/support/tickets', ticket);
export const submitAiSupportQuery = (query: string) =>
  apiCall<{ reply: string }>('POST', '/api/v1/ai/support/query', { query });

export default client;
