import { useState } from 'react';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';
import { useSavedPlaces, useCreateSavedPlace, useUpdateSavedPlace, useDeleteSavedPlace } from '../api/hooks';
import type { SavedPlace } from '../api/types';
import { useAuth } from '../context/AuthContext';

export default function SavedPlacesPage() {
  const { user } = useAuth();
  const { data: places = [], refetch, isLoading } = useSavedPlaces();
  const createPlace = useCreateSavedPlace();
  const updatePlace = useUpdateSavedPlace();
  const deletePlace = useDeleteSavedPlace();

  const [editing, setEditing] = useState<SavedPlace | 'new' | null>(null);
  const [form, setForm] = useState<Partial<SavedPlace>>({});

  const openNew = () => { setEditing('new'); setForm({}); };
  const closeForm = () => { setEditing(null); setForm({}); };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSave = () => {
    if (!user?.id || !form.label || !form.address || !form.latitude || !form.longitude) return;
    const payload = {
      userId: user.id,
      label: form.label,
      address: form.address,
      latitude: Number(form.latitude),
      longitude: Number(form.longitude),
      type: (form.type ?? 'OTHER') as 'HOME' | 'WORK' | 'OTHER',
    };
    const cb = () => { refetch(); closeForm(); };
    if (editing === 'new') createPlace.mutate(payload, { onSuccess: cb });
    else if (editing) updatePlace.mutate({ ...payload, id: editing.id }, { onSuccess: cb });
  };

  const handleDelete = (id: string) => {
    if (window.confirm('Remove this saved place?')) {
      deletePlace.mutate(id, { onSuccess: () => refetch() });
    }
  };

  if (isLoading) {
    return (
      <AppShell title="Saved places">
        <div className="space-y-3" data-testid="saved-places-loading">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="im-skeleton h-16 w-full rounded-xl" />
          ))}
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell title="Saved places">
      <div className="im-fade-up max-w-2xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight text-[var(--im-text)]">Saved places</h1>
          <button className="im-btn im-btn-primary" onClick={openNew} data-testid="add-place-btn">
            <Icon name="help" size={16} /> Add place
          </button>
        </div>

        {editing && (
          <div className="im-card im-card-pad mb-4" data-testid="place-form">
            <h2 className="font-semibold mb-3">{editing === 'new' ? 'Add place' : 'Edit place'}</h2>
            <div className="grid gap-3 sm:grid-cols-2">
              <input className="im-input sm:col-span-2" name="label" placeholder="Label"
                value={form.label ?? ''} onChange={handleChange} data-testid="place-label" />
              <input className="im-input sm:col-span-2" name="address" placeholder="Address"
                value={form.address ?? ''} onChange={handleChange} data-testid="place-address" />
              <input className="im-input" type="number" name="latitude" placeholder="Latitude"
                value={form.latitude ?? ''} onChange={handleChange} data-testid="place-lat" />
              <input className="im-input" type="number" name="longitude" placeholder="Longitude"
                value={form.longitude ?? ''} onChange={handleChange} data-testid="place-lng" />
              <select className="im-input sm:col-span-2" name="type"
                value={form.type ?? 'OTHER'} onChange={handleChange} data-testid="place-type">
                <option value="HOME">Home</option>
                <option value="WORK">Work</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <div className="mt-4 flex gap-2">
              <button className="im-btn im-btn-primary" onClick={handleSave} data-testid="save-place">
                Save
              </button>
              <button className="im-btn im-btn-secondary" onClick={closeForm}>
                Cancel
              </button>
            </div>
          </div>
        )}

        {!places.length && !editing ? (
          <div className="text-center py-10 text-[var(--im-text-muted)]">
            <Icon name="map-pin" size={36} className="mx-auto mb-3" />
            <p>No saved places yet.</p>
            <button className="im-btn im-btn-secondary mt-3" onClick={openNew}>
              Add your first place
            </button>
          </div>
        ) : (
          <ul className="space-y-3" data-testid="saved-places-list">
            {places.map((p: SavedPlace) => (
              <li key={p.id} className="im-card im-card-pad flex items-center justify-between">
                <div className="min-w-0">
                  <span className="font-medium text-[var(--im-text)]">{p.label}</span>
                  <span className="block text-sm text-[var(--im-text-muted)] truncate">{p.address}</span>
                </div>
                <div className="flex gap-2">
                  <button className="im-btn im-btn-ghost"
                    onClick={() => { setEditing(p); setForm(p); }} data-testid={`edit-place-${p.id}`}>
                    <Icon name="help" size={15} /> Edit
                  </button>
                  <button className="im-btn im-btn-danger"
                    onClick={() => handleDelete(p.id)} data-testid={`delete-place-${p.id}`}>
                    <Icon name="x" size={15} />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </AppShell>
  );
}
