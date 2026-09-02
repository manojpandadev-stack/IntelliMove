import { useEffect, useRef, useState, useCallback } from 'react';

const WS_BASE =
  (import.meta.env.VITE_WS_URL as string | undefined) ??
  `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`;

type ConnectionStatus = 'connecting' | 'open' | 'closed';

/**
 * Subscribes to the Location Service WebSocket for a given topic path,
 * e.g. `/location` (the `/ws/location` endpoint proxied by Vite).
 *
 * - Authenticates the handshake with the JWT access token as the `token`
 *   query parameter (the browser-supported path in WebSocketConfig) and also
 *   sends it in the `Authorization` subprotocol slot for compatibility.
 * - When `subscribeMessage` is provided it is sent automatically every time
 *   the socket opens — including after automatic reconnects — so channel
 *   subscriptions survive disconnects.
 * - Auto-reconnects with capped exponential backoff while enabled.
 */
export function useRideWebSocket(
  topicPath: string | null,
  onMessage: (data: unknown) => void,
  subscribeMessage?: Record<string, unknown>,
) {
  const [status, setStatus] = useState<ConnectionStatus>('closed');
  const socketRef = useRef<WebSocket | null>(null);
  const retryRef = useRef(0);
  const timerRef = useRef<number | undefined>(undefined);
  const handlerRef = useRef(onMessage);
  handlerRef.current = onMessage;
  // Kept in a ref so callers can pass an inline object without triggering
  // reconnects; re-read on every successful open.
  const subscribeRef = useRef(subscribeMessage);
  subscribeRef.current = subscribeMessage;

  const connect = useCallback(() => {
    if (!topicPath || socketRef.current) return;
    setStatus('connecting');
    const proto = window.location.protocol === 'https:' ? ['wss'] : [];
    let ws: WebSocket;
    try {
      const token = localStorage.getItem('accessToken') ?? '';
      const authQuery = token ? `?token=${encodeURIComponent(token)}` : '';
      ws = new WebSocket(
        `${WS_BASE}${topicPath}${authQuery}`,
        proto.length ? [...proto, `auth_${token}`] : [`auth_${token}`]
      );
    } catch {
      setStatus('closed');
      return;
    }
    socketRef.current = ws;

    ws.onopen = () => {
      retryRef.current = 0;
      setStatus('open');
      // Re-establish the channel subscription after (re)connect.
      const sub = subscribeRef.current;
      if (sub && ws.readyState === WebSocket.OPEN) {
        try {
          ws.send(JSON.stringify(sub));
        } catch {
          // ignore transient send failures; server tolerates missing subs
        }
      }
    };
    ws.onmessage = (ev) => {
      try {
        handlerRef.current(JSON.parse(ev.data as string));
      } catch {
        handlerRef.current(ev.data);
      }
    };
    ws.onclose = () => {
      socketRef.current = null;
      setStatus('closed');
      // Reconnect with backoff (1s, 2s, 4s ... capped at 15s).
      const delay = Math.min(1000 * 2 ** retryRef.current, 15000);
      retryRef.current += 1;
      timerRef.current = window.setTimeout(connect, delay);
    };
    ws.onerror = () => ws.close();
  }, [topicPath]);

  useEffect(() => {
    connect();
    return () => {
      if (timerRef.current) window.clearTimeout(timerRef.current);
      const ws = socketRef.current;
      if (ws) {
        ws.onclose = null; // prevent reconnect loop after intentional close
        ws.close();
        socketRef.current = null;
      }
    };
  }, [connect]);

  return status;
}
