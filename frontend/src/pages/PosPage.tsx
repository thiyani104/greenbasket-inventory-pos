import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Page, PaymentMethod, Product, Sale } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ReceiptDialog } from '../components/Receipt';
import { Button, Card, Empty, ErrorBox, Field, PageHeader, Spinner, useLoad, useToast } from '../components/ui';
import { money, round2 } from '../lib/format';

interface Line { product: Product; qty: number }

export default function PosPage() {
  const { user, settings, isManager } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [cart, setCart] = useState<Record<number, Line>>({});
  const [discount, setDiscount] = useState('0');
  const [payment, setPayment] = useState<PaymentMethod>('CASH');
  const [tendered, setTendered] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [receipt, setReceipt] = useState<Sale | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => { const t = setTimeout(() => setDebounced(q), 200); return () => clearTimeout(t); }, [q]);
  const list = useLoad(() => api<Page<Product>>('/api/products', { query: { q: debounced, size: 40 } }), [debounced]);

  const maxDiscount = isManager ? 100 : 10;
  const taxRate = parseFloat(settings['tax.rate'] ?? '0') || 0;
  const lines = Object.values(cart);

  const totals = useMemo(() => {
    const subtotal = round2(lines.reduce((s, l) => s + l.product.price * l.qty, 0));
    const d = Math.min(Math.max(parseFloat(discount) || 0, 0), 100);
    const discountAmount = round2((subtotal * d) / 100);
    const tax = round2(((subtotal - discountAmount) * taxRate) / 100);
    const total = round2(subtotal - discountAmount + tax);
    return { subtotal, discountAmount, tax, total, units: lines.reduce((s, l) => s + l.qty, 0) };
  }, [lines, discount, taxRate]);

  const paid = payment === 'CASH' ? parseFloat(tendered) || 0 : totals.total;
  const change = Math.max(round2(paid - totals.total), 0);

  function add(p: Product) {
    setError(null);
    setCart((c) => {
      const cur = c[p.id]?.qty ?? 0;
      if (cur + 1 > p.quantity) { setError(`Only ${p.quantity} of ${p.name} in stock.`); return c; }
      return { ...c, [p.id]: { product: p, qty: cur + 1 } };
    });
  }
  function setQty(id: number, qty: number) {
    setCart((c) => {
      const l = c[id];
      if (!l) return c;
      if (qty <= 0) { const { [id]: _drop, ...rest } = c; return rest; }
      if (qty > l.product.quantity) { setError(`Only ${l.product.quantity} of ${l.product.name} in stock.`); return c; }
      return { ...c, [id]: { ...l, qty } };
    });
  }

  async function checkout() {
    setBusy(true);
    setError(null);
    try {
      const sale = await api<Sale>('/api/sales', {
        method: 'POST',
        body: {
          items: lines.map((l) => ({ productId: l.product.id, quantity: l.qty })),
          paymentMethod: payment,
          discountPercent: parseFloat(discount) || 0,
          tendered: payment === 'CASH' ? paid : undefined,
        },
      });
      setReceipt(sale);
      setCart({});
      setDiscount('0');
      setTendered('');
      list.reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Checkout failed');
      toast('err', 'Sale was not completed');
      list.reload();                                  // stock may have changed under us
    } finally {
      setBusy(false);
    }
  }

  const canPay = lines.length > 0 && (payment !== 'CASH' || paid + 0.005 >= totals.total) && (parseFloat(discount) || 0) <= maxDiscount;

  return (
    <>
      <PageHeader title="Point of sale" subtitle={`Cashier: ${user?.fullName}`} />
      <div className="grid gap-4 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <Card>
            <input ref={searchRef} autoFocus placeholder="Search products…" value={q} onChange={(e) => setQ(e.target.value)} className="mb-4" />
            {list.loading && !list.data ? <Spinner /> : !list.data?.content.length ? <Empty>No products found.</Empty> : (
              <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
                {list.data.content.map((p) => {
                  const blocked = p.quantity <= 0 || p.status === 'EXPIRED';
                  return (
                    <button key={p.id} disabled={blocked} onClick={() => add(p)}
                      className="rounded-xl border border-slate-200 bg-white p-3 text-left shadow-sm transition hover:border-brand-400 hover:shadow disabled:cursor-not-allowed disabled:opacity-45">
                      <div className="line-clamp-2 text-sm font-semibold text-slate-900">{p.name}</div>
                      <div className="mt-1 text-xs text-slate-400">{p.category}</div>
                      <div className="mt-2 flex items-end justify-between">
                        <span className="font-bold text-brand-700">{money(p.price)}</span>
                        <span className={`text-xs font-semibold ${p.quantity <= 0 ? 'text-red-600' : p.status === 'LOW' ? 'text-amber-600' : 'text-slate-500'}`}>
                          {p.status === 'EXPIRED' ? 'Expired' : p.quantity <= 0 ? 'Out' : `${p.quantity} left`}
                        </span>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </Card>
        </div>

        <div className="lg:col-span-2">
          <Card title={`Cart · ${totals.units} item${totals.units === 1 ? '' : 's'}`} action={lines.length > 0 ? <button className="text-xs font-semibold text-red-600 hover:underline" onClick={() => setCart({})}>Clear</button> : undefined}>
            {lines.length === 0 ? <Empty>Tap a product to add it.</Empty> : (
              <ul className="divide-y divide-slate-100">
                {lines.map((l) => (
                  <li key={l.product.id} className="flex items-center gap-3 py-2">
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">{l.product.name}</div>
                      <div className="text-xs text-slate-400">{money(l.product.price)} each</div>
                    </div>
                    <div className="flex items-center gap-1">
                      <button aria-label="Decrease" className="h-7 w-7 rounded-full bg-slate-100 font-bold hover:bg-slate-200" onClick={() => setQty(l.product.id, l.qty - 1)}>−</button>
                      <span className="w-8 text-center text-sm font-semibold">{l.qty}</span>
                      <button aria-label="Increase" className="h-7 w-7 rounded-full bg-slate-100 font-bold hover:bg-slate-200" onClick={() => setQty(l.product.id, l.qty + 1)}>+</button>
                    </div>
                    <div className="w-24 text-right text-sm font-semibold">{money(l.product.price * l.qty)}</div>
                  </li>
                ))}
              </ul>
            )}

            <div className="mt-4 space-y-3 border-t border-slate-100 pt-4">
              <div className="grid grid-cols-2 gap-3">
                <Field label="Discount %" hint={isManager ? undefined : `Max ${maxDiscount}% for your role`}>
                  <input type="number" min="0" max={maxDiscount} step="0.5" value={discount} onChange={(e) => setDiscount(e.target.value)} />
                </Field>
                <Field label="Payment">
                  <select value={payment} onChange={(e) => setPayment(e.target.value as PaymentMethod)}>
                    <option value="CASH">Cash</option><option value="CARD">Card</option><option value="MOBILE">Mobile</option>
                  </select>
                </Field>
              </div>
              {payment === 'CASH' && (
                <Field label="Cash tendered">
                  <input type="number" min="0" step="0.01" value={tendered} placeholder={totals.total.toFixed(2)} onChange={(e) => setTendered(e.target.value)} />
                </Field>
              )}

              <dl className="space-y-1 text-sm">
                <div className="flex justify-between"><dt className="text-slate-500">Subtotal</dt><dd>{money(totals.subtotal)}</dd></div>
                {totals.discountAmount > 0 && <div className="flex justify-between"><dt className="text-slate-500">Discount</dt><dd>− {money(totals.discountAmount)}</dd></div>}
                {totals.tax > 0 && <div className="flex justify-between"><dt className="text-slate-500">Tax ({taxRate}%)</dt><dd>{money(totals.tax)}</dd></div>}
                <div className="flex justify-between border-t border-slate-200 pt-2 text-lg font-bold"><dt>Total</dt><dd className="text-brand-700">{money(totals.total)}</dd></div>
                {payment === 'CASH' && paid > 0 && <div className="flex justify-between text-slate-600"><dt>Change</dt><dd className="font-semibold">{money(change)}</dd></div>}
              </dl>
              <ErrorBox message={error} />
              <Button className="w-full py-3 text-base" busy={busy} disabled={!canPay} onClick={() => void checkout()}>Charge {money(totals.total)}</Button>
            </div>
          </Card>
        </div>
      </div>
      {receipt && <ReceiptDialog sale={receipt} onClose={() => { setReceipt(null); searchRef.current?.focus(); }} />}
    </>
  );
}
