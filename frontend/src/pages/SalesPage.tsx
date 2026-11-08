import { useState } from 'react';
import { api } from '../api/client';
import type { Page, Sale } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ReceiptDialog } from '../components/Receipt';
import { Button, Card, Empty, ErrorBox, Field, Modal, PageHeader, Pager, Pill, Spinner, Table, Td, useLoad, useToast } from '../components/ui';
import { dateTime, isoToday, money } from '../lib/format';

export default function SalesPage() {
  const { isManager } = useAuth();
  const toast = useToast();
  const [from, setFrom] = useState(isoToday());
  const [to, setTo] = useState(isoToday());
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<Sale | null>(null);
  const [voiding, setVoiding] = useState<Sale | null>(null);
  const list = useLoad(() => api<Page<Sale>>('/api/sales', { query: { from, to, page, size: 20 } }), [from, to, page]);

  return (
    <>
      <PageHeader title="Sales history" subtitle="Receipts and voids" />
      <Card>
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <Field label="From"><input type="date" value={from} max={to} onChange={(e) => { setFrom(e.target.value); setPage(0); }} /></Field>
          <Field label="To"><input type="date" value={to} min={from} onChange={(e) => { setTo(e.target.value); setPage(0); }} /></Field>
          <Button variant="secondary" onClick={() => { setFrom(isoToday()); setTo(isoToday()); setPage(0); }}>Today</Button>
          <Button variant="secondary" onClick={() => { setFrom(isoToday(-6)); setTo(isoToday()); setPage(0); }}>Last 7 days</Button>
        </div>
        {list.loading ? <Spinner /> : list.error ? <ErrorBox message={list.error} /> : !list.data?.content.length ? <Empty>No sales in this period.</Empty> : (
          <>
            <Table head={['Receipt', 'When', 'Cashier', 'Items', 'Payment', 'Total', '']}>
              {list.data.content.map((s) => (
                <tr key={s.id} className={s.voided ? 'text-slate-400 line-through decoration-slate-300' : ''}>
                  <Td className="font-mono font-semibold">{s.receiptNo}{s.voided && <span className="ml-2 no-underline"><Pill tone="red">VOID</Pill></span>}</Td>
                  <Td className="whitespace-nowrap">{dateTime(s.createdAt)}</Td><Td>{s.cashier}</Td><Td>{s.units}</Td><Td>{s.paymentMethod}</Td>
                  <Td className="font-semibold">{money(s.total)}</Td>
                  <Td className="whitespace-nowrap text-right">
                    <Button variant="ghost" onClick={() => setOpen(s)}>Receipt</Button>
                    {isManager && !s.voided && <Button variant="ghost" className="text-red-600" onClick={() => setVoiding(s)}>Void</Button>}
                  </Td>
                </tr>
              ))}
            </Table>
            <Pager page={page} totalPages={list.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      {open && <ReceiptDialog sale={open} onClose={() => setOpen(null)} />}
      {voiding && <VoidDialog sale={voiding} onClose={() => setVoiding(null)} onDone={() => { setVoiding(null); toast('ok', 'Sale voided, stock restored'); list.reload(); }} />}
    </>
  );
}

function VoidDialog({ sale, onClose, onDone }: { sale: Sale; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function go() {
    setBusy(true);
    setError(null);
    try { await api(`/api/sales/${sale.id}/void`, { method: 'POST', body: { reason } }); onDone(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not void'); }
    finally { setBusy(false); }
  }
  return (
    <Modal title={`Void ${sale.receiptNo}`} onClose={onClose}
      footer={<><Button variant="secondary" onClick={onClose}>Keep sale</Button><Button variant="danger" busy={busy} disabled={!reason.trim()} onClick={() => void go()}>Void sale</Button></>}>
      <p className="mb-4 text-sm text-slate-600">This returns {sale.units} item(s) to stock and removes {money(sale.total)} from the sales totals. It cannot be undone.</p>
      <Field label="Reason"><input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} autoFocus /></Field>
      <div className="mt-3"><ErrorBox message={error} /></div>
    </Modal>
  );
}
