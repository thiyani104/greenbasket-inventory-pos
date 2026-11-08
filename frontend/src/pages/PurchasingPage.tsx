import { useState } from 'react';
import { api } from '../api/client';
import type { Page, PoStatus, Product, PurchaseOrder, ReorderSuggestion, Supplier } from '../api/types';
import { Button, Card, Empty, ErrorBox, Field, Modal, PageHeader, PoBadge, Spinner, Table, Td, useLoad, useToast } from '../components/ui';
import { dateTime, money } from '../lib/format';

const FILTERS: Array<PoStatus | ''> = ['', 'DRAFT', 'ORDERED', 'RECEIVED', 'CANCELLED'];

export default function PurchasingPage() {
  const toast = useToast();
  const [status, setStatus] = useState<PoStatus | ''>('');
  const [creating, setCreating] = useState(false);
  const [open, setOpen] = useState<PurchaseOrder | null>(null);
  const list = useLoad(() => api<PurchaseOrder[]>('/api/purchase-orders', { query: { status } }), [status]);

  async function act(po: PurchaseOrder, action: 'order' | 'receive' | 'cancel') {
    const text = { order: 'Mark as ordered?', receive: 'Receive everything still outstanding into stock?', cancel: 'Cancel this order?' }[action];
    if (!window.confirm(`${po.poNumber}: ${text}`)) return;
    try {
      await api(`/api/purchase-orders/${po.id}/${action}`, { method: 'POST', body: action === 'receive' ? {} : undefined });
      toast('ok', action === 'receive' ? 'Stock received' : 'Order updated');
      setOpen(null);
      list.reload();
    } catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }

  return (
    <>
      <PageHeader title="Purchasing" subtitle="Order stock from suppliers and receive deliveries" actions={<Button onClick={() => setCreating(true)}>+ New order</Button>} />
      <Card>
        <div className="mb-4 flex flex-wrap gap-2">
          {FILTERS.map((f) => (
            <button key={f} onClick={() => setStatus(f)} className={`rounded-full px-3 py-1 text-xs font-semibold ${status === f ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}>{f || 'All'}</button>
          ))}
        </div>
        {list.loading ? <Spinner /> : list.error ? <ErrorBox message={list.error} /> : !list.data?.length ? <Empty>No purchase orders yet.</Empty> : (
          <Table head={['Order', 'Supplier', 'Created', 'Lines', 'Total', 'Status', '']}>
            {list.data.map((po) => (
              <tr key={po.id}>
                <Td className="font-mono font-semibold">{po.poNumber}</Td><Td>{po.supplier ?? '—'}</Td><Td className="whitespace-nowrap">{dateTime(po.createdAt)}</Td>
                <Td>{po.items.length}</Td><Td className="font-semibold">{money(po.total)}</Td><Td><PoBadge status={po.status} /></Td>
                <Td className="whitespace-nowrap text-right">
                  <Button variant="ghost" onClick={() => setOpen(po)}>View</Button>
                  {po.status === 'DRAFT' && <Button variant="ghost" onClick={() => void act(po, 'order')}>Mark ordered</Button>}
                  {po.status === 'ORDERED' && <Button variant="ghost" onClick={() => void act(po, 'receive')}>Receive</Button>}
                  {(po.status === 'DRAFT' || po.status === 'ORDERED') && <Button variant="ghost" className="text-red-600" onClick={() => void act(po, 'cancel')}>Cancel</Button>}
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      {creating && <NewOrder onClose={() => setCreating(false)} onSaved={() => { setCreating(false); toast('ok', 'Order created'); list.reload(); }} />}
      {open && (
        <Modal title={open.poNumber} onClose={() => setOpen(null)} wide footer={<Button variant="secondary" onClick={() => setOpen(null)}>Close</Button>}>
          <p className="mb-3 text-sm text-slate-500">{open.supplier ?? 'No supplier'} · created by {open.createdBy} · <PoBadge status={open.status} />{open.notes && <> · “{open.notes}”</>}</p>
          <Table head={['Product', 'Ordered', 'Received', 'Unit cost', 'Line total']}>
            {open.items.map((i) => <tr key={i.productId}><Td className="font-medium">{i.name}</Td><Td>{i.quantityOrdered}</Td><Td>{i.quantityReceived}</Td><Td>{money(i.unitCost)}</Td><Td>{money(i.lineTotal)}</Td></tr>)}
          </Table>
          <div className="mt-3 text-right text-lg font-bold">{money(open.total)}</div>
        </Modal>
      )}
    </>
  );
}

interface DraftLine { productId: number; name: string; quantity: number; unitCost: number }

function NewOrder({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const sups = useLoad(() => api<Supplier[]>('/api/suppliers'), []);
  const prods = useLoad(() => api<Page<Product>>('/api/products', { query: { size: 200, sort: 'name' } }), []);
  const reorder = useLoad(() => api<ReorderSuggestion[]>('/api/reports/reorder'), []);
  const [supplierId, setSupplierId] = useState('');
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([]);
  const [pick, setPick] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const supplierName = sups.data?.find((s) => String(s.id) === supplierId)?.name;

  function addProduct(id: number, qty = 1) {
    const p = prods.data?.content.find((x) => x.id === id);
    if (!p || lines.some((l) => l.productId === id)) return;
    setLines((ls) => [...ls, { productId: p.id, name: p.name, quantity: qty, unitCost: p.cost }]);
  }
  function fromSuggestions() {
    const picks = (reorder.data ?? []).filter((r) => !supplierName || r.supplier === supplierName);
    const next = [...lines];
    for (const r of picks) {
      if (!next.some((l) => l.productId === r.productId)) next.push({ productId: r.productId, name: r.name, quantity: r.suggestedOrder, unitCost: r.unitCost });
    }
    setLines(next);
  }
  const update = (id: number, patch: Partial<DraftLine>) => setLines((ls) => ls.map((l) => (l.productId === id ? { ...l, ...patch } : l)));
  const total = lines.reduce((s, l) => s + l.quantity * l.unitCost, 0);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await api('/api/purchase-orders', {
        method: 'POST',
        body: { supplierId: supplierId ? Number(supplierId) : null, notes, items: lines.map((l) => ({ productId: l.productId, quantity: l.quantity, unitCost: l.unitCost })) },
      });
      onSaved();
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not create order'); }
    finally { setBusy(false); }
  }

  return (
    <Modal title="New purchase order" onClose={onClose} wide
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button busy={busy} disabled={!lines.length} onClick={() => void save()}>Create order · {money(total)}</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Supplier">
          <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
            <option value="">— none —</option>{sups.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
        <Field label="Notes (optional)"><input value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={255} /></Field>
      </div>
      <div className="mt-4 flex flex-wrap items-end gap-2">
        <div className="min-w-[14rem] flex-1">
          <Field label="Add product">
            <select value={pick} onChange={(e) => { if (e.target.value) addProduct(Number(e.target.value)); setPick(''); }}>
              <option value="">Choose…</option>{prods.data?.content.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </Field>
        </div>
        <Button variant="secondary" disabled={!reorder.data?.length} onClick={fromSuggestions}>Fill from low stock{supplierName ? ` (${supplierName})` : ''}</Button>
      </div>
      <div className="mt-4">
        {!lines.length ? <Empty>No products added yet.</Empty> : (
          <Table head={['Product', 'Quantity', 'Unit cost', 'Line total', '']}>
            {lines.map((l) => (
              <tr key={l.productId}>
                <Td className="font-medium">{l.name}</Td>
                <Td className="w-28"><input type="number" min="1" value={l.quantity} onChange={(e) => update(l.productId, { quantity: Math.max(1, parseInt(e.target.value || '1', 10)) })} /></Td>
                <Td className="w-32"><input type="number" min="0" step="0.01" value={l.unitCost} onChange={(e) => update(l.productId, { unitCost: Math.max(0, parseFloat(e.target.value || '0')) })} /></Td>
                <Td>{money(l.quantity * l.unitCost)}</Td>
                <Td><button aria-label={`Remove ${l.name}`} className="text-slate-400 hover:text-red-600" onClick={() => setLines((ls) => ls.filter((x) => x.productId !== l.productId))}>✕</button></Td>
              </tr>
            ))}
          </Table>
        )}
      </div>
      <div className="mt-3"><ErrorBox message={error} /></div>
    </Modal>
  );
}
