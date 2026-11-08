import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/client';
import type { Dashboard, ReorderSuggestion } from '../api/types';
import { Card, Empty, ErrorBox, Kpi, PageHeader, Spinner, Table, Td, useLoad } from '../components/ui';
import { money, num } from '../lib/format';
import { Link } from 'react-router-dom';

const COLORS = ['#1f7c3d', '#4db46a', '#f59e0b', '#3b82f6', '#a855f7', '#ef4444'];

export default function DashboardPage() {
  const { data, loading, error } = useLoad(() => api<Dashboard>('/api/reports/dashboard'), []);
  const reorder = useLoad(() => api<ReorderSuggestion[]>('/api/reports/reorder'), []);
  if (loading) return <Spinner />;
  if (error || !data) return <ErrorBox message={error} />;

  const t = data.today, y = data.yesterday;
  const delta = y.netRevenue > 0 ? ((t.netRevenue - y.netRevenue) / y.netRevenue) * 100 : null;
  const hours = t.byHour.filter((b) => b.revenue > 0 || b.label >= '08:00').filter((b) => b.label >= '06:00' && b.label <= '22:00');

  return (
    <>
      <PageHeader title="Dashboard" subtitle="How the store is doing right now" />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi label="Sales today" value={money(t.netRevenue)} tone="good"
          sub={delta === null ? `${t.transactions} receipts` : `${delta >= 0 ? '▲' : '▼'} ${Math.abs(delta).toFixed(0)}% vs yesterday · ${t.transactions} receipts`} />
        <Kpi label="Profit today" value={money(t.profit)} sub={`Avg basket ${money(t.averageBasket)}`} />
        <Kpi label="Daily target" value={data.dailyTarget > 0 ? `${data.targetProgressPercent.toFixed(0)}%` : 'Not set'}
          sub={data.dailyTarget > 0 ? `of ${money(data.dailyTarget)}` : 'Set one in Settings'} tone={data.targetProgressPercent >= 100 ? 'good' : 'default'} />
        <Kpi label="Stock value" value={money(data.stockValueAtCost)} sub={`${money(data.stockValueAtRetail)} at retail · ${num(data.productCount)} products`} />
      </div>

      <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi label="Low stock" value={num(data.lowStock)} tone={data.lowStock ? 'warn' : 'default'} />
        <Kpi label="Out of stock" value={num(data.outOfStock)} tone={data.outOfStock ? 'bad' : 'default'} />
        <Kpi label="Expiring soon" value={num(data.expiringSoon)} tone={data.expiringSoon ? 'warn' : 'default'} />
        <Kpi label="Expired" value={num(data.expired)} tone={data.expired ? 'bad' : 'default'} />
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        <Card title="Last 7 days" className="lg:col-span-2">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data.last7Days}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="label" tickFormatter={(v: string) => v.slice(5)} fontSize={12} />
                <YAxis fontSize={12} width={60} />
                <Tooltip formatter={(v: number) => money(v)} />
                <Line type="monotone" dataKey="revenue" name="Revenue" stroke="#1f7c3d" strokeWidth={3} dot={{ r: 3 }} />
                <Line type="monotone" dataKey="profit" name="Profit" stroke="#f59e0b" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Today by category">
          {t.byCategory.length === 0 ? <Empty>No sales yet today.</Empty> : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={t.byCategory} dataKey="revenue" nameKey="label" innerRadius={50} outerRadius={85} paddingAngle={2}>
                    {t.byCategory.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                  </Pie>
                  <Tooltip formatter={(v: number) => money(v)} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card title="Sales by hour (today)">
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={hours}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="label" fontSize={11} interval={1} />
                <YAxis fontSize={12} width={50} />
                <Tooltip formatter={(v: number) => money(v)} />
                <Bar dataKey="revenue" name="Revenue" fill="#4db46a" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Needs reordering" action={<Link to="/purchasing" className="text-sm font-semibold text-brand-700 hover:underline">Create order →</Link>}>
          {reorder.loading ? <Spinner /> : !reorder.data?.length ? <Empty>Everything is above its reorder level. 🎉</Empty> : (
            <Table head={['Product', 'In stock', 'Reorder at', 'Suggested']}>
              {reorder.data.slice(0, 6).map((r) => (
                <tr key={r.productId}><Td className="font-medium">{r.name}</Td><Td className={r.quantity === 0 ? 'font-semibold text-red-600' : ''}>{r.quantity}</Td><Td>{r.reorderLevel}</Td><Td>{r.suggestedOrder}</Td></tr>
              ))}
            </Table>
          )}
        </Card>
      </div>
    </>
  );
}
