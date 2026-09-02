import AppShell from '../components/AppShell';
import Icon from '../components/Icon';

export default function SafetyPage() {
  return (
    <AppShell title="Safety">
      <div className="im-fade-up max-w-3xl">
        <h1 className="text-2xl font-bold tracking-tight text-[var(--im-text)] mb-1 flex items-center gap-3">
          <span aria-hidden="true" className="grid h-9 w-9 place-items-center rounded-lg text-[#FFFFFF]"
            style={{ background: 'linear-gradient(135deg,#059669,#0d9488)' }}>
            <Icon name="shield" size={20} />
          </span>
          Rider &amp; driver safety
        </h1>
        <p className="mt-1 text-[var(--im-text-muted)]">IntelliMove is built with safety at every step of the journey.</p>

        <div className="mt-6 space-y-6">
          <section className="im-card im-card-pad">
            <h2 className="font-semibold text-[var(--im-text)] mb-2 flex items-center gap-2">
              <Icon name="star" size={16} /> Driver verification
            </h2>
            <p className="text-sm text-[var(--im-text-muted)]">
              Every driver in the IntelliMove network is screened and verified. Before a driver is
              assigned to your ride, their government ID, driving license, and background check are
              confirmed. After a driver is matched, the app displays the driver's verified name,
              vehicle make/model, color, license plate, and star rating so you can confirm you have
              the right driver and car.
            </p>
          </section>

          <section className="im-card im-card-pad">
            <h2 className="font-semibold text-[var(--im-text)] mb-2 flex items-center gap-2">
              <Icon name="eye" size={16} /> Real-time trip sharing
            </h2>
            <p className="text-sm text-[var(--im-text-muted)]">
              While a trip is active you can share your real-time location with a trusted contact so
              they can follow your progress along the route. The driver assigned to your ride is the
              only person who can drive you, and the trip is logged end-to-end.
            </p>
          </section>

          <section className="im-card im-card-pad">
            <h2 className="font-semibold text-[var(--im-text)] mb-2 flex items-center gap-2">
              <Icon name="navigation" size={16} /> Verified routes
            </h2>
            <p className="text-sm text-[var(--im-text-muted)]">
              The route between pickup and destination is calculated by the IntelliMove Location
              service and displayed on the booking and active-trip screens. Your trip follows the
              confirmed route, and the final fare is computed from the actual distance travelled.
            </p>
          </section>

          <section className="im-card im-card-pad">
            <h2 className="font-semibold text-[var(--im-text)] mb-2 flex items-center gap-2">
              <Icon name="alert" size={16} /> Reporting an issue
            </h2>
            <p className="text-sm text-[var(--im-text-muted)]">
              If you experience a safety issue or a problem with a trip, use the Contact Support page
              to open a support ticket. Provide your ride details and the nature of the concern and a
              support agent will review it promptly. You may also cancel any trip that has not yet
              been started directly from the active-trip card.
            </p>
          </section>
        </div>
      </div>
    </AppShell>
  );
}
