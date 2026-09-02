import { useEffect, useState } from 'react';
import Icon from './Icon';
import { useProfilePhoto } from '../api/hooks';

/**
 * Circular profile avatar.
 *
 * Photo bytes are fetched once per user through the authenticated
 * {@link useProfilePhoto} query (React Query dedupes concurrent instances)
 * and rendered from an in-memory object URL — nothing is persisted
 * browser-side; persistence is the server's job. Falls back to the user's
 * initials when no photo exists or fetching fails.
 */
export default function Avatar({
  userId,
  firstName,
  lastName,
  size = 32,
  testId = 'avatar',
}: {
  userId?: string;
  firstName?: string;
  lastName?: string;
  size?: number;
  testId?: string;
}) {
  const hasId = !!userId;
  const { data: blob, isError } = useProfilePhoto(userId);
  const [failed, setFailed] = useState(false);

  // Owns the full object-URL lifecycle (create AND revoke inside the effect).
  // This survives React StrictMode's mount → cleanup → re-setup simulation,
  // unlike a useMemo + revoke-cleanup pair which leaves the first URL revoked.
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  useEffect(() => {
    if (blob instanceof Blob) {
      const url = URL.createObjectURL(blob);
      setObjectUrl(url);
      return () => URL.revokeObjectURL(url);
    }
    setObjectUrl(null);
  }, [blob]);

  const initials = `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.toUpperCase();
  const style: React.CSSProperties = {
    width: size,
    height: size,
    fontSize: Math.max(11, Math.round(size / 2.6)),
    background: 'linear-gradient(135deg,#E11D68,#BE185D)',
  };

  if (hasId && objectUrl && !isError && !failed) {
    return (
      <img
        src={objectUrl}
        onError={() => setFailed(true)}
        alt="Profile photo"
        data-testid={testId}
        className="shrink-0 rounded-full object-cover ring-2 ring-[#3A1D2B]"
        style={{ ...style, color: 'transparent' }}
      />
    );
  }

  return (
    <span
      aria-hidden="true"
      data-testid={`${testId}-fallback`}
      className="grid shrink-0 place-items-center rounded-full font-bold text-[#FFFFFF] ring-2 ring-[#3A1D2B]"
      style={style}
    >
      {initials || <Icon name="user" size={Math.round(size * 0.55)} />}
    </span>
  );
}
