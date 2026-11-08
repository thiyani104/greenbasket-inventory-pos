let currency = 'Rs.';
export function setCurrency(c: string) { currency = c || 'Rs.'; }

export function money(n: number | null | undefined): string {
  const v = Number(n ?? 0);
  return `${currency} ${v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function num(n: number | null | undefined): string {
  return Number(n ?? 0).toLocaleString();
}

export function dateTime(s: string | null | undefined): string {
  return s ? new Date(s).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '';
}

export function date(s: string | null | undefined): string {
  return s ? new Date(s + (s.length === 10 ? 'T00:00:00' : '')).toLocaleDateString(undefined, { dateStyle: 'medium' }) : '';
}

export function isoToday(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/** Mirrors the server's rounding (HALF_UP to 2 dp) so the POS preview matches the receipt. */
export function round2(n: number): number {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}
