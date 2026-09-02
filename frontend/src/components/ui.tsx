import type { ReactNode } from 'react';
import Icon from './Icon';

/** Status → badge color mapping (ride, payment, driver states). */
const STATUS_STYLE: Record<string, { bg: string; fg: string }> = {
  REQUESTED: { bg: 'rgba(245, 158, 11, 0.18)', fg: '#FCD34D' },
  MATCHING: { bg: 'rgba(244, 63, 127, 0.16)', fg: '#FDA4AF' },
  DRIVER_ASSIGNED: { bg: 'rgba(225, 29, 104, 0.24)', fg: '#FB7185' },
  DRIVER_ACCEPTED: { bg: 'rgba(34, 197, 94, 0.16)', fg: '#86EFAC' },
  DRIVER_ARRIVING: { bg: 'rgba(34, 197, 94, 0.16)', fg: '#86EFAC' },
  TRIP_STARTED: { bg: 'rgba(34, 197, 94, 0.22)', fg: '#4ADE80' },
  TRIP_COMPLETED: { bg: 'rgba(34, 197, 94, 0.16)', fg: '#86EFAC' },
  CANCELLED: { bg: 'rgba(239, 68, 68, 0.16)', fg: '#FCA5A5' },
  COMPLETED: { bg: 'rgba(34, 197, 94, 0.16)', fg: '#86EFAC' },
  PROCESSING: { bg: 'rgba(245, 158, 11, 0.18)', fg: '#FCD34D' },
  FAILED: { bg: 'rgba(239, 68, 68, 0.16)', fg: '#FCA5A5' },
  REFUNDED: { bg: 'rgba(251, 113, 133, 0.14)', fg: '#FDA4AF' },
};

export function StatusBadge({ status }: { status: string }) {
  const s = STATUS_STYLE[status] ?? { bg: 'rgba(217, 168, 183, 0.12)', fg: '#D1A8B7' };
  return (
    <span className="im-badge" style={{ background: s.bg, color: s.fg }} data-status={status}>
      {status.replaceAll('_', ' ').toLowerCase()}
    </span>
  );
}

export function EmptyState({
  icon,
  title,
  body,
  action,
}: {
  icon: Parameters<typeof Icon>[0]['name'];
  title: string;
  body?: string;
  action?: ReactNode;
}) {
  return (
    <div className="im-card im-fade-up flex flex-col items-center gap-3 px-6 py-12 text-center">
      <span
        aria-hidden="true"
        className="grid h-14 w-14 place-items-center rounded-full text-[#FB7185]"
        style={{ background: 'rgba(225, 29, 104, 0.14)' }}
      >
        <Icon name={icon} size={26} />
      </span>
      <p className="text-base font-semibold">{title}</p>
      {body && <p className="max-w-sm text-sm text-[var(--im-text-muted)]">{body}</p>}
      {action}
    </div>
  );
}

export function SkeletonList({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3" aria-hidden="true">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="im-card p-4 flex items-center gap-4">
          <div className="im-skeleton h-10 w-10 rounded-full" />
          <div className="flex-1 space-y-2">
            <div className="im-skeleton h-3.5 w-2/5" />
            <div className="im-skeleton h-3 w-3/5" />
          </div>
          <div className="im-skeleton h-6 w-16 rounded-full" />
        </div>
      ))}
    </div>
  );
}

export function ErrorState({ message }: { message: string }) {
  return (
    <div className="im-alert-error" role="alert">
      <Icon name="alert" size={18} />
      <div>
        <p className="font-semibold">Something went wrong</p>
        <p>{message}</p>
      </div>
    </div>
  );
}
