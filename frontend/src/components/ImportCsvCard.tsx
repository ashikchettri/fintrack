import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { UploadCloud, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import type { ImportSummary } from '../api/types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';

const CURRENCIES = ['AUD', 'USD', 'GBP', 'EUR', 'NZD', 'CAD'];

/**
 * Drag-drop (or click) a bank CSV to import it. On success the dashboard +
 * transactions queries are invalidated so the page refreshes itself, and a
 * toast summarizes what landed (imported / duplicates / accounts created).
 */
export function ImportCsvCard({ compact = false }: { compact?: boolean }) {
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [currency, setCurrency] = useState('AUD');
  const [dragging, setDragging] = useState(false);

  const mutation = useMutation({
    mutationFn: (file: File) => api.importTransactions(file, currency),
    onSuccess: (summary: ImportSummary) => {
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      void queryClient.invalidateQueries({ queryKey: ['transactions'] });
      toast.success(summaryMessage(summary), { description: summaryDetail(summary) });
    },
    onError: (error: unknown) => {
      const message =
        error instanceof ApiError
          ? (error.problem.detail ?? error.problem.title ?? 'Import failed')
          : 'Import failed';
      toast.error('Could not import that file', { description: message });
    },
  });

  function onFiles(files: FileList | null) {
    const file = files?.[0];
    if (file) mutation.mutate(file);
  }

  return (
    <Card>
      <CardHeader className={compact ? 'pb-2' : undefined}>
        <CardTitle className={compact ? 'text-base' : undefined}>Import transactions</CardTitle>
        {!compact && (
          <CardDescription>
            Upload a bank statement CSV — accounts are created automatically and duplicates are skipped.
          </CardDescription>
        )}
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <label className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Currency</span>
          <select
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            disabled={mutation.isPending}
            className="rounded-md border bg-background px-2 py-1 text-sm"
            aria-label="Statement currency"
          >
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </label>

        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            onFiles(e.dataTransfer.files);
          }}
          disabled={mutation.isPending}
          data-testid="csv-dropzone"
          className={cn(
            'flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed px-6 py-8 text-center transition-colors',
            'hover:border-primary/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            dragging ? 'border-primary bg-primary/5' : 'border-input',
            mutation.isPending && 'pointer-events-none opacity-70',
          )}
        >
          {mutation.isPending ? (
            <>
              <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden="true" />
              <span className="text-sm text-muted-foreground">Importing…</span>
            </>
          ) : (
            <>
              <UploadCloud className="size-6 text-muted-foreground" aria-hidden="true" />
              <span className="text-sm font-medium">Drop a CSV here, or click to choose</span>
              <span className="text-xs text-muted-foreground">.csv bank exports</span>
            </>
          )}
        </button>

        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          className="sr-only"
          aria-label="Choose a CSV file"
          onChange={(e) => onFiles(e.target.files)}
        />
      </CardContent>
    </Card>
  );
}

function summaryMessage(s: ImportSummary): string {
  return s.imported > 0
    ? `Imported ${s.imported} transaction${s.imported === 1 ? '' : 's'}`
    : 'No new transactions';
}

function summaryDetail(s: ImportSummary): string {
  const parts: string[] = [];
  if (s.duplicatesSkipped > 0) parts.push(`${s.duplicatesSkipped} duplicate${s.duplicatesSkipped === 1 ? '' : 's'} skipped`);
  if (s.accountsCreated.length > 0) parts.push(`${s.accountsCreated.length} account${s.accountsCreated.length === 1 ? '' : 's'} created`);
  if (s.failedRows > 0) parts.push(`${s.failedRows} row${s.failedRows === 1 ? '' : 's'} could not be read`);
  return parts.join(' · ') || `${s.rowsParsed} rows processed`;
}
