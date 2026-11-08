import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { Button, ErrorBox, Field } from '../components/ui';
import { AuthShell } from './LoginPage';

export function ForgotPage() {
  const [who, setWho] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api('/api/auth/forgot-password', { method: 'POST', body: { usernameOrEmail: who.trim() }, auth: false });
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Reset password" subtitle="Enter your username or e-mail address">
      {sent ? (
        <div className="space-y-4 text-center text-sm text-slate-600">
          <p>If that account exists and has an e-mail address, a reset link has been sent.</p>
          <Link to="/reset-password" className="font-semibold text-brand-700 hover:underline">I have a reset code</Link>
          <div><Link to="/login" className="text-slate-500 hover:underline">Back to sign in</Link></div>
        </div>
      ) : (
        <form onSubmit={(e) => void submit(e)} className="space-y-4">
          <Field label="Username or e-mail"><input autoFocus value={who} onChange={(e) => setWho(e.target.value)} required /></Field>
          <ErrorBox message={error} />
          <Button type="submit" busy={busy} className="w-full">Send reset link</Button>
          <div className="text-center text-sm"><Link to="/login" className="text-slate-500 hover:underline">Back to sign in</Link></div>
        </form>
      )}
    </AuthShell>
  );
}

export function ResetPage() {
  const [params] = useSearchParams();
  const nav = useNavigate();
  const [token, setToken] = useState(params.get('token') ?? '');
  const [pw, setPw] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api('/api/auth/reset-password', { method: 'POST', body: { token: token.trim(), newPassword: pw }, auth: false });
      nav('/login', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not reset password');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Set a new password" subtitle="Paste the code from your reset e-mail">
      <form onSubmit={(e) => void submit(e)} className="space-y-4">
        <Field label="Reset code"><input value={token} onChange={(e) => setToken(e.target.value)} required /></Field>
        <Field label="New password" hint="At least 8 characters, with letters and numbers."><input type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} required /></Field>
        <ErrorBox message={error} />
        <Button type="submit" busy={busy} className="w-full">Reset password</Button>
        <div className="text-center text-sm"><Link to="/login" className="text-slate-500 hover:underline">Back to sign in</Link></div>
      </form>
    </AuthShell>
  );
}
