import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Category, Movement, MovementType, Page, Product, ProductInput, Supplier } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Button, Card, Empty, ErrorBox, Field, Modal, PageHeader, Pager, Spinner, StatusBadge, Table, Td, useLoad, useToast } from '../components/ui';
import { date, dateTime, money } from '../lib/format';

const STATUSES = ['', 'OK', 'LOW', 'OUT', 'EXPIRING', 'EXPIRED'] as const;

export default function ProductsPage() {
  const { isManager } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [archived, setArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Product | 'new' | null>(null);
  const [adjusting, setAdjusting] = useState<Product | null>(null);
  const [history, setHistory] = useState<Product | null>(null);

  useEffect(() => { const t = setTimeout(() => { setDebounced(q); setPage(0); }, 250); return () => clearTimeout(t); }, [q]);

  const cats = useLoad(() => api<Category[]>('/api/categories'), []);
  const list = useLoad(
    () => api<Page<Product>>('/api/products', { query: { q: debounced, category, status, includeInactive: archived, page, size: 20 } }),
    [debounced, category, status, archived, page],
  );

  async function archive(p: Product) {
    if (!window.confirm(`Archive "${p.name}"? It will no longer be sold, but its history is kept.`)) return;
    try { await api(`/api/products/${p.id}`, { method: 'DELETE' }); toast('ok', 'Product archived'); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }
  async function restore(p: Product) {
    try { await api(`/api/products/${p.id}/restore`, { method: 'POST' }); toast('ok', 'Product restored'); list.reload(); }
    catch (e) { toast('err', e instanceof Error ? e.message : 'Failed'); }
  }

  return (
    <>
      <PageHeader title="Products" subtitle="Catalogue, prices and stock levels"
        actions={isManager && <Button onClick={() => setEditing('new')}>+ Add product</Button>} />
      <Card>
        <div className="mb-4 grid gap-3 md:grid-cols-4">
          <input placeholder="Search name, category or supplier…" value={q} onChange={(e) => setQ(e.target.value)} />
          <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}>
            <option value="">All categories</option>
            {cats.data?.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            {STATUSES.map((s) => <option key={s} value={s}>{s === '' ? 'Any status' : s === 'OK' ? 'In stock' : s === 'OUT' ? 'Out of stock' : s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
          </select>
          {isManager && <label className="flex items-center gap-2 text-sm text-slate-600"><input type="checkbox" checked={archived} onChange={(e) => { setArchived(e.target.checked); setPage(0); }} />Show archived</label>}
        </div>

        {list.loading ? <Spinner /> : list.error ? <ErrorBox message={list.error} /> : !list.data?.content.length ? <Empty>No products match.</Empty> : (
          <>
            <Table head={['Product', 'Category', 'Price', ...(isManager ? ['Cost', 'Margin'] : []), 'Stock', 'Expiry', 'Status', '']}>
              {list.data.content.map((p) => (
                <tr key={p.id} className={p.active ? '' : 'opacity-50'}>
                  <Td className="font-medium text-slate-900">{p.name}{p.supplier && <div className="text-xs font-normal text-slate-400">{p.supplier}</div>}</Td>
                  <Td>{p.category}</Td>
                  <Td>{money(p.price)}</Td>
                  {isManager && <><Td>{money(p.cost)}</Td><Td>{p.marginPercent.toFixed(1)}%</Td></>}
                  <Td className="font-semibold">{p.quantity}</Td>
                  <Td>{p.expiryDate ? <>{date(p.expiryDate)}<div className="text-xs text-slate-400">{p.daysToExpiry! < 0 ? `${-p.daysToExpiry!}d ago` : `in ${p.daysToExpiry}d`}</div></> : '—'}</Td>
                  <Td><StatusBadge status={p.status} /></Td>
                  <Td className="whitespace-nowrap text-right">
                    <Button variant="ghost" onClick={() => setHistory(p)}>History</Button>
                    {isManager && p.active && <>
                      <Button variant="ghost" onClick={() => setAdjusting(p)}>Stock</Button>
                      <Button variant="ghost" onClick={() => setEditing(p)}>Edit</Button>
                      <Button variant="ghost" className="text-red-600" onClick={() => void archive(p)}>Archive</Button>
                    </>}
                    {isManager && !p.active && <Button variant="ghost" onClick={() => void restore(p)}>Restore</Button>}
                  </Td>
                </tr>
              ))}
            </Table>
            <Pager page={page} totalPages={list.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>

      {editing && <ProductForm product={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); list.reload(); cats.reload(); }} />}
      {adjusting && <StockForm product={adjusting} onClose={() => setAdjusting(null)} onSaved={() => { setAdjusting(null); list.reload(); }} />}
      {history && <HistoryDialog product={history} onClose={() => setHistory(null)} />}
    </>
  );
}

function ProductForm({ product, onClose, onSaved }: { product: Product | null; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const cats = useLoad(() => api<Category[]>('/api/categories'), []);
  const sups = useLoad(() => api<Supplier[]>('/api/suppliers'), []);
  const [f, setF] = useState({
    name: product?.name ?? '', category: product?.category ?? '', supplier: product?.supplier ?? '',
    price: product ? String(product.price) : '', cost: product ? String(product.cost) : '',
    quantity: '0', reorderLevel: product ? String(product.reorderLevel) : '10', expiryDate: product?.expiryDate ?? '',
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  const p = parseFloat(f.price), c = parseFloat(f.cost);
  const margin = p > 0 && c >= 0 ? ((p - c) / p) * 100 : null;

  const save = useCallback(async () => {
    setBusy(true);
    setError(null);
    const body: ProductInput = {
      name: f.name.trim(), category: f.category.trim(), supplier: f.supplier.trim() || undefined,
      price: p, cost: c, reorderLevel: parseInt(f.reorderLevel || '0', 10), expiryDate: f.expiryDate || null,
      ...(product ? {} : { quantity: parseInt(f.quantity || '0', 10) }),
    };
    try {
      if (product) await api(`/api/products/${product.id}`, { method: 'PUT', body });
      else await api('/api/products', { method: 'POST', body });
      toast('ok', product ? 'Product updated' : 'Product added');
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setBusy(false);
    }
  }, [f, p, c, product, toast, onSaved]);

  return (
    <Modal title={product ? 'Edit product' : 'Add product'} onClose={onClose}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button busy={busy} onClick={() => void save()}>{product ? 'Save changes' : 'Add product'}</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2"><Field label="Product name"><input value={f.name} onChange={set('name')} autoFocus /></Field></div>
        <Field label="Category" hint="Pick one or type a new one">
          <input list="cat-list" value={f.category} onChange={set('category')} />
          <datalist id="cat-list">{cats.data?.map((x) => <option key={x.id} value={x.name} />)}</datalist>
        </Field>
        <Field label="Supplier (optional)">
          <input list="sup-list" value={f.supplier} onChange={set('supplier')} />
          <datalist id="sup-list">{sups.data?.map((x) => <option key={x.id} value={x.name} />)}</datalist>
        </Field>
        <Field label="Selling price"><input type="number" min="0" step="0.01" value={f.price} onChange={set('price')} /></Field>
        <Field label="Cost price"><input type="number" min="0" step="0.01" value={f.cost} onChange={set('cost')} /></Field>
        {margin !== null && <div className={`sm:col-span-2 text-sm ${c > p ? 'text-red-600' : 'text-brand-700'}`}>Margin {margin.toFixed(1)}% · profit {money(p - c)} per unit</div>}
        {!product && <Field label="Opening stock"><input type="number" min="0" value={f.quantity} onChange={set('quantity')} /></Field>}
        <Field label="Reorder level"><input type="number" min="0" value={f.reorderLevel} onChange={set('reorderLevel')} /></Field>
        <Field label="Expiry date (optional)" hint="Expired stock cannot be sold."><input type="date" value={f.expiryDate} onChange={set('expiryDate')} /></Field>
      </div>
      <div className="mt-4"><ErrorBox message={error} /></div>
    </Modal>
  );
}

function StockForm({ product, onClose, onSaved }: { product: Product; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [type, setType] = useState<MovementType>('RECEIVE');
  const [qty, setQty] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const labels: Record<string, string> = { RECEIVE: 'Stock received (+)', WASTAGE: 'Write-off: damaged / expired (−)', CORRECTION: 'Stock-take: set counted quantity' };

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/products/${product.id}/stock`, { method: 'POST', body: { type, quantity: parseInt(qty || '0', 10), note } });
      toast('ok', 'Stock updated');
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update stock');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Stock · ${product.name}`} onClose={onClose}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button busy={busy} onClick={() => void save()}>Apply</Button></>}>
      <p className="mb-4 text-sm text-slate-500">Currently <b className="text-slate-900">{product.quantity}</b> in stock.</p>
      <div className="space-y-4">
        <Field label="Change"><select value={type} onChange={(e) => setType(e.target.value as MovementType)}>{Object.entries(labels).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
        <Field label={type === 'CORRECTION' ? 'Counted quantity' : 'Quantity'}><input type="number" min="0" value={qty} onChange={(e) => setQty(e.target.value)} autoFocus /></Field>
        <Field label={type === 'WASTAGE' ? 'Reason (required)' : 'Note (optional)'}><input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} /></Field>
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}

function HistoryDialog({ product, onClose }: { product: Product; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const { data, loading, error } = useLoad(() => api<Page<Movement>>(`/api/products/${product.id}/movements`, { query: { page, size: 10 } }), [page]);
  return (
    <Modal title={`Stock history · ${product.name}`} onClose={onClose} wide footer={<Button variant="secondary" onClick={onClose}>Close</Button>}>
      {loading ? <Spinner /> : error ? <ErrorBox message={error} /> : !data?.content.length ? <Empty>No movements yet.</Empty> : (
        <>
          <Table head={['When', 'Type', 'Change', 'Balance', 'By', 'Note']}>
            {data.content.map((m) => (
              <tr key={m.id}>
                <Td className="whitespace-nowrap">{dateTime(m.createdAt)}</Td><Td>{m.type}</Td>
                <Td className={m.change < 0 ? 'font-semibold text-red-600' : 'font-semibold text-brand-700'}>{m.change > 0 ? '+' : ''}{m.change}</Td>
                <Td>{m.balanceAfter}</Td><Td>{m.user}</Td><Td className="text-slate-500">{m.note}</Td>
              </tr>
            ))}
          </Table>
          <Pager page={page} totalPages={data.totalPages} onPage={setPage} />
        </>
      )}
    </Modal>
  );
}
