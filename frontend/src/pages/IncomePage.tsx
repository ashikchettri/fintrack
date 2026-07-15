import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Wallet } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import type { HouseholdIncome, Income } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { formatMoney } from '@/lib/format';

interface FormValues {
  salaryAmount: string;
  salaryFrequency: '' | 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY' | 'ANNUALLY';
  superRate: string;
  bonusAnnual: string;
  otherIncomeAnnual: string;
  otherIncomeNote: string;
  notes: string;
}

const str = (n: number | null | undefined) => (n == null ? '' : String(n));

function toForm(income: Income): FormValues {
  return {
    salaryAmount: str(income.salaryAmount),
    salaryFrequency: income.salaryFrequency ?? '',
    superRate: str(income.superRate),
    bonusAnnual: str(income.bonusAnnual),
    otherIncomeAnnual: str(income.otherIncomeAnnual),
    otherIncomeNote: income.otherIncomeNote ?? '',
    notes: income.notes ?? '',
  };
}

function toPayload(v: FormValues): Partial<Income> {
  const num = (x: string) => (x.trim() === '' ? null : Number(x));
  return {
    salaryAmount: num(v.salaryAmount),
    salaryFrequency: v.salaryFrequency || null,
    superRate: num(v.superRate),
    bonusAnnual: num(v.bonusAnnual),
    otherIncomeAnnual: num(v.otherIncomeAnnual),
    otherIncomeNote: v.otherIncomeNote.trim() || null,
    currency: 'AUD',
    notes: v.notes.trim() || null,
  };
}

export default function IncomePage() {
  const { data, isPending, isError } = useQuery({ queryKey: ['income'], queryFn: api.getIncome, retry: false });

  if (isPending) {
    return <AppShell><p className="text-muted-foreground">Loading your income…</p></AppShell>;
  }
  if (isError || !data) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your income.</Alert></AppShell>;
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-lg space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Income</h1>
          <p className="text-sm text-muted-foreground">
            Your income feeds the household cash flow — everyone enters their own.
          </p>
        </div>
        <IncomeForm income={data} />
        <HouseholdIncomeCard />
      </div>
    </AppShell>
  );
}

function IncomeForm({ income }: { income: Income }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, reset, formState: { isSubmitting } } =
    useForm<FormValues>({ defaultValues: toForm(income) });

  const mutation = useMutation({
    mutationFn: (payload: Partial<Income>) => api.saveIncome(payload),
    onSuccess: (saved) => {
      toast.success('Income saved');
      reset(toForm(saved));
      void queryClient.invalidateQueries({ queryKey: ['income'] });
      void queryClient.invalidateQueries({ queryKey: ['income-summary'] });
    },
    onError: (error: unknown) =>
      toast.error(error instanceof ApiError ? (error.problem.detail ?? 'Could not save') : 'Network error'),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Wallet className="size-4" aria-hidden="true" />
          Your income
        </CardTitle>
        <CardDescription>Salary, super, bonus and anything else.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit((v) => mutation.mutate(toPayload(v)))} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="salaryAmount">Salary (per pay)</Label>
            <Input id="salaryAmount" type="number" inputMode="decimal" step="any" {...register('salaryAmount')} />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="salaryFrequency">Pay frequency</Label>
            <select id="salaryFrequency" {...register('salaryFrequency')}
                    className="rounded-md border bg-background px-2 py-2 text-sm">
              <option value="">—</option>
              <option value="WEEKLY">Weekly</option>
              <option value="FORTNIGHTLY">Fortnightly</option>
              <option value="MONTHLY">Monthly</option>
              <option value="ANNUALLY">Annually</option>
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="superRate">Super rate (% p.a.)</Label>
            <Input id="superRate" type="number" inputMode="decimal" step="any" placeholder="e.g. 11.5"
                   {...register('superRate')} />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="bonusAnnual">Bonus (per year)</Label>
            <Input id="bonusAnnual" type="number" inputMode="decimal" step="any" {...register('bonusAnnual')} />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="otherIncomeAnnual">Other income (per year)</Label>
            <Input id="otherIncomeAnnual" type="number" inputMode="decimal" step="any"
                   {...register('otherIncomeAnnual')} />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="otherIncomeNote">What's the other income?</Label>
            <Input id="otherIncomeNote" placeholder="e.g. rental, dividends" {...register('otherIncomeNote')} />
          </div>

          <Button type="submit" disabled={isSubmitting || mutation.isPending} className="mt-1 w-full sm:w-auto">
            {mutation.isPending ? 'Saving…' : 'Save'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function HouseholdIncomeCard() {
  const { data } = useQuery({ queryKey: ['income-summary'], queryFn: api.householdIncome, retry: false });
  const { data: members } = useQuery({ queryKey: ['household-members'], queryFn: api.householdMembers, retry: false });

  if (!data || data.members.length === 0) return null;

  const nameById = new Map((members ?? []).map((m) => [m.memberId, m.name]));

  return (
    <Card className="border-primary/30 bg-primary/[0.03]">
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Household income</CardTitle>
        <CardDescription>Everyone&apos;s income, combined.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {data.members.map((m: HouseholdIncome['members'][number]) => (
          <div key={m.memberId} className="flex items-center justify-between text-sm">
            <span>{m.isYou ? 'You' : nameById.get(m.memberId) ?? 'Member'}</span>
            <span className="tabular-nums font-medium">{formatMoney(m.annualIncome, data.currency)} / yr</span>
          </div>
        ))}
        <div className="flex items-center justify-between border-t pt-2 text-sm font-semibold">
          <span>Total</span>
          <span className="tabular-nums" data-testid="household-income-total">
            {formatMoney(data.annualTotal, data.currency)} / yr
          </span>
        </div>
      </CardContent>
    </Card>
  );
}
