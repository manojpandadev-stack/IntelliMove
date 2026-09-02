import axios from 'axios';

/**
 * Extracts a safe, user-presentable message from an API error.
 * Only surfaces backend-provided messages (never stack traces,
 * SQL errors, JWT contents or internal details).
 */
export function safeErrorMessage(err: unknown, fallback = 'Service temporarily unavailable. Please try again.'): string {
  if (axios.isAxiosError(err)) {
    if (err.code === 'ECONNABORTED' || err.message === 'Network Error') {
      return 'Cannot reach the IntelliMove service. Check your connection and try again.';
    }
    const data = err.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message ?? data?.error;
    if (msg) return msg;
    if (err.response?.status === 401) return 'Your session has expired. Please sign in again.';
    if (err.response?.status === 403) return 'You are not authorized to perform this action.';
    if (err.response?.status === 404) return 'The requested item was not found.';
  }
  return fallback;
}

/** Maps a backend status code to ride-flow friendly guidance. */
export function rideRequestError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined;
    const detail = data?.message ? `: ${data.message}` : '.';
    switch (err.response?.status) {
      case 400:
        return `Unable to request your ride${detail} Please check the pickup and destination.`;
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 403:
        return 'Your account is not allowed to request rides.';
      case 409:
        return `Unable to request your ride${detail}`;
      case 422:
        return `Unable to request your ride${detail}`;
      default:
        break;
    }
  }
  return safeErrorMessage(err, 'Unable to request your ride right now. Please try again.');
}
