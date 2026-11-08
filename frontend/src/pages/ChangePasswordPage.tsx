import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Button, ErrorBox, Field } from '../components/ui';
import { AuthShell } from './LoginPage';

export default function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const nav = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [again, setAgain] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (next !== again) { setError('The two new passwords do not match.'); return; }
    setBusy(true);
    setError(null);
    try {
      await changePassword(current, next);
      nav('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not change password');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Choose a new password" subtitle={user?.mustChangePassword ? 'Your password is temporary and must be changed before you continue.' : 'Update your password'}>
      <form onSubmit={(e) => void submit(e)} className="space-y-4">
        <Field label="Current password"><input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required /></Field>
        <Field label="New password" hint="At least 8 characters, with letters and numbers."><input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required /></Field>
        <Field label="Repeat new password"><input type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} required /></Field>
        <ErrorBox message={error} />
        <Button type="submit" busy={busy} className="w-full">Save password</Button>
        <button type="button" onClick={() => void logout().then(() => nav('/login'))} className="w-full text-center text-sm text-slate-500 hover:underline">Sign out</button>
      </form>
    </AuthShell>
  );
}
