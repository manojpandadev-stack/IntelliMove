import { NavLink, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useAuth } from '../context/AuthContext';
import { useUnreadNotificationCount } from '../api/hooks';
import Icon, { type IconName } from './Icon';
import NotificationBell from './NotificationBell';
import Avatar from './Avatar';

function Logo() {
  return (
    <span className="flex items-center gap-2 select-none">
      <span
        aria-hidden="true"
        className="grid h-8 w-8 place-items-center rounded-xl text-[#FFFFFF]"
        style={{ background: 'linear-gradient(135deg,#E11D68,#BE185D)' }}
      >
        <Icon name="navigation" size={17} strokeWidth={2} />
      </span>
      <span className="text-lg font-bold tracking-tight" style={{ color: 'var(--im-text)' }}>
        Intelli<span style={{ color: 'var(--im-brand-600)' }}>Move</span>
      </span>
    </span>
  );
}

const NAV_BY_ROLE: Record<string, { to: string; label: string; icon: IconName }[]> = {
  CUSTOMER: [
    { to: '/dashboard', label: 'Book a ride', icon: 'home' },
    { to: '/rides', label: 'My Rides', icon: 'route' },
    { to: '/payments', label: 'Payments', icon: 'wallet' },
    { to: '/saved-places', label: 'Saved places', icon: 'map-pin' },
    { to: '/notifications', label: 'Notifications', icon: 'bell' },
    { to: '/profile', label: 'Profile', icon: 'user' },
    { to: '/settings', label: 'Settings', icon: 'help' },
    { to: '/help-center', label: 'Help center', icon: 'help' },
    { to: '/safety', label: 'Safety', icon: 'shield' },
    { to: '/contact-support', label: 'Contact support', icon: 'message' },
  ],
  DRIVER: [
    { to: '/driver', label: 'Drive', icon: 'car' },
    { to: '/driver/earnings', label: 'Earnings', icon: 'trend' },
    { to: '/driver/profile', label: 'Profile', icon: 'user' },
    { to: '/notifications', label: 'Notifications', icon: 'bell' },
    { to: '/settings', label: 'Settings', icon: 'help' },
    { to: '/help-center', label: 'Help center', icon: 'help' },
    { to: '/safety', label: 'Safety', icon: 'shield' },
    { to: '/contact-support', label: 'Contact support', icon: 'message' },
  ],
  ADMIN: [
    { to: '/admin', label: 'Operations', icon: 'activity' },
    { to: '/admin/ai', label: 'AI Assistant', icon: 'sparkles' },
    { to: '/notifications', label: 'Notifications', icon: 'bell' },
    { to: '/settings', label: 'Settings', icon: 'help' },
    { to: '/help-center', label: 'Help center', icon: 'help' },
    { to: '/safety', label: 'Safety', icon: 'shield' },
    { to: '/contact-support', label: 'Contact support', icon: 'message' },
  ],
};
NAV_BY_ROLE.SUPER_ADMIN = NAV_BY_ROLE.ADMIN;

/** App-like bottom navigation for the rider experience on mobile. */
const BOTTOM_NAV_CUSTOMER: { to: string; label: string; icon: IconName }[] = [
  { to: '/dashboard', label: 'Book', icon: 'car' },
  { to: '/rides', label: 'Rides', icon: 'route' },
  { to: '/payments', label: 'Payments', icon: 'wallet' },
  { to: '/saved-places', label: 'Saved', icon: 'map-pin' },
];

/** Compact desktop profile dropdown (links only; logout stays a direct,
 * always-visible control so it is never more than one interaction away). */
function ProfileMenu({
  profileTo,
  profileLabel,
  name,
  avatar,
}: {
  profileTo: string;
  profileLabel: string;
  name: string;
  avatar?: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const item = 'im-nav-item !py-1.5 !text-sm w-full';
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        className="im-nav-item"
        aria-label="Open profile menu"
        aria-expanded={open}
        aria-haspopup="true"
        data-testid="profile-menu"
        title="Account"
        onClick={() => setOpen((o) => !o)}
      >
        {avatar ?? <Icon name="user" size={17} />}
      </button>
      {open && (
        <div role="menu" aria-label="Profile" className="im-card im-pop absolute right-0 z-50 mt-2 w-48 p-1">
          <div className="flex items-center gap-2 px-3 py-2">
            {avatar}
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold text-[var(--im-text)]" data-testid="profile-menu-name">
                {name}
              </span>
              <span className="block text-[11px] text-[var(--im-text-muted)]">{profileLabel}</span>
            </span>
          </div>
          <NavLink role="menuitem" to={profileTo} className={item} onClick={() => setOpen(false)}>
            <Icon name="user" size={15} /> {profileLabel}
          </NavLink>
          <NavLink role="menuitem" to="/settings" className={item} onClick={() => setOpen(false)}>
            <Icon name="help" size={15} /> Settings
          </NavLink>
          <NavLink role="menuitem" to="/help-center" className={item} onClick={() => setOpen(false)}>
            <Icon name="message" size={15} /> Help center
          </NavLink>
        </div>
      )}
    </div>
  );
}

