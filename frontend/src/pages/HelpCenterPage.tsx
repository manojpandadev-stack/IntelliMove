import { Link } from 'react-router-dom';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';

const faqs = [
  {
    category: 'Rides',
    items: [
      { q: 'How do I request a ride?', a: 'Enter a pickup location and destination on the Book a ride screen, pick a ride type, and click Request Ride. A nearby driver will be matched automatically.' },
      { q: 'How is my fare calculated?', a: 'Fares are computed in real time by IntelliMove\'s pricing engine based on distance, time, and ride type. The estimate you see before booking is the same engine used to set the final fare.' },
      { q: 'How do I cancel a ride?', a: 'Tap "Cancel ride" on your active trip card. Cancellations are free if made before a driver is assigned.' },
    ],
  },
  {
    category: 'Payments',
    items: [
      { q: 'What payment methods are accepted?', a: 'Payments are processed through IntelliMove\'s sandbox provider. Completed trips are charged automatically to your saved payment method in USD.' },
      { q: 'Why is my payment pending?', a: 'Payments may show as pending for a few minutes after trip completion while the provider finalizes authorization. They will settle to COMPLETED automatically.' },
    ],
  },
  {
    category: 'Account',
    items: [
      { q: 'How do I update my phone number?', a: 'Go to Profile → Edit to update your phone number. Changes are validated by the User service.' },
      { q: 'How do I log out on a shared device?', a: 'Click the logout button in the header or Profile page. Your access token is cleared immediately.' },
    ],
  },
  {
    category: 'Safety',
    items: [
      { q: 'How do I report a safety issue?', a: 'Use the Contact Support page to file a support ticket. Include your ride details and any safety concerns and a support agent will review it.' },
      { q: 'How can I verify my driver?', a: 'After a driver is assigned, the app displays the driver\'s name, photo, vehicle details, license plate, and rating.' },
    ],
  },
];

export default function HelpCenterPage() {
  return (
    <AppShell title="Help center">
      <div className="im-fade-up max-w-3xl">
        <h1 className="text-2xl font-bold tracking-tight text-[var(--im-text)] mb-1">Help center</h1>
        <p className="text-[var(--im-text-muted)] mb-6">Find answers to common questions about IntelliMove.</p>

        {faqs.map((section) => (
          <section key={section.category} className="mb-8">
            <h2 className="text-lg font-semibold text-[var(--im-text)] mb-4 flex items-center gap-2">
              <Icon name="help" size={18} /> {section.category}
            </h2>
            <dl className="space-y-4">
              {section.items.map((item) => (
                <div key={item.q} className="im-card im-card-pad">
                  <dt className="font-medium text-[var(--im-text)]">{item.q}</dt>
                  <dd className="mt-1 text-sm text-[var(--im-text-muted)]">{item.a}</dd>
                </div>
              ))}
            </dl>
          </section>
        ))}

        <div className="rounded-xl border border-dashed p-6 text-center" style={{ borderColor: 'var(--im-border)' }}>
          <Icon name="message" size={28} className="mx-auto mb-2 text-[var(--im-text-muted)]" />
          <h3 className="font-semibold text-[var(--im-text)]">Still need help?</h3>
          <p className="mt-1 text-sm text-[var(--im-text-muted)]">Open a support ticket and we'll get back to you.</p>
          <Link to="/contact-support" className="im-btn im-btn-primary mt-3">
            Contact support
          </Link>
        </div>
      </div>
    </AppShell>
  );
}
