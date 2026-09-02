import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useRegister } from '../api/hooks';

export default function RegisterPage() {
  const [form, setForm] = useState({ email: '', password: '', firstName: '', lastName: '' });
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();
  const registerMutation = useRegister();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      const result = await registerMutation.mutateAsync(form);
      login(result.accessToken, result.refreshToken, result.user);
      navigate('/dashboard');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Registration failed';
      setError(msg);
    }
  };

  const update = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [field]: e.target.value });

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--im-canvas)]">
      <div className="w-full max-w-md p-8 rounded-xl border border-[var(--im-border)] bg-[var(--im-surface)] shadow-md">
        <h1 className="text-2xl font-bold text-center mb-6 text-[var(--im-text)]">IntelliMove</h1>
        <p className="text-center text-[var(--im-text-muted)] mb-6">Create your account</p>
        {error && <div className="mb-4 p-3 bg-red-50 text-[#FCA5A5] rounded text-sm border border-[rgb(239_68_68/0.35)]">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-[var(--im-text-secondary)] mb-1">First Name</label>
              <input value={form.firstName} onChange={update('firstName')} placeholder="First name" required
                className="w-full px-3 py-2 border border-[var(--im-input-border)] rounded-md focus:outline-none focus:ring-2 focus:ring-[var(--im-focus-ring)]" />
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--im-text-secondary)] mb-1">Last Name</label>
              <input value={form.lastName} onChange={update('lastName')} placeholder="Last name" required
                className="w-full px-3 py-2 border border-[var(--im-input-border)] rounded-md focus:outline-none focus:ring-2 focus:ring-[var(--im-focus-ring)]" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-[var(--im-text-secondary)] mb-1">Email</label>
            <input type="email" value={form.email} onChange={update('email')} placeholder="Email" required
              className="w-full px-3 py-2 border border-[var(--im-input-border)] rounded-md focus:outline-none focus:ring-2 focus:ring-[var(--im-focus-ring)]" />
          </div>
          <div>
            <label className="block text-sm font-medium text-[var(--im-text-secondary)] mb-1">Password</label>
            <input type="password" value={form.password} onChange={update('password')} placeholder="Password (min 8 characters)" required minLength={8}
              className="w-full px-3 py-2 border border-[var(--im-input-border)] rounded-md focus:outline-none focus:ring-2 focus:ring-[var(--im-focus-ring)]" />
          </div>
          <button type="submit" disabled={registerMutation.isPending}
            className="w-full py-2 px-4 bg-[var(--im-brand-600)] text-[#FFFFFF] rounded-md hover:bg-[var(--im-brand-700)] disabled:opacity-50 font-medium">
            {registerMutation.isPending ? 'Creating account...' : 'Create Account'}
          </button>
        </form>
        <p className="mt-4 text-center text-sm text-[var(--im-text-secondary)]">
          Already have an account? <Link to="/login" style={{ color: 'var(--im-bright)' }} className="hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
