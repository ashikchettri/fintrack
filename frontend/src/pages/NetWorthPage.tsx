import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Landmark, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import type { NetWorth, NetWorthItem, NetWorthKind } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

const CATEGORIES: Record<NetWorthKind, string[]> = {
  ASSET: ['Property', 'Savings & cash', 'Investments', 'Super / pension', 'Vehicle', 'Other'],
  LIABILITY: ['Mortgage', 'Personal loan', 'Credit card', 'Car loan', 'Other'],
};

interface Row {
  key: string;
  kind: NetWorthKind;
  category: string;
  name: string;
  value: string;
}

let nextKey = 0;
const newRow = (kind: NetWorthKind): Row => ({ key: `r${nextKey++}`, kind, category: '', name: '', value: '' });

export default function NetWorthPage() {
  const summary = useQuery({ queryKey: ['net-worth'], queryFn: api.getNetWorth, retry: false });
  const items = useQuery({ queryKey: ['net-worth-items'], queryFn: api.getNetWorthItems, retry: false });

  if (items.isPending) {
    return <AppShell><p className="text-muted-foreground">Loading your net worth…</p></AppShell>;
  }
  if (items.isError || !items.data) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your net worth.</Alert></AppShell>;
  }

  return (
    <AppShell>
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Net worth</h1>
          <p className="text-sm text-muted-foreground">
            Everything you own minus what you owe. Your home loan is included automatically.
          </p>
        </div>

        {summary.data && <SummaryCard data={summary.data} />}
        <Editor initial={items.data.items} currency={items.data.currency} />
      </div>
    </AppShell>
  );
}

function SummaryCard({ data }: { data: NetWorth }) {
  if (data.assets.length === 0 && data.liabilities.length === 0) return null;
  return (
    <Card className="border-primary/30 hero-gradient">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <Landmark className="size-4 text-primary" aria-hidden="true" />
          Net worth
        </CardTitle>
        <CardDescription>
          <span
            className={cn('text-2xl font-semibold tabular-nums',
              data.netWorth >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-foreground')}
            data-testid="net-worth-total"
          >
            {formatMoney(data.netWorth, data.currency)}
          </span>
        </CardDescription>
      </CardHeader>
      <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Breakdown title="Assets" total={data.totalAssets} lines={data.assets} currency={data.currency} tone="good" />
        <Breakdown title="Liabilities" total={data.totalLiabilities} lines={data.liabilities} currency={data.currency} tone="bad" />
      </CardContent>
    </Card>
  );
}

function Breakdown({ title, total, lines, currency, tone }: {
  title: string; total: number; lines: NetWorth['assets']; currency: string; tone: 'good' | 'bad';
}) {
  return (
    <div>
      <div className="flex items-baseline justify-between border-b pb-1">
        <span className="text-sm font-medium">{title}</span>
        <span className={cn('text-sm font-semibold tabular-nums',
          tone === 'good' ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400')}>
          {formatMoney(total, currency)}
        </span>
      </div>
      <ul className="mt-2 space-y-1">
        {lines.map((l, i) => (
          <li key={i} className="flex items-center justify-between gap-2 text-sm">
            <span className="truncate text-muted-foreground">
              {l.name}
              {l.source === 'HOME_LOAN' && <span className="ml-1 text-xs">(home loan)</span>}
            </span>
            <span className="tabular-nums">{formatMoney(l.value, currency)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Editor({ initial, currency }: { initial: NetWorthItem[]; currency: string }) {
  const queryClient = useQueryClient();
  const [rows, setRows] = useState<Row[]>([]);

  // seed the editable rows from the saved sheet once it arrives
  useEffect(() => {
    setRows(initial.map((it) => ({
      key: `r${nextKey++}`, kind: it.kind, category: it.category ?? '', name: it.name, value: String(it.value),
    })));
  }, [initial]);

  const mutation = useMutation({
    mutationFn: (payload: NetWorthItem[]) => api.saveNetWorthItems(payload, currency),
    onSuccess: () => {
      toast.success('Net worth saved');
      void queryClient.invalidateQueries({ queryKey: ['net-worth'] });
      void queryClient.invalidateQueries({ queryKey: ['net-worth-items'] });
    },
    onError: (error: unknown) =>
      toast.error(error instanceof ApiError ? (error.problem.detail ?? 'Could not save') : 'Network error'),
  });

  function update(key: string, patch: Partial<Row>) {
    setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  }
  function remove(key: string) {
    setRows((rs) => rs.filter((r) => r.key !== key));
  }

  function onSave() {
    const payload: NetWorthItem[] = rows
      .filter((r) => r.name.trim() !== '' && r.value.trim() !== '' && Number.isFinite(Number(r.value)))
      .map((r) => ({
        kind: r.kind,
        category: r.category.trim() || null,
        name: r.name.trim(),
        value: Number(r.value),
      }));
    mutation.mutate(payload);
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Your balance sheet</CardTitle>
        <CardDescription>Add what you own and owe, then save. The home loan is added for you.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <Section kind="ASSET" title="Assets" rows={rows} update={update} remove={remove} setRows={setRows} />
        <Section kind="LIABILITY" title="Liabilities" rows={rows} update={update} remove={remove} setRows={setRows} />
        <Button onClick={onSave} disabled={mutation.isPending} className="w-full sm:w-auto">
          {mutation.isPending ? 'Saving…' : 'Save net worth'}
        </Button>
      </CardContent>
    </Card>
  );
}

function Section({ kind, title, rows, update, remove, setRows }: {
  kind: NetWorthKind; title: string; rows: Row[];
  update: (key: string, patch: Partial<Row>) => void;
  remove: (key: string) => void;
  setRows: React.Dispatch<React.SetStateAction<Row[]>>;
}) {
  const listId = `cats-${kind}`;
  const own = rows.filter((r) => r.kind === kind);
  return (
    <div className="space-y-2">
      <p className="text-sm font-medium">{title}</p>
      <datalist id={listId}>
        {CATEGORIES[kind].map((c) => <option key={c} value={c} />)}
      </datalist>
      {own.map((r) => (
        <div key={r.key} className="flex items-center gap-2">
          <Input aria-label={`${title} category`} list={listId} placeholder="Category"
                 className="w-32 shrink-0" value={r.category}
                 onChange={(e) => update(r.key, { category: e.target.value })} />
          <Input aria-label={`${title} name`} placeholder="Name" value={r.name}
                 onChange={(e) => update(r.key, { name: e.target.value })} />
          <Input aria-label={`${title} value`} type="number" inputMode="decimal" min="0" step="any"
                 placeholder="0" className="w-28 shrink-0" value={r.value}
                 onChange={(e) => update(r.key, { value: e.target.value })} />
          <button type="button" aria-label={`Remove ${title.toLowerCase()}`}
                  onClick={() => remove(r.key)}
                  className="text-muted-foreground transition-colors hover:text-destructive">
            <Trash2 className="size-4" aria-hidden="true" />
          </button>
        </div>
      ))}
      <Button type="button" variant="ghost" size="sm" className="gap-1.5"
              onClick={() => setRows((rs) => [...rs, newRow(kind)])}>
        <Plus className="size-4" aria-hidden="true" /> Add {kind === 'ASSET' ? 'asset' : 'liability'}
      </Button>
    </div>
  );
}
