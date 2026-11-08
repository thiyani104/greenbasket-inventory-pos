import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Category, Settings, Supplier } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Button, Card, ErrorBox, Field, Modal, PageHeader, Spinner, Table, Td, useLoad, useToast } from '../components/ui';

const FIELDS: Array<{ key: string; label: string; hint?: string; type?: string }> = [
  { key: 'store.name', label: 'Store name' },
  { key: 'store.address', label: 'Address (shown on receipts)' },
  { key: 'currency', label: 'Currency symbol' },
  { key: 'tax.rate', label: 'Tax rate %', type: 'number', hint: 'Added to every sale after discount' },
  { key: 'expiry.warn.days', label: 'Expiry warning (days)', type: 'number' },
  { key: 'default.reorder', label: 'Default reorder level', type: 'number' },
  { key: 'daily.target', label: 'Daily sales target', type: 'number', hint: '0 = no target' },
  { key: 'receipt.footer', label: 'Receipt footer' },
];

export default function SettingsPage() {
  const { settings, reloadSettings } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState<Settings>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => setForm(settings), [settings]);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const body: Settings = {};
      for (const f of FIELDS) body[f.key] = form[f.key] ?? '';
      await api('/api/settings', { method: 'PUT', body });
      await reloadSettings();
      toast('ok', 'Settings saved');
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not save'); }
    finally { setBusy(false); }
  }

  return (
    <>
      <PageHeader title="Settings" subtitle="Store details, tax and thresholds" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Store">
          <div className="grid gap-4 sm:grid-cols-2">
            {FIELDS.map((f) => (
              <Field key={f.key} label={f.label} hint={f.hint}>
                <input type={f.type ?? 'text'} step={f.type === 'number' ? 'any' : undefined} value={form[f.key] ?? ''} onChange={(e) => setForm({ ...form, [f.key]: e.target.value })} />
              </Field>
            ))}
          </div>
          <div className="mt-4 space-y-3"><ErrorBox message={error} /><Button busy={busy} onClick={() => void save()}>Save settings</Button></div>
        </Card>
        <div className="space-y-4"><Categories /><Suppliers /></div>
      </div>
    </>
  );
}

function Categories() {
  const toast = useToast();
  const list = useLoad(() => api<Category[]>('/api/categories'), []);
  const [name, setName] = useState('');
  async function add() {
    try { await api('/api/categories', { method: 'POST', body: { name } }); setName(''); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }
  async function rename(c: Category) {
    const n = window.prompt('New name', c.name);
    if (!n || n === c.name) return;
    try { await api(`/api/categories/${c.id}`, { method: 'PUT', body: { name: n } }); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }
  async function remove(c: Category) {
    if (!window.confirm(`Delete category "${c.name}"?`)) return;
    try { await api(`/api/categories/${c.id}`, { method: 'DELETE' }); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }
  return (
    <Card title="Categories">
      <div className="mb-3 flex gap-2"><input placeholder="New category" value={name} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && name.trim() && void add()} /><Button disabled={!name.trim()} onClick={() => void add()}>Add</Button></div>
      {list.loading ? <Spinner /> : (
        <ul className="flex flex-wrap gap-2">
          {list.data?.map((c) => (
            <li key={c.id} className="flex items-center gap-1 rounded-full bg-slate-100 py-1 pl-3 pr-1 text-sm">
              {c.name}
              <button aria-label={`Rename ${c.name}`} className="rounded-full px-1.5 text-slate-400 hover:text-slate-700" onClick={() => void rename(c)}>✎</button>
              <button aria-label={`Delete ${c.name}`} className="rounded-full px-1.5 text-slate-400 hover:text-red-600" onClick={() => void remove(c)}>✕</button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Suppliers() {
  const toast = useToast();
  const list = useLoad(() => api<Supplier[]>('/api/suppliers'), []);
  const [edit, setEdit] = useState<Supplier | 'new' | null>(null);
  async function remove(s: Supplier) {
    if (!window.confirm(`Delete supplier "${s.name}"?`)) return;
    try { await api(`/api/suppliers/${s.id}`, { method: 'DELETE' }); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }
  return (
    <Card title="Suppliers" action={<Button variant="secondary" onClick={() => setEdit('new')}>+ Add</Button>}>
      {list.loading ? <Spinner /> : (
        <Table head={['Name', 'Phone', 'E-mail', '']}>
          {list.data?.map((s) => (
            <tr key={s.id}><Td className="font-medium">{s.name}</Td><Td>{s.phone}</Td><Td>{s.email}</Td>
              <Td className="whitespace-nowrap text-right"><Button variant="ghost" onClick={() => setEdit(s)}>Edit</Button><Button variant="ghost" className="text-red-600" onClick={() => void remove(s)}>Delete</Button></Td></tr>
          ))}
        </Table>
      )}
      {edit && <SupplierForm supplier={edit === 'new' ? null : edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); list.reload(); }} />}
    </Card>
  );
}

function SupplierForm({ supplier, onClose, onSaved }: { supplier: Supplier | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({ name: supplier?.name ?? '', phone: supplier?.phone ?? '', email: supplier?.email ?? '', address: supplier?.address ?? '' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function save() {
    setBusy(true);
    setError(null);
    try {
      const body = { ...f, email: f.email || null };
      if (supplier) await api(`/api/suppliers/${supplier.id}`, { method: 'PUT', body });
      else await api('/api/suppliers', { method: 'POST', body });
      onSaved();
    } catch (e) { setError(e instanceof Error ? e.message : 'Failed'); }
    finally { setBusy(false); }
  }
  return (
    <Modal title={supplier ? 'Edit supplier' : 'Add supplier'} onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button busy={busy} onClick={() => void save()}>Save</Button></>}>
      <div className="space-y-4">
        <Field label="Name"><input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} autoFocus /></Field>
        <Field label="Phone"><input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
        <Field label="E-mail"><input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <Field label="Address"><input value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} /></Field>
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}