export default function AppShell({
  title,
  children,
}: {
  title?: string;
  children: ReactNode;
}) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [menuOpen, setMenuOpen] = useState(false);
  const role = user?.role ?? 'CUSTOMER';
  const nav = NAV_BY_ROLE[role] ?? NAV_BY_ROLE.CUSTOMER;
  const { data: unread = 0 } = useUnreadNotificationCount();
  const headerAvatar = (
    <Avatar
      userId={user?.id}
      firstName={user?.firstName}
      lastName={user?.lastName}
      size={26}
      testId="header-avatar"
    />
  );

  const doLogout = () => {
    logout();
    queryClient.clear();
    navigate('/login');
  };

  const profileTo =
    role === 'DRIVER' ? '/driver/profile' : role === 'CUSTOMER' ? '/profile' : '/admin';

  return (
    <div className="min-h-screen flex flex-col" style={{ background: 'var(--im-canvas)' }}>
      {/* Top bar */}
      <header
        className="sticky top-0 z-40 border-b im-glass"
        style={{ borderColor: 'var(--im-border)' }}
      >
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4">
          <button
            className="ml-0 grid h-9 w-9 place-items-center rounded-lg text-[var(--im-text-muted)] transition hover:bg-[rgb(244_63_127/0.08)] hover:text-[var(--im-text)] md:hidden"
            aria-label="Toggle navigation menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((o) => !o)}
          >
            <Icon name={menuOpen ? 'x' : 'menu'} />
          </button>
          <NavLink to="/dashboard" className="mr-auto" aria-label="IntelliMove home">
            <Logo />
          </NavLink>

          {/* Compact mobile actions: bell → notifications page, profile avatar */}
          <div className="flex items-center gap-0.5 md:hidden">
            <NotificationBell mobile />
            <NavLink
              to={profileTo}
              className="im-nav-item !px-2"
              aria-label="Profile"
              data-testid="mobile-profile"
              title="Profile"
            >
              {headerAvatar}
            </NavLink>
          </div>

          {/* Desktop nav */}
          <nav className="hidden md:flex items-center gap-1" aria-label="Primary">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => `im-nav-item ${isActive ? 'active' : ''}`}
              >
                <Icon name={item.icon} size={17} />
                {item.label}
                {item.to === '/notifications' && unread > 0 && (
                  <span
                    data-testid="unread-badge"
                    className="ml-1 grid h-5 min-w-5 place-items-center rounded-full px-1 text-[11px] font-bold text-[#FFFFFF]"
                    style={{ background: 'var(--im-danger)' }}
                  >
                    {unread > 99 ? '99+' : unread}
                  </span>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="hidden md:flex items-center gap-1 ml-2 pl-2 border-l" style={{ borderColor: 'var(--im-border)' }}>
            {user && (
              <span className="hidden lg:inline text-sm font-medium text-[var(--im-text-secondary)]" data-testid="header-user-name">
                {user.firstName} {user.lastName}
              </span>
            )}
            <NotificationBell />
            <ProfileMenu
              profileTo={profileTo}
              profileLabel={role === 'DRIVER' ? 'Driver profile' : role === 'CUSTOMER' ? 'My profile' : 'Operations'}
              name={user ? `${user.firstName} ${user.lastName}` : ''}
              avatar={headerAvatar}
            />
            <button className="im-btn im-btn-ghost" onClick={doLogout} title="Log out" aria-label="Log out">
              <Icon name="logout" size={18} />
            </button>
          </div>
        </div>

        {/* Mobile drawer */}
        {menuOpen && (
          <nav className="md:hidden border-t bg-[var(--im-bg-alt)] px-4 py-3 space-y-1" style={{ borderColor: 'var(--im-border)' }} aria-label="Mobile">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={() => setMenuOpen(false)}
                className={({ isActive }) => `im-nav-item ${isActive ? 'active' : ''}`}
              >
                <Icon name={item.icon} size={17} />
                {item.label}
              </NavLink>
            ))}
            <NavLink to={profileTo} onClick={() => setMenuOpen(false)} className="im-nav-item">
              <Icon name="user" size={17} /> Profile
            </NavLink>
            <button className="im-nav-item w-full text-left" onClick={doLogout}>
              <Icon name="logout" size={17} /> Log out
            </button>
          </nav>
        )}
      </header>

      {/* Page content */}
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 pb-24 md:pb-10">
        {(title || user) && (
          <div className="mb-5 flex items-end justify-between gap-3">
            <h1 className="text-xl md:text-2xl font-bold tracking-tight">{title}</h1>
            <span className="hidden sm:inline text-sm text-[var(--im-text-muted)]">
              {user?.firstName} {user?.lastName}
            </span>
          </div>
        )}
        {children}
      </main>

      {/* Mobile bottom nav — app-like rider tabs with comfortable targets */}
      <nav
        className="md:hidden fixed bottom-0 inset-x-0 z-40 flex border-t im-glass"
        style={{ borderColor: 'var(--im-border)', paddingBottom: 'env(safe-area-inset-bottom)' }}
        aria-label="Bottom navigation"
      >
        {(role === 'CUSTOMER'
          ? [...BOTTOM_NAV_CUSTOMER, { to: profileTo, label: 'Profile', icon: 'user' as IconName }]
          : nav.slice(0, 4)
        ).map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex min-h-[56px] flex-1 flex-col items-center justify-center gap-0.5 py-2 text-[11px] font-medium transition-colors ${
                isActive ? '' : 'text-[var(--im-text-muted)]'
              }`
            }
            style={({ isActive }) => (isActive ? { color: 'var(--im-brand-600)' } : undefined)}
          >
            <Icon name={item.icon} size={20} />
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
