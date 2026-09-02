import { useState } from 'react';
import Icon from '../components/Icon';
import AppShell from '../components/AppShell';
import { useAiQuery } from '../api/hooks';

const SUGGESTED = [
  'How many rides today?',
  'What is the cancellation rate?',
  'Which drivers are currently active?',
  'Show today\'s ride statistics.',
];

interface Turn {
  q: string;
  a?: string;
  tools?: string[];
  error?: string;
  at: string;
}

export default function AdminAiPage() {
  const ai = useAiQuery();
  const [input, setInput] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);

  const ask = (q: string) => {
    const query = q.trim();
    if (!query || ai.isPending) return;
    setInput('');
    setTurns((t) => [...t, { q: query, at: new Date().toISOString() }]);
    ai.mutate(query, {
      onSuccess: (data) =>
        setTurns((t) =>
          t.map((turn, i) =>
            i === t.length - 1 ? { ...turn, a: data.analysis, tools: data.toolsUsed } : turn
          )
        ),
      onError: (err) => {
        const resp = err as { response?: { status?: number } };
        const offline = !resp.response;
        setTurns((t) =>
          t.map((turn, i) =>
            i === t.length - 1
              ? {
                  ...turn,
                  error: offline
                    ? 'The AI service is unreachable right now. Verify the Ollama provider is running and try again.'
                    : 'The AI service could not answer this question. Please try again shortly.',
                }
              : turn
          )
        );
      },
    });
  };

  return (
    <AppShell>
      <div className="mb-6">
        <h1 className="flex items-center gap-2 text-2xl font-bold tracking-tight">
          <Icon name="sparkles" size={22} style={{ color: 'var(--im-brand-600)' }} /> Ask IntelliMove Operations
        </h1>
        <p className="text-sm text-[var(--im-text-muted)]">
          Natural-language operations analytics. Answers are produced by live tool calls against platform data — never fabricated.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[260px_1fr]">
        <aside className="im-card im-card-pad h-fit" aria-label="Suggested questions">
          <p className="im-label">Try asking</p>
          <ul className="space-y-2">
            {SUGGESTED.map((q) => (
              <li key={q}>
                <button
                  className="w-full rounded-lg border border-[var(--im-border)] p-2.5 text-left text-sm text-[var(--im-text-secondary)] transition hover:bg-[rgb(244_63_127/0.08)] disabled:opacity-50"
                  onClick={() => ask(q)}
                  disabled={ai.isPending}
                >
                  {q}
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <section className="flex min-h-[420px] flex-col im-card im-card-pad">
          <div className="flex-1 space-y-4 overflow-y-auto" data-testid="ai-transcript" aria-live="polite">
            {turns.length === 0 && (
              <div className="grid h-full place-items-center text-center text-[var(--im-text-muted)]">
                <span>
                  <Icon name="sparkles" size={30} className="mx-auto mb-3 opacity-40" />
                  <span className="block text-sm">Ask a question to see live operational analysis.</span>
                </span>
              </div>
            )}

            {turns.map((turn, i) => (
              <div key={i} className="space-y-2">
                <p className="ml-auto w-fit max-w-[80%] rounded-2xl rounded-br-sm bg-[var(--im-brand-600)] px-4 py-2 text-sm text-[#FFFFFF]">
                  {turn.q}
                </p>
                {turn.a && (
                  <div className="im-fade-up max-w-[90%] rounded-2xl rounded-bl-sm border border-[var(--im-border)] bg-[var(--im-canvas)] px-4 py-3 text-sm text-[var(--im-text)] whitespace-pre-wrap">
                    {turn.a}
                    {turn.tools && turn.tools.length > 0 && (
                      <p className="mt-2 flex flex-wrap items-center gap-1 text-xs text-[var(--im-text-muted)]">
                        <Icon name="activity" size={12} />
                        tools: {turn.tools.join(', ')}
                      </p>
                    )}
                    <p className="mt-1 text-xs text-[var(--im-text-muted)]">{new Date(turn.at).toLocaleTimeString()}</p>
                  </div>
                )}
                {turn.error && (
                  <p className="im-alert-error max-w-[90%]" role="alert">
                    <Icon name="alert" size={15} /> {turn.error}
                  </p>
                )}
                {!turn.a && !turn.error && (
                  <p className="flex w-fit items-center gap-2 rounded-2xl border border-[var(--im-border)] bg-[var(--im-canvas)] px-4 py-2 text-sm text-[var(--im-text-muted)]">
                    <Icon name="clock" size={14} className="im-spin" />
                    Analyzing platform data…
                  </p>
                )}
              </div>
            ))}
          </div>

          <form
            className="mt-4 flex gap-2 border-t border-[var(--im-border)] pt-4"
            onSubmit={(e) => {
              e.preventDefault();
              ask(input);
            }}
          >
            <label htmlFor="ai-q" className="sr-only">Ask a question</label>
            <input
              id="ai-q"
              className="im-input"
              placeholder="e.g. How many rides were completed today?"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              disabled={ai.isPending}
              data-testid="ai-input"
            />
            <button type="submit" className="im-btn im-btn-primary shrink-0" disabled={ai.isPending || !input.trim()}>
              {ai.isPending ? 'Thinking…' : 'Ask'}
            </button>
          </form>
        </section>
      </div>
    </AppShell>
  );
}
