import { useEffect, useRef, useState } from 'react';
import Icon from './Icon';

export interface PlacePoint {
  lat: number;
  lng: number;
  address: string;
}

interface GeocodeHit {
  lat: number;
  lng: number;
  address: string;
}

const client = { headers: { Accept: 'application/json' } };

/**
 * Address input backed by REAL geocoding (OpenStreetMap Nominatim via the
 * Vite /geocode proxy — no API key required). Debounced, cancellable, and
 * keyboard accessible. Shows a clear state when nothing is found or the
 * service is unreachable; never fabricates results.
 */
export default function LocationPicker({
  label,
  icon,
  suggestions,
  value,
  onPick,
}: {
  label: string;
  icon: 'map-pin' | 'flag';
  suggestions: PlacePoint[];
  value: PlacePoint | null;
  onPick: (p: PlacePoint | null) => void;
}) {
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<GeocodeHit[]>([]);
  const [searching, setSearching] = useState(false);
  const [failed, setFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const [activeIdx, setActiveIdx] = useState(-1);
  const abortRef = useRef<AbortController | null>(null);
  const boxRef = useRef<HTMLDivElement>(null);

  // Close dropdown on outside click.
  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  // Debounced real geocode search.
  useEffect(() => {
    if (query.trim().length < 3 || value?.address === query) {
      setHits([]);
      return;
    }
    const t = setTimeout(async () => {
      abortRef.current?.abort();
      const ctrl = new AbortController();
      abortRef.current = ctrl;
      setSearching(true);
      setFailed(false);
      try {
        const r = await fetch(
          `/geocode/search?format=jsonv2&limit=5&q=${encodeURIComponent(query)}`,
          { signal: ctrl.signal, headers: client.headers }
        );
        if (!r.ok) throw new Error(String(r.status));
        const data = (await r.json()) as Array<{ lat: string; lon: string; display_name: string }>;
        setHits(data.map((d) => ({ lat: parseFloat(d.lat), lng: parseFloat(d.lon), address: d.display_name })));
        setActiveIdx(data.length > 0 ? 0 : -1);
        setOpen(true);
      } catch (e) {
        if ((e as Error).name !== 'AbortError') {
          setHits([]);
          setActiveIdx(-1);
          setFailed(true);
        }
      } finally {
        setSearching(false);
      }
    }, 400);
    return () => clearTimeout(t);
  }, [query, value?.address]);

  const pick = (p: PlacePoint) => {
    setQuery(p.address);
    setOpen(false);
    setHits([]);
    setActiveIdx(-1);
    onPick(p);
  };

  const clear = () => {
    setQuery('');
    setHits([]);
    setOpen(false);
    setActiveIdx(-1);
    onPick(null);
  };

  // Full combobox keyboard support: ArrowDown/Up move the active option,
  // Enter selects it, Escape dismisses the listbox.
  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!open || hits.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIdx((i) => (i + 1) % hits.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIdx((i) => (i <= 0 ? hits.length - 1 : i - 1));
    } else if (e.key === 'Enter' && activeIdx >= 0 && activeIdx < hits.length) {
      e.preventDefault();
      pick(hits[activeIdx]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <div ref={boxRef} className="relative" data-testid={`location-${icon}`}>
      <label className="im-label" htmlFor={`loc-${icon}`}>{label}</label>
      <div className="relative">
        <span aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[var(--im-text-muted)]">
          <Icon name={icon} size={17} />
        </span>
        <input
          id={`loc-${icon}`}
          className="im-input !pl-10"
          placeholder={icon === 'map-pin' ? 'Enter pickup location…' : 'Where to?'}
          autoComplete="off"
          value={value && query === '' ? value.address : query}
          onChange={(e) => {
            setQuery(e.target.value);
            if (!e.target.value) onPick(null);
          }}
          onFocus={() => hits.length > 0 && setOpen(true)}
          onKeyDown={onKeyDown}
          role="combobox"
          aria-expanded={open}
          aria-controls={`loc-list-${icon}`}
          aria-autocomplete="list"
          aria-activedescendant={open && activeIdx >= 0 ? `loc-opt-${icon}-${activeIdx}` : undefined}
          data-testid={`location-input-${icon}`}
        />
        {searching && (
          <span aria-hidden="true" className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--im-text-muted)]">
            <Icon name="clock" size={15} className="im-spin" />
          </span>
        )}
        {!searching && value && query === '' && (
          <button
            type="button"
            aria-label={`Clear ${label.toLowerCase()}`}
            title="Clear"
            onClick={clear}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full p-1 text-[var(--im-text-muted)] transition hover:bg-[rgb(244_63_127/0.08)] hover:text-[var(--im-text-secondary)]"
          >
            <Icon name="x" size={13} />
          </button>
        )}
      </div>

      {/* Saved / recent destinations from real user data — reuse their coordinates */}
      {!value && !open && suggestions.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-2">
          {suggestions.map((s) => (
            <button
              key={s.address}
              type="button"
              className="im-badge !normal-case transition hover:brightness-95"
              style={{ background: 'var(--im-elevated)', color: 'var(--im-text-secondary)', fontSize: '0.75rem', maxWidth: 260 }}
              title={s.address}
              onClick={() => pick(s)}
            >
              <Icon name="clock" size={12} /> {s.address.length > 34 ? s.address.slice(0, 34) + '…' : s.address}
            </button>
          ))}
        </div>
      )}

      {open && (
        <ul
          id={`loc-list-${icon}`}
          role="listbox"
          aria-label={label}
          className="im-card im-pop absolute z-20 mt-1 max-h-56 w-full overflow-auto p-1"
        >
          {hits.length === 0 ? (
            <li className="px-3 py-2 text-sm text-[var(--im-text-muted)]">
              {failed ? 'Location search is unavailable right now.' : searching ? 'Searching…' : 'No matches found.'}
            </li>
          ) : (
            hits.map((h, i) => (
              <li key={i}>
                <button
                  type="button"
                  id={`loc-opt-${icon}-${i}`}
                  role="option"
                  aria-selected={activeIdx === i}
                  className={`flex w-full items-start gap-2 rounded-lg px-3 py-2 text-left text-sm transition-colors ${
                    activeIdx === i ? 'bg-[rgb(225_29_104/0.12)] text-[var(--im-text)]' : 'hover:bg-[rgb(244_63_127/0.08)]'
                  }`}
                  onMouseEnter={() => setActiveIdx(i)}
                  onClick={() => pick(h)}
                >
                  <Icon name={icon} size={14} className="mt-0.5 shrink-0 text-[var(--im-text-muted)]" />
                  <span className="line-clamp-2">{h.address}</span>
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
