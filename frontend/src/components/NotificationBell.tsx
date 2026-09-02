import { useEffect, useRef, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import Icon from './Icon';
import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
  useUnreadNotificationCount,
} from '../api/hooks';

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60_000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min}m ago`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h}h ago`;
  return new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric' });
}

/**
 * Notification bell with unread badge.
 * - Desktop (md+): opens an accessible popover with the latest notifications,
 *   mark-as-read and mark-all-as-read — backed by the real notification APIs.
 * - Mobile: renders a link to the dedicated /notifications page.
 */
export default function NotificationBell({ mobile = false }: { mobile?: boolean }) {
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();
  const { data: unread = 0 } = useUnreadNotificationCount();

  // Latest notifications for the popover (real API; page 0, newest first).
  const { data: notifs, isLoading, isError, refetch } = useNotifications(0);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
    queryClient.invalidateQueries({ queryKey: ['unreadNotifications'] });
  };

  // Close on outside click or Escape.
  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const badge =
    unread > 0 ? (
      <span
        data-testid={mobile ? 'unread-badge-mobile' : 'unread-badge'}
        className="absolute -right-0.5 -top-0.5 grid place-items-center rounded-full px-1 text-[10px] font-bold text-[#FFFFFF]"
        style={{ background: 'var(--im-danger)', height: 18, minWidth: 18 }}
        aria-label={`${unread} unread notifications`}
      >
        {unread > 99 ? '99+' : unread}
      </span>
    ) : null;

  if (mobile) {
    return (
      <NavLink
        to="/notifications"
        className="relative im-nav-item !px-2"
        aria-label={`Notifications${unread > 0 ? ` (${unread} unread)` : ''}`}
        data-testid="notifications-bell"
      >
        <Icon name="bell" size={19} />
        {badge}
      </NavLink>
    );
  }

  const latest = notifs?.content?.slice(0, 5) ?? [];

  return (
    <div ref={boxRef} className="relative">
      <button
        type="button"
        className="im-nav-item relative !px-2"
        aria-label={`Notifications${unread > 0 ? ` (${unread} unread)` : ''}`}
        aria-expanded={open}
        aria-haspopup="true"
        data-testid="notifications-bell"
        onClick={() => setOpen((o) => !o)}
      >
        <Icon name="bell" size={18} />
        {badge}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="im-card im-pop absolute right-0 z-50 mt-2 w-80 overflow-hidden p-0"
        >
          <div className="flex items-center justify-between border-b border-[var(--im-border)] px-4 py-2.5">
            <p className="text-sm font-semibold">
              Notifications
              {unread > 0 && (
                <span
                  className="ml-2 rounded-full px-2 py-0.5 text-[11px] font-bold text-[#FFFFFF]"
                  style={{ background: 'var(--im-danger)' }}
                >
                  {unread} new
                </span>
              )}
            </p>
            <button
              type="button"
              className="rounded px-1.5 py-0.5 text-xs font-semibold transition hover:bg-[rgb(244_63_127/0.08)]"
              style={{ color: 'var(--im-brand-600)' }}
              disabled={markAllRead.isPending || unread === 0}
              onClick={() => markAllRead.mutate(undefined, { onSuccess: invalidate })}
            >
              Mark all read
            </button>
          </div>

          <div className="max-h-72 overflow-auto">
            {isLoading ? (
              <div className="space-y-3 p-4" aria-hidden="true">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="space-y-1.5">
                    <div className="im-skeleton h-3 w-2/3" />
                    <div className="im-skeleton h-3 w-1/3" />
                  </div>
                ))}
              </div>
            ) : isError ? (
              <div className="p-4 text-center">
                <p className="mb-2 text-sm text-[var(--im-text-muted)]">Couldn't load notifications.</p>
                <button type="button" className="im-btn im-btn-secondary !px-3 !py-1 !text-xs" onClick={() => refetch()}>
                  Retry
                </button>
              </div>
            ) : latest.length === 0 ? (
              <div className="flex flex-col items-center gap-2 px-4 py-8 text-center">
                <span aria-hidden="true" className="grid h-10 w-10 place-items-center rounded-full bg-[var(--im-elevated)] text-[var(--im-text-muted)]">
                  <Icon name="bell" size={18} />
                </span>
                <p className="text-sm font-medium">You're all caught up</p>
                <p className="text-xs text-[var(--im-text-muted)]">Ride updates will appear here.</p>
              </div>
            ) : (
              <ul>
                {latest.map((n) => (
                  <li key={n.id} className={`border-b border-[var(--im-border)] last:border-0 ${n.read ? '' : 'bg-[rgb(225_29_104/0.10)]'}`}>
                    <div className="flex items-start gap-2.5 px-4 py-2.5">
                      <span
                        aria-hidden="true"
                        className="mt-1 h-2 w-2 shrink-0 rounded-full"
                        style={{ background: n.read ? 'var(--im-text-muted)' : 'var(--im-bright, #F43F7F)' }}
                      />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{n.title}</p>
                        <p className="line-clamp-2 text-xs text-[var(--im-text-muted)]">{n.message}</p>
                        <p className="mt-0.5 flex items-center gap-2 text-[11px] text-[var(--im-text-muted)]">
                          {relativeTime(n.createdAt)}
                          {!n.read && (
                            <button
                              type="button"
                              className="font-semibold transition hover:brightness-90"
                              style={{ color: 'var(--im-brand-600)' }}
                              disabled={markRead.isPending}
                              onClick={() => markRead.mutate(n.id, { onSuccess: invalidate })}
                            >
                              Mark as read
                            </button>
                          )}
                        </p>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <NavLink
            to="/notifications"
            onClick={() => setOpen(false)}
            className="block border-t border-[var(--im-border)] px-4 py-2 text-center text-xs font-semibold transition hover:bg-[rgb(244_63_127/0.08)]"
            style={{ color: 'var(--im-brand-600)' }}
          >
            View all notifications
          </NavLink>
        </div>
      )}
    </div>
  );
}
