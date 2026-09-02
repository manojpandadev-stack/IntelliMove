import { useState } from 'react';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';
import { EmptyState, SkeletonList, ErrorState } from '../components/ui';
import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
} from '../api/hooks';
import type { Notification as NotificationItem } from '../api/types';

const TYPE_ICON: Record<string, Parameters<typeof Icon>[0]['name']> = {
  DRIVER_ASSIGNED: 'car',
  DRIVER_ARRIVING: 'navigation',
  RIDE_STARTED: 'route',
  RIDE_COMPLETED: 'check-circle',
  PAYMENT_COMPLETED: 'wallet',
  PAYMENT_FAILED: 'alert',
  RIDE_CANCELLED: 'x',
};

function timeAgo(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return new Date(iso).toLocaleDateString();
}

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useNotifications(page);
  const markRead = useMarkNotificationRead();
  const markAll = useMarkAllNotificationsRead();

  const items = data?.content ?? [];
  const unread = items.filter((n) => !n.read).length;

  return (
    <AppShell title="Notifications">
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="text-sm text-[var(--im-text-muted)]" data-testid="notifications-summary">
          {unread > 0 ? `${unread} unread notification${unread === 1 ? '' : 's'}` : 'You are all caught up.'}
        </p>
        <button
          className="im-btn im-btn-secondary !py-1.5 !px-3 !text-sm"
          onClick={() => markAll.mutate()}
          disabled={markAll.isPending || unread === 0}
          data-testid="mark-all-read"
        >
          <Icon name="check" size={15} />
          {markAll.isPending ? 'Updating…' : 'Mark all read'}
        </button>
      </div>

      {isLoading ? (
        <SkeletonList rows={4} />
      ) : isError ? (
        <ErrorState message="Notifications could not be loaded. Please try again." />
      ) : items.length === 0 ? (
        <EmptyState
          icon="bell"
          title="No notifications yet."
          body="Ride updates — driver assigned, trip started, payment receipts — will appear here in real time."
        />
      ) : (
        <ul className="space-y-2" data-testid="notification-list">
          {items.map((n: NotificationItem) => (
            <li key={n.id}>
              <div
                className={`im-card flex items-start gap-3 p-4 ${n.read ? 'opacity-70' : ''}`}
                style={n.read ? undefined : { borderLeft: '3px solid var(--im-brand-600)' }}
              >
                <span
                  aria-hidden="true"
                  className="grid h-9 w-9 shrink-0 place-items-center rounded-lg"
                  style={{ background: n.read ? 'rgba(217, 168, 183, 0.10)' : 'rgba(225, 29, 104, 0.16)', color: n.read ? 'var(--im-text-muted)' : '#FDA4AF' }}
                >
                  <Icon name={TYPE_ICON[n.type ?? ''] ?? 'bell'} size={17} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 font-medium text-[var(--im-text)]">
                    {n.title}
                    {!n.read && (
                      <span className="im-badge" style={{ background: 'rgba(225, 29, 104, 0.20)', color: '#FB7185' }}>new</span>
                    )}
                  </p>
                  <p className="text-sm text-[var(--im-text-muted)]">{n.message}</p>
                  <p className="mt-1 text-xs text-[var(--im-text-muted)]">{timeAgo(n.createdAt)}</p>
                </div>
                {!n.read && (
                  <button
                    className="im-btn im-btn-ghost !px-2 !py-1 !text-xs shrink-0"
                    onClick={() => markRead.mutate(n.id)}
                    disabled={markRead.isPending}
                    aria-label={`Mark "${n.title}" as read`}
                  >
                    Mark read
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-5 flex items-center justify-center gap-3">
          <button className="im-btn im-btn-secondary !py-1.5 !text-sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <span className="text-sm text-[var(--im-text-muted)]">
            Page {data.page + 1} of {data.totalPages}
          </span>
          <button className="im-btn im-btn-secondary !py-1.5 !text-sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Next
          </button>
        </div>
      )}
    </AppShell>
  );
}
