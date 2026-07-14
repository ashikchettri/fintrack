import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2, Users } from 'lucide-react';
import { toast } from 'sonner';
import { api } from '../api/client';
import { cn } from '@/lib/utils';

/**
 * Per-transaction toggle to mark it as a shared commitment (ADR 006). Flipping
 * it refreshes both the dashboard and the household view so the shared card
 * updates immediately.
 */
export function ShareToggle({ id, visibility }: { id: string; visibility: string }) {
  const queryClient = useQueryClient();
  const shared = visibility === 'shared';

  const mutation = useMutation({
    mutationFn: () => api.setTransactionVisibility(id, shared ? 'personal' : 'shared'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      void queryClient.invalidateQueries({ queryKey: ['household'] });
    },
    onError: () => toast.error('Could not update sharing'),
  });

  return (
    <button
      type="button"
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      aria-pressed={shared}
      aria-label={shared ? 'Shared with household — click to make personal' : 'Mark as shared with household'}
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        shared
          ? 'border-primary/40 bg-primary/10 text-primary'
          : 'border-input text-muted-foreground hover:text-foreground',
        mutation.isPending && 'opacity-60',
      )}
    >
      {mutation.isPending ? (
        <Loader2 className="size-3 animate-spin" aria-hidden="true" />
      ) : (
        <Users className="size-3" aria-hidden="true" />
      )}
      {shared ? 'Shared' : 'Share'}
    </button>
  );
}
