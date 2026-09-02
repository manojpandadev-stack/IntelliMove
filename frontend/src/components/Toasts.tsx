import { useCallback, useRef, useState } from 'react';
import Icon from './Icon';

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

const TOAST_ICON: Record<ToastKind, { name: 'check-circle' | 'alert' | 'bell'; color: string }> = {
  success: { name: 'check-circle', color: 'var(--im-success)' },
  error: { name: 'alert', color: 'var(--im-danger)' },
  info: { name: 'bell', color: 'var(--im-brand-600)' },
};

/** Animated toast stack — aria-live so screen readers announce updates. */
export function ToastStack({ toasts, onDismiss }: { toasts: ToastItem[]; onDismiss: (id: number) => void }) {
  if (toasts.length === 0) return null;
  return (
    <div
      className="fixed bottom-20 right-4 z-50 flex flex-col gap-2 md:bottom-6"
      role="status"
      aria-live="polite"
    >
      {toasts.map((t) => {
        const ic = TOAST_ICON[t.kind];
        return (
          <div key={t.id} className={`im-toast im-toast-${t.kind}`}>
            <Icon name={ic.name} size={18} style={{ color: ic.color, flexShrink: 0, marginTop: 1 }} />
            <p className="min-w-0 flex-1">{t.message}</p>
            <button
              type="button"
              aria-label="Dismiss notification"
              className="shrink-0 rounded p-0.5 text-[var(--im-text-muted)] hover:text-[var(--im-text-secondary)]"
              onClick={() => onDismiss(t.id)}
            >
              <Icon name="x" size={14} />
            </button>
          </div>
        );
      })}
    </div>
  );
}

/** Hook backing the toast stack: `push('success', 'msg')` auto-dismisses after 5s. */
export function useToastStack() {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((list) => list.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string) => {
      const id = nextId.current++;
      setToasts((list) => [...list.slice(-3), { id, kind, message }]);
      window.setTimeout(() => dismiss(id), 5000);
    },
    [dismiss],
  );

  return { toasts, push, dismiss };
}
