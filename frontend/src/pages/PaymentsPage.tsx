import { useState } from 'react';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';
import { EmptyState, SkeletonList, StatusBadge, ErrorState } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useCustomerPayments, useGetRide } from '../api/hooks';
import type { Payment } from '../api/types';

function fmtDate(iso?: string) {
  return iso ? new Date(iso).toLocaleString() : '—';
}

function amount(p: Payment) {
  return `${p.currency ?? 'USD'} ${Number(p.amount).toFixed(2)}`;
}

function PaymentRow({ payment }: { payment: Payment }) {
  const { data: ride } = useGetRide(payment.rideId);
  const label = ride
    ? `${ride.pickupAddress ?? 'Pickup'} → ${ride.dropoffAddress ?? 'Destination'}`
    : `Trip #${payment.rideId.slice(0, 8)}`;
  return (
    <div className="im-card im-card-pad flex items-center gap-4" data-testid="payment-row">
      <span
        aria-hidden="true"
        className="grid h-10 w-10 shrink-0 place-items-center rounded-lg"
        style={{ background: payment.status === 'COMPLETED' ? 'rgba(34, 197, 94, 0.16)' : 'rgba(217, 168, 183, 0.12)', color: payment.status === 'COMPLETED' ? '#86EFAC' : '#D1A8B7' }}
      >
        <Icon name="credit-card" size={18} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium text-[var(--im-text)]">{label}</p>
        <p className="text-xs text-[var(--im-text-muted)]">
          {fmtDate(payment.completedAt ?? payment.createdAt)} · {payment.method?.toLowerCase() ?? 'wallet'}
        </p>
        {payment.failureReason && (
          <p className="mt-0.5 text-xs" style={{ color: '#FCA5A5' }}>{payment.failureReason}</p>
        )}
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <span className="text-sm font-semibold text-[var(--im-text)]">{amount(payment)}</span>
        <StatusBadge status={payment.status} />
      </div>
    </div>
  );
}

export default function PaymentsPage() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useCustomerPayments(user?.id ?? '', page);

  const completed = (data?.content ?? []).filter((p) => p.status === 'COMPLETED');
  const totalSpent = completed.reduce((sum, p) => sum + Number(p.amount), 0);

  return (
    <AppShell title="Payments">
      {/* Summary */}
      <div className="mb-6 grid gap-4 sm:grid-cols-3" data-testid="payments-summary">
        <div className="im-card p-5">
          <p className="text-sm text-[var(--im-text-muted)]">Total spent</p>
          <p className="mt-1 text-2xl font-bold tracking-tight text-[var(--im-text)]" data-testid="total-spent">
            USD {totalSpent.toFixed(2)}
          </p>
        </div>
        <div className="im-card p-5">
          <p className="text-sm text-[var(--im-text-muted)]">Completed payments</p>
          <p className="mt-1 text-2xl font-bold tracking-tight text-[var(--im-text)]">{completed.length}</p>
        </div>
        <div className="im-card p-5">
          <p className="text-sm text-[var(--im-text-muted)]">Payment method</p>
          <p className="mt-1 flex items-center gap-2 text-base font-semibold text-[var(--im-text)]">
            <Icon name="wallet" size={18} style={{ color: 'var(--im-brand-600)' }} /> IntelliMove Wallet
            <span className="im-badge" style={{ background: 'rgba(244, 63, 127, 0.16)', color: '#FDA4AF' }}>sandbox</span>
          </p>
        </div>
      </div>

      <p className="im-alert-info mb-6">
        <Icon name="shield" size={16} />
        Payments run on the IntelliMove sandbox provider for local development. No real cards or bank
        credentials are used, and no card data is ever stored or displayed.
      </p>

      <h3 className="mb-3 font-semibold">Recent payments</h3>
      {isError ? (
        <ErrorState message="We couldn't load your payments. Please try again later." />
      ) : isLoading ? (
        <SkeletonList rows={3} />
      ) : !data?.content?.length ? (
        <EmptyState
          icon="credit-card"
          title="No payments yet."
          body="After your first trip is completed, its fare and receipt will appear here."
        />
      ) : (
        <>
          <ul className="space-y-3" data-testid="payments-list">
            {data.content.map((p) => (
              <li key={p.id}>
                <PaymentRow payment={p} />
              </li>
            ))}
          </ul>
          {!data.first && (
            <button className="im-btn im-btn-secondary mt-4 mr-2" onClick={() => setPage((v) => Math.max(0, v - 1))}>
              Previous
            </button>
          )}
          {!data.last && (
            <button className="im-btn im-btn-secondary mt-4" onClick={() => setPage((v) => v + 1)}>
              Next
            </button>
          )}
        </>
      )}
    </AppShell>
  );
}
