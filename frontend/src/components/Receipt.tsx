import type { Sale } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { dateTime, money } from '../lib/format';
import { Button, Modal } from './ui';

export function ReceiptDialog({ sale, onClose }: { sale: Sale; onClose: () => void }) {
  const { settings } = useAuth();
  return (
    <Modal title={`Receipt ${sale.receiptNo}`} onClose={onClose}
      footer={<><Button variant="secondary" onClick={() => window.print()}>🖨 Print</Button><Button onClick={onClose}>Done</Button></>}>
      <div className="print-area mx-auto max-w-xs font-mono text-xs leading-5 text-slate-800">
        <div className="text-center">
          <div className="text-base font-bold">{settings['store.name'] || 'Green Basket'}</div>
          {settings['store.address'] && <div>{settings['store.address']}</div>}
          <div>{dateTime(sale.createdAt)}</div>
          <div>{sale.receiptNo} · {sale.cashier}</div>
          {sale.voided && <div className="mt-1 border border-red-600 px-1 font-bold text-red-600">VOIDED — {sale.voidReason}</div>}
        </div>
        <hr className="my-2 border-dashed border-slate-400" />
        {sale.items.map((i, idx) => (
          <div key={idx} className="mb-1">
            <div>{i.name}</div>
            <div className="flex justify-between"><span>{i.quantity} × {money(i.unitPrice)}</span><span>{money(i.lineTotal)}</span></div>
          </div>
        ))}
        <hr className="my-2 border-dashed border-slate-400" />
        <Row l="Subtotal" r={money(sale.subtotal)} />
        {sale.discountAmount > 0 && <Row l={`Discount ${sale.discountPercent}%`} r={`− ${money(sale.discountAmount)}`} />}
        {sale.taxAmount > 0 && <Row l={`Tax ${sale.taxRate}%`} r={money(sale.taxAmount)} />}
        <div className="mt-1 flex justify-between text-sm font-bold"><span>TOTAL</span><span>{money(sale.total)}</span></div>
        <Row l={`Paid (${sale.paymentMethod.toLowerCase()})`} r={money(sale.tendered)} />
        {sale.paymentMethod === 'CASH' && <Row l="Change" r={money(sale.change)} />}
        <div className="mt-3 text-center">{settings['receipt.footer']}</div>
      </div>
    </Modal>
  );
}

const Row = ({ l, r }: { l: string; r: string }) => <div className="flex justify-between"><span>{l}</span><span>{r}</span></div>;
