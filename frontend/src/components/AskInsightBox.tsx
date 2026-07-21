import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { MessageCircleQuestion, Send } from 'lucide-react';
import { ApiError, api } from '../api/client';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';

/**
 * Natural-language Q&A over the caller's spending (ADR 013). Sends the question
 * to insight-service, which grounds the answer in real data via Claude tool use.
 * When AI isn't configured the endpoint returns 503 — surfaced as a gentle note.
 */
export function AskInsightBox() {
  const [question, setQuestion] = useState('');
  const mutation = useMutation({ mutationFn: (q: string) => api.askInsight(q) });

  const notConfigured = mutation.error instanceof ApiError && mutation.error.status === 503;

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const q = question.trim();
    if (q) mutation.mutate(q);
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <MessageCircleQuestion className="size-4 text-primary" aria-hidden="true" />
          Ask about your spending
        </CardTitle>
        <CardDescription>
          e.g. &ldquo;How much did I spend on groceries last month?&rdquo;
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <form onSubmit={onSubmit} className="flex gap-2">
          <Input
            aria-label="Question"
            placeholder="Ask a question…"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            maxLength={500}
          />
          <Button type="submit" disabled={mutation.isPending || question.trim() === ''}>
            {mutation.isPending ? 'Asking…' : <Send className="size-4" aria-hidden="true" />}
          </Button>
        </form>

        {mutation.data && (
          <div className="rounded-lg border bg-muted/40 p-3 text-sm" data-testid="insight-answer">
            <p className="whitespace-pre-wrap">{mutation.data.answer}</p>
          </div>
        )}

        {notConfigured && (
          <p className="text-sm text-muted-foreground" role="status">
            AI answers aren&apos;t enabled here yet. Your monthly summary still works.
          </p>
        )}
        {mutation.isError && !notConfigured && (
          <p className="text-sm text-destructive" role="alert">
            Couldn&apos;t get an answer just now — please try again.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
