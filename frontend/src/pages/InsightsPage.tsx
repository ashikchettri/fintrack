import { AppShell } from '@/components/AppShell';
import { AskInsightBox } from '@/components/AskInsightBox';
import { InsightsSummaryCard } from '@/components/InsightsSummaryCard';

/** AI spending insights: the monthly summary + natural-language Q&A. */
export default function InsightsPage() {
  return (
    <AppShell>
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Insights</h1>
          <p className="text-sm text-muted-foreground">
            A summary of where your money went — and answers to your questions about it.
          </p>
        </div>
        <InsightsSummaryCard />
        <AskInsightBox />
      </div>
    </AppShell>
  );
}
