import { useState } from 'react';
import { api } from '../api/client';
import type { Role, StaffUser } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Button, Card, ErrorBox, Field, Modal, PageHeader, Pill, Spinner, Table, Td, useLoad, useToast } from '../components/ui';
import { dateTime } from '../lib/format';

export default function UsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const list = useLoad(() => api<StaffUser[]>('/api/users'), []);
  const [creating, setCreating] = useState(false);
  const [resetting, setResetting] = useState<StaffUser | null>(null);

  async function patch(u: StaffUser, body: Record<string, unknown>, ok: string) {
    try { await api(`/api/users/${u.id}`, { method: 'PUT', body }); toast('ok', ok); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }
  async function unlock(u: StaffUser) {
    try { await api(`/api/users/${u.id}/unlock`, { method: 'POST' }); toast('ok', 'Account unlocked'); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }

  return (
    <>
      <PageHeader title="Staff" subtitle="Accounts and roles" actions={<Button onClick={() => setCreating(true)}>+ Add staff</Button>} />
      <Card>
        {list.loading ? <Spinner /> : list.error ? <ErrorBox message={list.error} /> : (
          <Table head={['Name', 'Username', 'Role', 'Status', 'Last sign-in', '']}>
            {list.data?.map((u) => (
              <tr key={u.id} className={u.active ? '' : 'opacity-50'}>
                <Td className="font-medium">{u.fullName}{u.email && <div className="text-xs font-normal text-slate-400">{u.email}</div>}</Td>
                <Td className="font-mono text-xs">{u.username}</Td>
                <Td>{u.role === 'MANAGER' ? 'Store manager' : 'Sales assistant'}</Td>
                <Td>{!u.active ? <Pill tone="red">Disabled</Pill> : u.locked ? <Pill tone="red">Locked</Pill> : u.mustChangePassword ? <Pill>Temp password</Pill> : <Pill tone="green">Active</Pill>}</Td>
                <Td className="whitespace-nowrap">{u.lastLogin ? dateTime(u.lastLogin) : 'never'}</Td>
                <Td className="whitespace-nowrap text-right">
                  {u.locked && <Button variant="ghost" onClick={() => void unlock(u)}>Unlock</Button>}
                  <Button variant="ghost" onClick={() => setResetting(u)}>Reset password</Button>
                  <Button variant="ghost" disabled={u.id === me?.id} onClick={() => void patch(u, { role: (u.role === 'MANAGER' ? 'ASSISTANT' : 'MANAGER') as Role }, 'Role changed')}>Make {u.role === 'MANAGER' ? 'assistant' : 'manager'}</Button>
                  <Button variant="ghost" className={u.active ? 'text-red-600' : ''} disabled={u.id === me?.id} onClick={() => void patch(u, { active: !u.active }, u.active ? 'Account disabled' : 'Account enabled')}>{u.active ? 'Disable' : 'Enable'}</Button>
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      {creating && <CreateUser onClose={() => setCreating(false)} onSaved={() => { setCreating(false); toast('ok', 'Account created'); list.reload(); }} />}
      {resetting && <ResetPw user={resetting} onClose={() => setResetting(null)} onSaved={() => { setResetting(null); toast('ok', 'Password reset'); list.reload(); }} />}
    </>
  );
}

function CreateUser({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({ username: '', fullName: '', email: '', role: 'ASSISTANT' as Role, password: '' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function save() {
    setBusy(true);
    setError(null);
    try { await api('/api/users', { method: 'POST', body: { ...f, email: f.email || null } }); onSaved(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Failed'); }
    finally { setBusy(false); }
  }
  return (
    <Modal title="Add staff member" onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button busy={busy} onClick={() => void save()}>Create account</Button></>}>
      <div className="space-y-4">
        <Field label="Full name"><input value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} autoFocus /></Field>
        <Field label="Username" hint="3–30 letters, numbers, dot, dash or underscore"><input value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} /></Field>
        <Field label="E-mail (for password reset)"><input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <Field label="Role"><select value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as Role })}><option value="ASSISTANT">Sales assistant</option><option value="MANAGER">Store manager</option></select></Field>
        <Field label="Temporary password" hint="They must change it at first sign-in."><input type="text" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></Field>
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}

function ResetPw({ user, onClose, onSaved }: { user: StaffUser; onClose: () => void; onSaved: () => void }) {
  const [pw, setPw] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function save() {
    setBusy(true);
    setError(null);
    try { await api(`/api/users/${user.id}/reset-password`, { method: 'POST', body: { temporaryPassword: pw } }); onSaved(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Failed'); }
    finally { setBusy(false); }
  }
  return (
    <Modal title={`Reset password · ${user.username}`} onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button busy={busy} onClick={() => void save()}>Reset</Button></>}>
      <p className="mb-4 text-sm text-slate-500">Signs them out everywhere and unlocks the account. They choose a new password at next sign-in.</p>
      <Field label="Temporary password" hint="At least 8 characters, letters and numbers."><input type="text" value={pw} onChange={(e) => setPw(e.target.value)} autoFocus /></Field>
      <div className="mt-3"><ErrorBox message={error} /></div>
    </Modal>
  );
}
