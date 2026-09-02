import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';
import { useAuth } from '../context/AuthContext';
import { usePreferences, useUpdatePreferences, useSavedPlaces, useCreateSavedPlace, useDeleteSavedPlace } from '../api/hooks';
import type { SavedPlace } from '../api/types';

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [showSavedPlaces, setShowSavedPlaces] = useState(false);
  const { data: prefs, isLoading: prefsLoading } = usePreferences();
  const updatePrefs = useUpdatePreferences();
  const { data: savedPlaces = [], refetch: refetchPlaces } = useSavedPlaces();
  const createPlace = useCreateSavedPlace();
  const deletePlace = useDeleteSavedPlace();

  const [newLabel, setNewLabel] = useState('');
  const [newAddress, setNewAddress] = useState('');
  const [newLat, setNewLat] = useState('');
  const [newLng, setNewLng] = useState('');
  const [newType, setNewType] = useState<'HOME' | 'WORK' | 'OTHER'>('OTHER');

  const handleSavePrefs = () => {
    if (!prefs) return;
    updatePrefs.mutate({
      emailNotifications: prefs.emailNotifications,
      smsNotifications: prefs.smsNotifications,
      pushNotifications: prefs.pushNotifications,
      darkMode: prefs.darkMode,
      currency: prefs.currency,
    });
  };

  const togglePref = (key: keyof NonNullable<typeof prefs>) => {
    if (!prefs) return;
    const updated = { ...prefs, [key]: !prefs[key] } as typeof prefs;
    updatePrefs.mutate(updated);
  };

  const handleAddPlace = () => {
    if (!newLabel || !newAddress || !newLat || !newLng || !user?.id) return;
    createPlace.mutate(
      {
        userId: user.id,
        label: newLabel,
        address: newAddress,
        latitude: parseFloat(newLat),
        longitude: parseFloat(newLng),
        type: newType,
      },
      {
        onSuccess: () => {
          setNewLabel(''); setNewAddress(''); setNewLat(''); setNewLng(''); setNewType('OTHER');
          refetchPlaces();
        },
      }
    );
  };

  const handleDeletePlace = (id: string) => {
    if (window.confirm('Remove this saved place?')) {
      deletePlace.mutate(id, { onSuccess: () => refetchPlaces() });
    }
  };

  const doLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <AppShell title="Settings">
      <div className="im-fade-up max-w-3xl space-y-6">
        <h1 className="text-2xl font-bold tracking-tight text-[var(--im-text)]">Settings</h1>

        <section className="im-card im-card-pad">
          <h2 className="font-semibold text-[var(--im-text)] mb-4 flex items-center gap-2">
            <Icon name="bell" size={18} /> Preferences
          </h2>
          {prefsLoading ? (
            <div className="space-y-2 py-2" aria-hidden="true">
              <div className="im-skeleton h-4 w-3/4" />
              <div className="im-skeleton h-4 w-1/2" />
            </div>
          ) : !prefs ? (
            <p className="text-sm text-[var(--im-text-muted)]">Preferences could not be loaded.</p>
          ) : (
            <div className="space-y-3">
              <ToggleRow label="Email notifications" checked={prefs.emailNotifications}
                onChange={() => togglePref('emailNotifications')} data-testid="toggle-email" />
              <ToggleRow label="SMS notifications" checked={prefs.smsNotifications}
                onChange={() => togglePref('smsNotifications')} data-testid="toggle-sms" />
              <ToggleRow label="Push notifications" checked={prefs.pushNotifications}
                onChange={() => togglePref('pushNotifications')} data-testid="toggle-push" />
              <ToggleRow label="Dark mode" checked={prefs.darkMode}
                onChange={() => togglePref('darkMode')} data-testid="toggle-dark" />
              <div className="flex items-center justify-between py-2">
                <span className="text-sm font-medium text-[var(--im-text-secondary)]">Currency</span>
                <select className="im-input !w-auto !max-w-[120px]" value={prefs.currency}
                  onChange={(e) => updatePrefs.mutate({ ...prefs, currency: e.target.value })} data-testid="currency-select">
                  <option value="USD">USD</option>
                  <option value="EUR">EUR</option>
                  <option value="GBP">GBP</option>
                </select>
              </div>
            </div>
          )}
          {prefs && (
            <button className="im-btn im-btn-secondary mt-4 text-sm" onClick={handleSavePrefs}>
              Save preferences
            </button>
          )}
        </section>

        <section className="im-card im-card-pad">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold text-[var(--im-text)] flex items-center gap-2">
              <Icon name="map-pin" size={18} /> Saved places
            </h2>
            <button className="im-btn im-btn-ghost" onClick={() => setShowSavedPlaces(!showSavedPlaces)}>
              <Icon name={showSavedPlaces ? 'x' : 'help'} size={14} />
              {showSavedPlaces ? 'Close' : 'Manage'}
            </button>
          </div>
          {!showSavedPlaces ? (
            <p className="text-sm text-[var(--im-text-muted)]">
              {savedPlaces.length === 0
                ? 'No saved places yet. Click Manage to add home, work, and other favorite locations.'
                : `${savedPlaces.length} saved place${savedPlaces.length === 1 ? '' : 's'}. Click Manage to edit.`}
            </p>
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <div><input className="im-input" placeholder="Label (e.g. Home)" value={newLabel}
                  onChange={(e) => setNewLabel(e.target.value)} data-testid="place-label" /></div>
                <div><input className="im-input" placeholder="Address" value={newAddress}
                  onChange={(e) => setNewAddress(e.target.value)} data-testid="place-address" /></div>
                <div><input className="im-input" type="number" step="any" placeholder="Latitude" value={newLat}
                  onChange={(e) => setNewLat(e.target.value)} data-testid="place-lat" /></div>
                <div><input className="im-input" type="number" step="any" placeholder="Longitude" value={newLng}
                  onChange={(e) => setNewLng(e.target.value)} data-testid="place-lng" /></div>
                <div className="sm:col-span-2 flex items-center justify-between">
                  <select className="im-input !w-auto" value={newType}
                    onChange={(e) => setNewType(e.target.value as 'HOME' | 'WORK' | 'OTHER')} data-testid="place-type">
                    <option value="HOME">Home</option>
                    <option value="WORK">Work</option>
                    <option value="OTHER">Other</option>
                  </select>
                  <button className="im-btn im-btn-primary" onClick={handleAddPlace}
                    disabled={!newLabel || !newAddress || !newLat || !newLng} data-testid="add-place">
                    Add place
                  </button>
                </div>
              </div>
              <ul className="space-y-2" data-testid="saved-places-list">
                {savedPlaces.length === 0 ? (
                  <li className="text-sm text-[var(--im-text-muted)]">No saved places.</li>
                ) : (
                  savedPlaces.map((p: SavedPlace) => (
                    <li key={p.id} className="flex items-center justify-between rounded-lg border p-3"
                      style={{ borderColor: 'var(--im-border)' }}>
                      <div className="min-w-0">
                        <span className="block font-medium text-[var(--im-text)]">{p.label}</span>
                        <span className="block text-sm text-[var(--im-text-muted)] truncate">{p.address}</span>
                      </div>
                      <button className="im-btn im-btn-ghost im-btn-danger"
                        onClick={() => handleDeletePlace(p.id)} data-testid={`delete-place-${p.id}`}>
                        <Icon name="x" size={14} /> Delete
                      </button>
                    </li>
                  ))
                )}
              </ul>
            </div>
          )}
        </section>

        <section className="im-card im-card-pad">
          <h2 className="font-semibold text-[var(--im-text)] mb-4 flex items-center gap-2">
            <Icon name="logout" size={18} /> Account
          </h2>
          <button className="im-btn im-btn-danger w-full sm:w-auto" onClick={doLogout} data-testid="settings-logout">
            <Icon name="logout" size={16} /> Log out of IntelliMove
          </button>
        </section>
      </div>
    </AppShell>
  );
}

function ToggleRow({
  label, checked, onChange, 'data-testid': testId,
}: {
  label: string; checked: boolean; onChange: () => void; 'data-testid'?: string;
}) {
  return (
    <label className={`flex items-center justify-between cursor-pointer py-2 ${testId || ''}`}>
      <span className="text-sm font-medium text-[var(--im-text-secondary)]">{label}</span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={onChange}
        data-testid={testId}
        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
          checked ? 'bg-[var(--im-brand-600)]' : 'bg-[var(--im-input-border)]'
        }`}
      >
        <span className="absolute inset-0 grid place-items-center">
          <span
            className={`h-3.5 w-3.5 rounded-full bg-white transition-transform ${
              checked ? 'translate-x-2.5' : '-translate-x-2.5'
            }`}
          />
        </span>
      </button>
    </label>
  );
}

