import type { ReactNode } from 'react';
import { useForm, useFieldArray } from 'react-hook-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Table2, X } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import type { Budget, BudgetSection } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { formatMoney } from '@/lib/format';
import { annualAmount, monthlyAmount } from '@/lib/finance';
import { cn } from '@/lib/utils';

interface FormLine {
  section: BudgetSection;
  category: string;
  name: string;
  frequency: '' | 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';
  amount: string;
}
interface FormValues {
  lines: FormLine[];
}

const FREQ_OPTIONS: [FormLine['frequency'], string][] = [
  ['WEEKLY', 'Weekly'], ['FORTNIGHTLY', 'Fortnightly'], ['MONTHLY', 'Monthly'],
  ['QUARTERLY', 'Quarterly'], ['ANNUALLY', 'Annually'],
];

function toForm(budget: Budget): FormValues {
  return {
    lines: budget.lines.map((l) => ({
      section: l.section,
      category: l.category ?? '',
      name: l.name,
      frequency: l.frequency ?? '',
      amount: l.amount == null ? '' : String(l.amount),
    })),
  };
}

function toBudget(v: FormValues): Budget {
  return {
    currency: 'AUD',
    lines: v.lines.map((l) => ({
      section: l.section,
      category: l.category.trim() || null,
      name: l.name.trim(),
      frequency: l.frequency || null,
      amount: l.amount.trim() === '' ? null : Number(l.amount),
    })),
  };
}

export default function BudgetPage() {
  const { data, isPending, isError } = useQuery({ queryKey: ['budget'], queryFn: api.getBudget, retry: false });

  if (isPending) {
    return <AppShell><p className="text-muted-foreground">Loading your budget…</p></AppShell>;
  }
  if (isError || !data) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your budget.</Alert></AppShell>;
  }
  return <AppShell><BudgetForm budget={data} /></AppShell>;
}

function BudgetForm({ budget }: { budget: Budget }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, control, watch, reset, formState: { isSubmitting } } =
    useForm<FormValues>({ defaultValues: toForm(budget) });
  const { fields, append, remove } = useFieldArray({ control, name: 'lines' });
  const lines = watch('lines');

  const mutation = useMutation({
    mutationFn: (b: Budget) => api.saveBudget(b),
    onSuccess: (saved) => {
      toast.success('Budget saved');
      reset(toForm(saved));
      void queryClient.invalidateQueries({ queryKey: ['budget'] });
    },
    onError: (e: unknown) =>
      toast.error(e instanceof ApiError ? (e.problem.detail ?? 'Could not save') : 'Network error'),
  });

  // live monthly total for a set of field indices
  const monthlyOf = (indices: number[]) =>
    indices.reduce((sum, i) => sum + monthlyAmount(num(lines[i]?.amount), lines[i]?.frequency || null), 0);

  const incomeIdx = fields.map((_, i) => i).filter((i) => fields[i].section === 'INCOME');
  const savingIdx = fields.map((_, i) => i).filter((i) => fields[i].section === 'SAVING');
  const expenseCats: string[] = [];
  fields.forEach((f) => {
    if (f.section === 'EXPENSE' && !expenseCats.includes(f.category)) expenseCats.push(f.category);
  });
  const expenseIdx = fields.map((_, i) => i).filter((i) => fields[i].section === 'EXPENSE');

  const income = monthlyOf(incomeIdx);
  const expenses = monthlyOf(expenseIdx);
  const savings = monthlyOf(savingIdx);
  const leftover = income - expenses - savings;
  const savingsRate = income > 0 ? (savings / income) * 100 : 0;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Income &amp; expenses</h1>
          <p className="text-sm text-muted-foreground">Your household budget — monthly &amp; annual fill in automatically.</p>
        </div>
        <Button onClick={handleSubmit((v) => mutation.mutate(toBudget(v)))} disabled={isSubmitting || mutation.isPending}>
          {mutation.isPending ? 'Saving…' : 'Save budget'}
        </Button>
      </div>

      {/* summary */}
      <Card className="mb-6 border-primary/30 hero-gradient">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base"><Table2 className="size-4 text-primary" aria-hidden="true" />Summary</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1.5">
          <SummaryRow label="Total income" monthly={income} />
          <SummaryRow label="Total living expenses" monthly={expenses} />
          <SummaryRow label="Savings & investments" monthly={savings} />
          <div className="border-t pt-1.5">
            <SummaryRow label="Leftover (unallocated)" monthly={leftover} strong
                        tone={leftover < 0 ? 'negative' : 'positive'} testid="budget-leftover" />
          </div>
          <div className="flex justify-between text-sm text-muted-foreground">
            <span>Savings rate</span><span className="tabular-nums">{savingsRate.toFixed(1)}%</span>
          </div>
        </CardContent>
      </Card>

      <form onSubmit={handleSubmit((v) => mutation.mutate(toBudget(v)))} className="space-y-6">
        {/* hidden fields keep section/category on submit */}
        {fields.map((f, i) => (
          <span key={`meta-${f.id}`} className="hidden">
            <input type="hidden" {...register(`lines.${i}.section` as const)} />
            <input type="hidden" {...register(`lines.${i}.category` as const)} />
          </span>
        ))}

        <Section title="Income">
          {incomeIdx.map((i) => <Row key={fields[i].id} i={i} register={register} lines={lines} remove={remove} />)}
          <AddButton onClick={() => append(blank('INCOME', ''))} />
        </Section>

        <Section title="Expenses" subtotal={expenses}>
          {expenseCats.map((cat) => {
            const idx = expenseIdx.filter((i) => fields[i].category === cat);
            return (
              <div key={cat} className="space-y-1">
                <div className="flex items-center justify-between pt-2 text-sm font-medium text-primary">
                  <span>{cat}</span>
                  <span className="tabular-nums text-muted-foreground">{formatMoney(monthlyOf(idx), 'AUD')} / mo</span>
                </div>
                {idx.map((i) => <Row key={fields[i].id} i={i} register={register} lines={lines} remove={remove} />)}
                <AddButton onClick={() => append(blank('EXPENSE', cat))} />
              </div>
            );
          })}
        </Section>

        <Section title="Savings & investments">
          {savingIdx.map((i) => <Row key={fields[i].id} i={i} register={register} lines={lines} remove={remove} />)}
          <AddButton onClick={() => append(blank('SAVING', ''))} />
        </Section>
      </form>
    </div>
  );
}

