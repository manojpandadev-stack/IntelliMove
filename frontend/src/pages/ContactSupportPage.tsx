import { useState } from 'react';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';
import { useCreateSupportTicket } from '../api/hooks';

const CATEGORIES = ['ride', 'payment', 'account', 'safety', 'other'];

export default function ContactSupportPage() {
  const createTicket = useCreateSupportTicket();
  const [subject, setSubject] = useState('');
  const [category, setCategory] = useState('ride');
  const [message, setMessage] = useState('');
  const [created, setCreated] = useState<{ id: string; subject: string } | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setCreated(null);
    if (!subject.trim() || !message.trim()) return;
    createTicket.mutate(
      { subject, category, message },
      {
        onSuccess: (ticket) => setCreated({ id: ticket.id, subject: ticket.subject }),
      }
    );
  };

  return (
    <AppShell title="Contact support">
      <div className="im-fade-up max-w-2xl">
        <h1 className="text-2xl font-bold tracking-tight text-[var(--im-text)] mb-1 flex items-center gap-3">
          <Icon name="message" size={24} /> Contact support
        </h1>
        <p className="text-[var(--im-text-muted)] mb-6">
          Open a support ticket and a member of the IntelliMove team will review your request.
        </p>

        {created ? (
          <div className="im-alert-info flex items-start gap-2">
            <Icon name="check-circle" size={18} />
            <div>
              <p className="font-semibold">Ticket created</p>
              <p className="mt-1 text-sm">
                Reference: <span className="font-mono">{created.id}</span> — "{created.subject}"
              </p>
              <p className="mt-1 text-sm">A support agent will review your ticket and respond via in-app notifications.</p>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="im-card im-card-pad space-y-4">
            <div>
              <label className="im-label" htmlFor="subject">Subject</label>
              <input
                id="subject"
                className="im-input"
                placeholder="Brief summary of your issue"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                required
                data-testid="support-subject"
              />
            </div>
            <div>
              <label className="im-label" htmlFor="category">Category</label>
              <select
                id="category"
                className="im-input"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                data-testid="support-category"
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c.charAt(0).toUpperCase() + c.slice(1)}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="im-label" htmlFor="message">Message</label>
              <textarea
                id="message"
                className="im-input min-h-[100px] w-full resize-y"
                placeholder="Describe your issue in detail..."
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                required
                data-testid="support-message"
              />
            </div>
            {createTicket.isError && (
              <p className="im-alert-error" role="alert">Unable to submit your ticket. Please try again.</p>
            )}
            <button
              type="submit"
              className="im-btn im-btn-primary w-full"
              disabled={createTicket.isPending || !subject.trim() || !message.trim()}
              data-testid="submit-ticket"
            >
              {createTicket.isPending ? (
                <>
                  <Icon name="clock" size={16} className="im-spin" /> Submitting…
                </>
              ) : (
                <>Submit ticket</>
              )}
            </button>
          </form>
        )}
      </div>
    </AppShell>
  );
}
