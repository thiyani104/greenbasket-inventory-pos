import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

interface Item { to: string; label: string; icon: string; manager?: boolean }
const ITEMS: Item[] = [
  { to: '/', label: 'Dashboard', icon: '▦', manager: true },
  { to: '/pos', label: 'Point of sale', icon: '🛒' },
  { to: '/products', label: 'Products', icon: '📦' },
  { to: '/sales', label: 'Sales history', icon: '🧾' },
  { to: '/purchasing', label: 'Purchasing', icon: '🚚', manager: true },
  { to: '/reports', label: 'Reports', icon: '📈', manager: true },
  { to: '/users', label: 'Staff', icon: '👥', manager: true },
  { to: '/activity', label: 'Activity log', icon: '🕘', manager: true },
  { to: '/settings', label: 'Settings', icon: '⚙', manager: true },
];

export default function Layout() {
  const { user, settings, isManager, logout } = useAuth();
  const nav = useNavigate();
  const items = ITEMS.filter((i) => !i.manager || isManager);
  return (
    <div className="flex min-h-screen">
      <aside className="no-print hidden w-60 shrink-0 flex-col bg-brand-900 text-brand-50 md:flex">
        <div className="px-5 py-6">
          <div className="text-xl font-extrabold tracking-tight">🌿 {settings['store.name'] || 'Green Basket'}</div>
          <div className="mt-1 text-xs text-brand-200">Inventory &amp; POS</div>
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {items.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.to === '/'}
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-brand-700 text-white' : 'text-brand-100 hover:bg-brand-800'}`}>
              <span aria-hidden className="w-5 text-center">{i.icon}</span>{i.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-brand-800 p-4 text-sm">
          <div className="font-semibold">{user?.fullName}</div>
          <div className="text-xs text-brand-200">{user?.role === 'MANAGER' ? 'Store manager' : 'Sales assistant'}</div>
          <button onClick={() => void logout().then(() => nav('/login'))}
            className="mt-3 w-full rounded-lg bg-brand-800 px-3 py-2 text-xs font-semibold hover:bg-brand-700">Sign out</button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="no-print flex items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-3 md:hidden">
          <span className="font-bold text-brand-800">🌿 {settings['store.name'] || 'Green Basket'}</span>
          <button onClick={() => void logout().then(() => nav('/login'))} className="text-sm font-semibold text-slate-600">Sign out</button>
        </header>
        <nav className="no-print flex gap-1 overflow-x-auto border-b border-slate-200 bg-white px-2 py-2 md:hidden">
          {items.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.to === '/'}
              className={({ isActive }) => `whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-semibold ${isActive ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>{i.label}</NavLink>
          ))}
        </nav>
        <main className="min-w-0 flex-1 p-4 md:p-8"><Outlet /></main>
      </div>
    </div>
  );
}
