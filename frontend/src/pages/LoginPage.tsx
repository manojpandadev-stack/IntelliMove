import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useLogin } from '../api/hooks';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();
  const loginMutation = useLogin();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      const result = await loginMutation.mutateAsync({ email, password });
      login(result.accessToken, result.refreshToken, result.user);
      const role = result.user.role;
      if (role === 'ADMIN' || role === 'SUPER_ADMIN') navigate('/admin');
      else if (role === 'DRIVER') navigate('/driver');
      else navigate('/dashboard');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Login failed';
      setError(msg);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--im-canvas)]">
      <div className="w-full max-w-md p-8 rounded-xl border border-[var(--im-border)] bg-[var(--im-surface)] shadow-md">
        <h1 className="text-2xl font-bold text-center mb-6 text-[var(--im-text)]">IntelliMove</h1>
        <p className="text-center text-[var(--im-text-muted)] mb-6">Sign in to your account</p>
        {error && <div className="mb-4 p-3 bg-red-50 text-[#FCA5A5] rounded text-sm border border-[rgb(239_68_68/0.35)]">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-[var(--im-text-secondary)] mb-1">Email</label>
            <input
              type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              placeholder="Email"
              className="w-full px-3 py-2 border border-[var(--im-input-border)] rounded-md focus:outline-none focus:ring-2 focus:ring-[var(--im-focus-ring)]"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-[var(--im-text-secondary)] mb-1">Password</label>
            <input
              type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              className="w-full px-3 py-2 border border-[var(--im-input-border)] rounded-md focus:outline-none focus:ring-2 focus:ring-[var(--im-focus-ring)]"
              required
            />
          </div>
          <button
            type="submit" disabled={loginMutation.isPending}
            className="w-full py-2 px-4 bg-[var(--im-brand-600)] text-[#FFFFFF] rounded-md hover:bg-[var(--im-brand-700)] disabled:opacity-50 font-medium"
          >
            {loginMutation.isPending ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
        <p className="mt-4 text-center text-sm text-[var(--im-text-secondary)]">
          Don't have an account? <Link to="/register" style={{ color: 'var(--im-bright)' }} className="hover:underline">Register</Link>
        </p>
      </div>
    </div>
  );
}
