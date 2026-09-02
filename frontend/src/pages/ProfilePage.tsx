import { useRef, useState } from 'react';
import AppShell from '../components/AppShell';
import Icon from '../components/Icon';
import Avatar from '../components/Avatar';
import { useAuth } from '../context/AuthContext';
import {
  useGetUser,
  useProfilePhoto,
  useRemoveProfilePhoto,
  useUpdateUser,
  useUploadProfilePhoto,
} from '../api/hooks';

function Row({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex items-center justify-between gap-4 py-3">
      <dt className="text-sm text-[var(--im-text-muted)]">{label}</dt>
      <dd className="text-sm font-medium text-[var(--im-text)]">{value || '—'}</dd>
    </div>
  );
}

/** Client-side pre-checks mirror the server rules (server re-validates). */
const ALLOWED_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_PHOTO_BYTES = 2 * 1024 * 1024;

export default function ProfilePage() {
  const { user, login, logout } = useAuth();
  const { data: profile, isLoading } = useGetUser(user?.id ?? '');
  const updateUser = useUpdateUser();
  const uploadPhoto = useUploadProfilePhoto();
  const removePhoto = useRemoveProfilePhoto();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ firstName: user?.firstName ?? '', lastName: user?.lastName ?? '', phoneNumber: '' });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // ─── Photo state ────────────────────────────────────────────────
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [photoSuccess, setPhotoSuccess] = useState<string | null>(null);

  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase();
  // Server-side photo presence drives the button states (upload vs change/remove).
  const { data: photoBlob, isError: photoMissing } = useProfilePhoto(user?.id);
  const hasPhoto = photoBlob instanceof Blob && !photoMissing;

  /** Client-side pre-checks, then show a local preview until saved. */
  const handleFileChosen = (file: File | undefined) => {
    setPhotoError(null);
    setPhotoSuccess(null);
    if (!file) return;
    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      setPhotoError('Please choose a JPG, PNG or WebP image.');
      return;
    }
    if (file.size > MAX_PHOTO_BYTES) {
      setPhotoError('Photo must be 2 MB or smaller.');
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPendingFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const clearPreview = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    setPendingFile(null);
  };

  const handleSavePhoto = async () => {
    if (!pendingFile || !user) return;
    setPhotoBusy(true);
    setPhotoError(null);
    try {
      await uploadPhoto.mutateAsync({ userId: user.id, file: pendingFile });
      clearPreview();
      setPhotoSuccess('Profile photo updated.');
      setTimeout(() => setPhotoSuccess(null), 3000);
    } catch {
      setPhotoError('Upload failed. Please use a JPG/PNG/WebP image up to 2 MB and try again.');
    } finally {
      setPhotoBusy(false);
    }
  };

  const handleRemovePhoto = async () => {
    if (!user) return;
    setPhotoBusy(true);
    setPhotoError(null);
    try {
      await removePhoto.mutateAsync(user.id);
      clearPreview();
      setPhotoSuccess('Profile photo removed.');
      setTimeout(() => setPhotoSuccess(null), 3000);
    } catch {
      setPhotoError('Could not remove the photo. Please try again.');
    } finally {
      setPhotoBusy(false);
    }
  };


  const startEdit = () => {
    setEditing(true);
    setForm({
      firstName: profile?.firstName ?? user?.firstName ?? '',
      lastName: profile?.lastName ?? user?.lastName ?? '',
      phoneNumber: profile?.phoneNumber ?? '',
    });
    setSaveError(null); setSaveSuccess(false);
  };

  const handleSave = async () => {
    setSaving(true); setSaveError(null); setSaveSuccess(false);
    try {
            const updated = await updateUser.mutateAsync({
        id: user?.id ?? '',
        firstName: form.firstName,
        lastName: form.lastName,
        phoneNumber: form.phoneNumber || undefined,
      });
      if (user) {
        login(localStorage.getItem('accessToken') ?? '', localStorage.getItem('refreshToken') ?? '', {
          id: updated.id, email: updated.email, firstName: updated.firstName, lastName: updated.lastName, role: updated.role,
        });
      }
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2500);
      setEditing(false);
    } catch {
      setSaveError('Unable to save changes. Please try again.');
    } finally {
      setSaving(false);
    }
  };
    return (
    <AppShell title="Profile">
      <div className="im-fade-up max-w-3xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight text-[var(--im-text)]">Profile</h1>
          {!editing ? (
            <button className="im-btn im-btn-secondary" onClick={startEdit} data-testid="profile-edit">
              <Icon name="help" size={15} /> Edit
            </button>
          ) : (
            <button className="im-btn im-btn-secondary" onClick={() => { setEditing(false); setSaveError(null); }} data-testid="profile-cancel">
              <Icon name="x" size={15} /> Cancel
            </button>
          )}
        </div>

        <div className="grid gap-6 md:grid-cols-[300px_1fr]">
          <div className="im-card im-card-pad text-center" data-testid="profile-identity">
            {/* Avatar: live preview of a chosen file, else the stored photo, else initials */}
            <div className="mx-auto w-fit" data-testid="profile-avatar">
              {previewUrl ? (
                <img
                  src={previewUrl}
                  alt="Profile photo preview"
                  data-testid="photo-preview"
                  className="mx-auto h-20 w-20 rounded-full object-cover ring-2 ring-[#3A1D2B]"
                />
              ) : hasPhoto ? (
                <Avatar
                  userId={user?.id}
                  firstName={user?.firstName}
                  lastName={user?.lastName}
                  size={80}
                  testId="avatar-photo"
                />
              ) : (
                <span
                  data-testid="avatar-photo-fallback"
                  className="mx-auto grid h-20 w-20 place-items-center rounded-full text-2xl font-bold text-[#FFFFFF] ring-2 ring-[#3A1D2B]"
                  style={{ background: 'linear-gradient(135deg,#E11D68,#BE185D)' }}
                >
                  {initials || <Icon name="user" size={30} />}
                </span>
              )}
            </div>
            <h2 className="mt-3 text-lg font-bold tracking-tight">
              {profile?.firstName ?? user?.firstName} {profile?.lastName ?? user?.lastName}
            </h2>
            <p className="text-sm text-[var(--im-text-muted)]">{profile?.email ?? user?.email}</p>
            <span className="im-badge mt-3"
              style={{
                background: profile?.role === 'CUSTOMER' || user?.role === 'CUSTOMER' ? 'rgba(225, 29, 104, 0.18)' : 'rgba(251, 113, 133, 0.14)',
                color: profile?.role === 'CUSTOMER' || user?.role === 'CUSTOMER' ? '#FB7185' : '#FDA4AF',
              }}>
              {profile?.role?.toLowerCase() ?? user?.role?.toLowerCase()}
            </span>

            {/* Photo management */}
            <div className="mt-4 space-y-2">
              <input
                ref={fileInputRef}
                id="photo-input"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
                data-testid="photo-input"
                onChange={(e) => {
                  handleFileChosen(e.target.files?.[0]);
                  e.target.value = '';
                }}
              />
              {pendingFile ? (
                <div className="flex flex-col gap-2">
                  <button
                    type="button"
                    className="im-btn im-btn-primary w-full"
                    onClick={handleSavePhoto}
                    disabled={photoBusy}
                    data-testid="save-photo-btn"
                  >
                    {photoBusy ? (
                      <><Icon name="clock" size={15} className="im-spin" /> Uploading…</>
                    ) : (
                      <><Icon name="check" size={15} /> Save photo</>
                    )}
                  </button>
                  <button
                    type="button"
                    className="im-btn im-btn-secondary w-full"
                    onClick={() => { clearPreview(); setPhotoError(null); }}
                    disabled={photoBusy}
                    data-testid="cancel-photo-btn"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <div className="flex flex-col gap-2">
                  <button
                    type="button"
                    className="im-btn im-btn-secondary w-full"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={photoBusy}
                    data-testid={hasPhoto ? 'change-photo-btn' : 'upload-photo-btn'}
                  >
                    <Icon name={hasPhoto ? 'eye' : 'navigation'} size={15} />
                    {photoBusy ? 'Working…' : hasPhoto ? 'Change photo' : 'Upload photo'}
                  </button>
                  {hasPhoto && (
                    <button
                      type="button"
                      className="im-btn im-btn-danger w-full !py-1.5 !text-sm"
                      onClick={handleRemovePhoto}
                      disabled={photoBusy}
                      data-testid="remove-photo-btn"
                    >
                      Remove photo
                    </button>
                  )}
                </div>
              )}
              {photoError && (
                <p className="im-alert-error" role="alert" data-testid="photo-error">
                  <Icon name="alert" size={14} /> {photoError}
                </p>
              )}
              {photoSuccess && (
                <p className="im-alert-info" role="status" data-testid="photo-success">
                  {photoSuccess}
                </p>
              )}
              <p className="text-[11px] text-[var(--im-text-muted)]">JPG, PNG or WebP · up to 2 MB</p>
            </div>
          </div>

          <div className="space-y-6">
            <section className="im-card im-card-pad">
              <h3 className="mb-1 font-semibold">Account information</h3>
              {isLoading ? (
                <div className="space-y-2 py-2" aria-hidden="true">
                  <div className="im-skeleton h-4 w-2/3" />
                  <div className="im-skeleton h-4 w-1/2" />
                </div>
              ) : editing ? (
                <div className="space-y-3" data-testid="profile-edit-form">
                  <div><label className="im-label" htmlFor="firstName">First name</label>
                    <input id="firstName" className="im-input" value={form.firstName}
                      onChange={(e) => setForm({ ...form, firstName: e.target.value })} data-testid="edit-first-name" /></div>
                  <div><label className="im-label" htmlFor="lastName">Last name</label>
                    <input id="lastName" className="im-input" value={form.lastName}
                      onChange={(e) => setForm({ ...form, lastName: e.target.value })} data-testid="edit-last-name" /></div>
                  <div><label className="im-label" htmlFor="phoneNumber">Phone</label>
                    <input id="phoneNumber" className="im-input" placeholder="+1 (555) 000-0000"
                      value={form.phoneNumber} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} data-testid="edit-phone" /></div>
                  {saveError && <p className="im-alert-error" role="alert">{saveError}</p>}
                  {saveSuccess && <p className="im-alert-info">Profile updated successfully.</p>}
                  <button className="im-btn im-btn-primary" onClick={handleSave} disabled={saving} data-testid="profile-save">
                    {saving ? <>Saving…</> : 'Save changes'}
                  </button>
                </div>
              ) : (
                <dl className="divide-y divide-[var(--im-border)]">
                  <Row label="First name" value={profile?.firstName ?? user?.firstName} />
                  <Row label="Last name" value={profile?.lastName ?? user?.lastName} />
                  <Row label="Email" value={profile?.email ?? user?.email} />
                  <Row label="Phone" value={profile?.phoneNumber} />
                  <Row label="Member since" value={profile?.createdAt ? new Date(profile.createdAt).toLocaleDateString() : null} />
                </dl>
              )}
            </section>

            <section className="im-card im-card-pad">
              <h3 className="mb-3 font-semibold">Security</h3>
              <div className="flex items-start gap-3 rounded-xl p-3" style={{ background: 'rgba(225, 29, 104, 0.10)', border: '1px solid rgba(225, 29, 104, 0.30)' }}>
                <Icon name="shield" size={18} style={{ color: '#FB7185', marginTop: 2 }} />
                <p className="text-sm" style={{ color: '#FDA4AF' }}>
                  Your sessions are protected with JWT access and refresh tokens. Password changes and
                  two-factor authentication are handled by the IntelliMove auth service.
                </p>
              </div>
              <button className="im-btn im-btn-danger mt-4 w-full sm:w-auto" onClick={logout} data-testid="profile-logout">
                <Icon name="logout" size={16} /> Log out of IntelliMove
              </button>
            </section>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
