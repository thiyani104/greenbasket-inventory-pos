import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/client';
import type { Bucket, DeadStock, Summary } from '../api/types';
import { Button, Card, Empty, ErrorBox, Field, Kpi, PageHeader, Spinner, Table, Td, useLoad } from '../components/ui';
import { date, isoToday, money, num } from '../lib/format';

const COLORS = ['#1f7c3d', '#4db46a', '#f59e0b', '#3b82f6', '#a855f7', '#ef4444', '#14b8a6'];

function BucketTable({ rows, label }: { rows: Bucket[]; label: string }) {
  if (!rows.length) return <Empty>No data in this period.</Empty>;
  return (
    <Table head={[label, 'Units', 'Revenue', 'Profit']}>
      {rows.map((r) => <tr key={r.label}><Td className="font-medium">{r.label}</Td><Td>{num(r.units)}</Td><Td>{money(r.revenue)}</Td><Td className={r.profit < 0 ? 'text-red-600' : ''}>{money(r.profit)}</Td></tr>)}
    </Table>
  );
}

/** Downloads rows as a CSV file the user can open in Excel. */
function downloadCsv(name: string, header: string[], rows: Array<Array<string | number>>) {
  const esc = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;
  const csv = [header, ...rows].map((r) => r.map(esc).join(',')).join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  a.download = name;
  a.click();
  URL.revokeObjectURL(a.href);
}

export default function ReportsPage() {
  const [from, setFrom] = useState(isoToday(-29));
  const [to, setTo] = useState(isoToday());
  const [deadDays, setDeadDays] = useState(30);
  const sum = useLoad(() => api<Summary>('/api/reports/summary', { query: { from, to } }), [from, to]);
  const dead = useLoad(() => api<DeadStock[]>('/api/reports/dead-stock', { query: { days: deadDays } }), [deadDays]);

  const s = sum.data;
  const range = (a: number, b: number) => { setFrom(isoToday(a)); setTo(isoToday(b)); };

  return (
    <>
      <PageHeader title="Reports" subtitle="Sales, profit and stock analysis"
        actions={s && <Button variant="secondary" onClick={() => downloadCsv(`sales-${s.from}-to-${s.to}.csv`, ['Day', 'Revenue', 'Profit', 'Units'], s.byDay.map((d) => [d.label, d.revenue, d.profit, d.units]))}>⬇ Export daily CSV</Button>} />
      <Card className="mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <Field label="From"><input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></Field>
          <Field label="To"><input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></Field>
          <Button variant="secondary" onClick={() => range(0, 0)}>Today</Button>
          <Button variant="secondary" onClick={() => range(-6, 0)}>7 days</Button>
          <Button variant="secondary" onClick={() => range(-29, 0)}>30 days</Button>
          <Button variant="secondary" onClick={() => range(-89, 0)}>90 days</Button>
        </div>
      </Card>

      {sum.loading ? <Spinner /> : sum.error || !s ? <ErrorBox message={sum.error} /> : (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Kpi label="Net revenue" value={money(s.netRevenue)} tone="good" sub={`${s.transactions} receipts · ${num(s.units)} units`} />
            <Kpi label="Profit" value={money(s.profit)} sub={s.netRevenue > 0 ? `${((s.profit / (s.netRevenue - s.tax)) * 100 || 0).toFixed(1)}% margin` : undefined} />
            <Kpi label="Discounts given" value={money(s.discounts)} sub={`Gross ${money(s.grossSales)}`} />
            <Kpi label="Average basket" value={money(s.averageBasket)} sub={s.voidedTransactions ? `${s.voidedTransactions} voided` : 'No voids'} />
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-3">
            <Card title="Revenue &amp; profit per day" className="lg:col-span-2">
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={s.byDay}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="label" tickFormatter={(v: string) => v.slice(5)} fontSize={11} minTickGap={16} />
                    <YAxis fontSize={12} width={60} />
                    <Tooltip formatter={(v: number) => money(v)} />
                    <Legend />
                    <Bar dataKey="revenue" name="Revenue" fill="#4db46a" radius={[3, 3, 0, 0]} />
                    <Bar dataKey="profit" name="Profit" fill="#f59e0b" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Card>
            <Card title="By payment method">
              {!s.byPayment.length ? <Empty>No sales.</Empty> : (
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={s.byPayment} dataKey="revenue" nameKey="label" outerRadius={85} label={(e: { name?: string }) => e.name ?? ''}>
                        {s.byPayment.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                      </Pie>
                      <Tooltip formatter={(v: number) => money(v)} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <Card title="Busiest hours"><div className="h-56"><ResponsiveContainer width="100%" height="100%"><BarChart data={s.byHour}><CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" /><XAxis dataKey="label" fontSize={10} interval={2} /><YAxis fontSize={12} width={55} /><Tooltip formatter={(v: number) => money(v)} /><Bar dataKey="revenue" fill="#1f7c3d" radius={[3, 3, 0, 0]} /></BarChart></ResponsiveContainer></div></Card>
            <Card title="Weekday pattern"><div className="h-56"><ResponsiveContainer width="100%" height="100%"><BarChart data={s.byWeekday}><CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" /><XAxis dataKey="label" fontSize={12} /><YAxis fontSize={12} width={55} /><Tooltip formatter={(v: number) => money(v)} /><Bar dataKey="revenue" fill="#3b82f6" radius={[3, 3, 0, 0]} /></BarChart></ResponsiveContainer></div></Card>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-3">
            <Card title="Top products"><BucketTable rows={s.topProducts} label="Product" /></Card>
            <Card title="By category"><BucketTable rows={s.byCategory} label="Category" /></Card>
            <Card title="By cashier"><BucketTable rows={s.byCashier} label="Cashier" /></Card>
          </div>
        </>
      )}

      <Card title="Dead stock" className="mt-4" action={
        <label className="flex items-center gap-2 text-sm text-slate-500">No sale in
          <select value={deadDays} onChange={(e) => setDeadDays(Number(e.target.value))} className="!w-auto"><option value={14}>14</option><option value={30}>30</option><option value={60}>60</option><option value={90}>90</option></select> days
        </label>}>
        {dead.loading ? <Spinner /> : !dead.data?.length ? <Empty>Everything in stock has sold recently. 🎉</Empty> : (
          <Table head={['Product', 'Category', 'In stock', 'Value at cost', 'Last sold']}>
            {dead.data.map((d) => <tr key={d.productId}><Td className="font-medium">{d.name}</Td><Td>{d.category}</Td><Td>{d.quantity}</Td><Td>{money(d.valueAtCost)}</Td><Td>{d.lastSold ? date(d.lastSold) : 'never'}</Td></tr>)}
          </Table>
        )}
      </Card>
    </>
  );
}
