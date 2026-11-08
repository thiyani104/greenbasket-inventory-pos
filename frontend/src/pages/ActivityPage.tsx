import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Activity, Page } from '../api/types';
import { Card, Empty, ErrorBox, PageHeader, Pager, Spinner, Table, Td, useLoad } from '../components/ui';
import { dateTime } from '../lib/format';

export default function ActivityPage() {
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => { const t = setTimeout(() => { setDebounced(q); setPage(0); }, 250); return () => clearTimeout(t); }, [q]);
  const list = useLoad(() => api<Page<Activity>>('/api/activity', { query: { q: debounced, page, size: 25 } }), [debounced, page]);
  return (
    <>
      <PageHeader title="Activity log" subtitle="Who did what, newest first" />
      <Card>
        <input className="mb-4" placeholder="Filter by user, action or details…" value={q} onChange={(e) => setQ(e.target.value)} />
        {list.loading ? <Spinner /> : list.error ? <ErrorBox message={list.error} /> : !list.data?.content.length ? <Empty>Nothing logged.</Empty> : (
          <>
            <Table head={['When', 'User', 'Action', 'Details']}>
              {list.data.content.map((a) => <tr key={a.id}><Td className="whitespace-nowrap">{dateTime(a.createdAt)}</Td><Td className="font-medium">{a.username}</Td><Td>{a.action}</Td><Td className="text-slate-500">{a.details}</Td></tr>)}
            </Table>
            <Pager page={page} totalPages={list.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  );
}
