import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Landmark } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import type { HomeLoan } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { LoanCalculator } from '@/components/LoanCalculator';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

interface FormValues {
  hasHomeLoan: boolean;
  lender: string;
  loanAmount: string;
  interestRate: string;
  repaymentFrequency: '' | 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY';
  repaymentAmount: string;
  hasOffset: boolean;
  offsetBalance: string;
  ownership: '' | 'JOINT' | 'SOLE';
  notes: string;
}

const str = (n: number | null | undefined) => (n == null ? '' : String(n));

function toForm(loan: HomeLoan): FormValues {
  return {
    hasHomeLoan: loan.hasHomeLoan,
    lender: loan.lender ?? '',
    loanAmount: str(loan.loanAmount),
    interestRate: str(loan.interestRate),
    repaymentFrequency: loan.repaymentFrequency ?? '',
    repaymentAmount: str(loan.repaymentAmount),
    hasOffset: loan.hasOffset,
    offsetBalance: str(loan.offsetBalance),
    ownership: loan.ownership ?? '',
    notes: loan.notes ?? '',
  };
}

function toPayload(v: FormValues): Partial<HomeLoan> {
  const num = (x: string) => (x.trim() === '' ? null : Number(x));
  return {
    hasHomeLoan: v.hasHomeLoan,
    lender: v.lender.trim() || null,
    loanAmount: num(v.loanAmount),
    interestRate: num(v.interestRate),
    repaymentFrequency: v.repaymentFrequency || null,
    repaymentAmount: num(v.repaymentAmount),
    hasOffset: v.hasOffset,
    offsetBalance: num(v.offsetBalance),
    ownership: v.ownership || null,
    currency: 'AUD',
    notes: v.notes.trim() || null,
  };
}

export default function HomeLoanPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['home-loan'],
    queryFn: api.getHomeLoan,
    retry: false,
  });

  if (isPending) {
    return <AppShell><p className="text-muted-foreground">Loading your home-loan details…</p></AppShell>;
  }
  if (isError || !data) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your home-loan details.</Alert></AppShell>;
  }
  return <AppShell><HomeLoanForm loan={data} /></AppShell>;
}

function HomeLoanForm({ loan }: { loan: HomeLoan }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, watch, reset, formState: { isSubmitting } } =
    useForm<FormValues>({ defaultValues: toForm(loan) });
  const hasLoan = watch('hasHomeLoan');
  const hasOffset = watch('hasOffset');

  const mutation = useMutation({
    mutationFn: (payload: Partial<HomeLoan>) => api.saveHomeLoan(payload),
    onSuccess: (saved) => {
      toast.success('Home-loan details saved');
      reset(toForm(saved));
      void queryClient.invalidateQueries({ queryKey: ['home-loan'] });
    },
    onError: (error: unknown) =>
      toast.error(error instanceof ApiError ? (error.problem.detail ?? 'Could not save') : 'Network error'),
  });

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Home loan</h1>
        <p className="text-sm text-muted-foreground">
          Used to work out your household&apos;s cash flow — and later, what you can afford.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Landmark className="size-4" aria-hidden="true" />
            Your home loan
          </CardTitle>
          <CardDescription>Jointly held — visible to everyone in your household.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit((v) => mutation.mutate(toPayload(v)))} className="flex flex-col gap-4">
            <label className="flex items-center gap-2 text-sm font-medium">
              <input type="checkbox" className="size-4 accent-primary" {...register('hasHomeLoan')} />
              Do you have a home loan?
            </label>

            {hasLoan && (
              <div className="flex flex-col gap-4 border-l-2 border-primary/20 pl-4">
                <Money id="loanAmount" label="Total loan amount" register={register('loanAmount')} />
                <Field id="interestRate" label="Interest rate (% p.a.)" hint="e.g. 6.25"
                       register={register('interestRate')} />

                <Select id="repaymentFrequency" label="Repayment frequency" register={register('repaymentFrequency')}
                        options={[['WEEKLY', 'Weekly'], ['FORTNIGHTLY', 'Fortnightly'], ['MONTHLY', 'Monthly']]} />
                <Money id="repaymentAmount" label="Repayment amount" register={register('repaymentAmount')} />

                <Select id="ownership" label="Whose name is the loan in?" register={register('ownership')}
                        options={[['JOINT', 'Both names (joint)'], ['SOLE', 'One name (sole)']]} />

                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" className="size-4 accent-primary" {...register('hasOffset')} />
                  Do you have an offset account?
                </label>
                {hasOffset && (
                  <Money id="offsetBalance" label="Savings in offset account" register={register('offsetBalance')} />
                )}

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="notes">Anything else?</Label>
                  <textarea id="notes" rows={2} {...register('notes')}
                            className="rounded-md border bg-background px-3 py-2 text-sm" />
                </div>
              </div>
            )}

            <Button type="submit" disabled={isSubmitting || mutation.isPending} className="mt-1 w-full sm:w-auto">
              {mutation.isPending ? 'Saving…' : 'Save'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {hasLoan && (
        <LoanCalculator
          loanAmount={loan.loanAmount}
          interestRate={loan.interestRate}
          currency={loan.currency}
        />
      )}
    </div>
  );
}

type Reg = ReturnType<ReturnType<typeof useForm<FormValues>>['register']>;

function Field({ id, label, hint, register }: { id: string; label: string; hint?: string; register: Reg }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type="number" inputMode="decimal" step="any" placeholder={hint} {...register} />
    </div>
  );
}

function Money({ id, label, register }: { id: string; label: string; register: Reg }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex items-center gap-2">
        <span className="text-sm text-muted-foreground">$</span>
        <Input id={id} type="number" inputMode="decimal" step="any" {...register} />
      </div>
    </div>
  );
}

function Select({ id, label, register, options }: {
  id: string; label: string; register: Reg; options: [string, string][];
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <select id={id} {...register} className="rounded-md border bg-background px-2 py-2 text-sm">
        <option value="">—</option>
        {options.map(([value, text]) => <option key={value} value={value}>{text}</option>)}
      </select>
    </div>
  );
}