function Row({ i, register, lines, remove }: {
  i: number;
  register: ReturnType<typeof useForm<FormValues>>['register'];
  lines: FormLine[];
  remove: (index: number) => void;
}) {
  const line = lines[i];
  const monthly = monthlyAmount(num(line?.amount), line?.frequency || null);
  const annual = annualAmount(num(line?.amount), line?.frequency || null);
  return (
    <div className="flex items-center gap-2 text-sm">
      <Input aria-label="Line name" className="h-8 flex-1" {...register(`lines.${i}.name` as const)} />
      <select aria-label="Frequency" className="h-8 rounded-md border bg-background px-1 text-xs"
              {...register(`lines.${i}.frequency` as const)}>
        <option value="">—</option>
        {FREQ_OPTIONS.map(([v, t]) => <option key={v} value={v}>{t}</option>)}
      </select>
      <Input aria-label="Amount" type="number" inputMode="decimal" step="any"
             className="h-8 w-24" {...register(`lines.${i}.amount` as const)} />
      <span className="w-20 shrink-0 text-right tabular-nums text-muted-foreground">{formatMoney(monthly, 'AUD')}</span>
      <span className="hidden w-20 shrink-0 text-right tabular-nums text-muted-foreground sm:inline">{formatMoney(annual, 'AUD')}</span>
      <button type="button" aria-label="Remove line" onClick={() => remove(i)}
              className="text-muted-foreground hover:text-destructive"><X className="size-4" /></button>
    </div>
  );
}

function Section({ title, subtotal, children }: { title: string; subtotal?: number; children: ReactNode }) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between pb-2">
        <CardTitle className="text-base">{title}</CardTitle>
        {subtotal != null && <span className="text-sm text-muted-foreground tabular-nums">{formatMoney(subtotal, 'AUD')} / mo</span>}
      </CardHeader>
      <CardContent className="space-y-1">
        <div className="flex items-center gap-2 pb-1 text-xs text-muted-foreground">
          <span className="flex-1">Item</span>
          <span className="w-[76px]">Frequency</span>
          <span className="w-24">Amount</span>
          <span className="w-20 text-right">Monthly</span>
          <span className="hidden w-20 text-right sm:inline">Annual</span>
          <span className="w-4" />
        </div>
        {children}
      </CardContent>
    </Card>
  );
}

function SummaryRow({ label, monthly, strong, tone, testid }: {
  label: string; monthly: number; strong?: boolean; tone?: 'positive' | 'negative'; testid?: string;
}) {
  return (
    <div className="flex justify-between text-sm">
      <span className={strong ? 'font-semibold' : ''}>{label}</span>
      <span className={cn('tabular-nums', strong && 'font-semibold',
        tone === 'negative' && 'text-red-600 dark:text-red-400',
        tone === 'positive' && 'text-emerald-600 dark:text-emerald-400')} data-testid={testid}>
        {formatMoney(monthly, 'AUD')} / mo
      </span>
    </div>
  );
}

function AddButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick}
            className="flex items-center gap-1 pt-1 text-xs text-muted-foreground hover:text-foreground">
      <Plus className="size-3" /> Add line
    </button>
  );
}

function blank(section: BudgetSection, category: string): FormLine {
  return { section, category, name: '', frequency: 'MONTHLY', amount: '' };
}

function num(x: string | undefined): number | null {
  if (x == null || x.trim() === '') return null;
  return Number(x);
}
