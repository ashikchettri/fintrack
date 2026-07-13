import { Moon, Sun } from 'lucide-react';
import { useTheme } from '@/theme/ThemeProvider';
import { Button } from '@/components/ui/button';

/** Simple light/dark toggle. Clicking picks the opposite of what's showing. */
export function ThemeToggle({ className }: { className?: string }) {
  const { resolved, setTheme } = useTheme();
  const next = resolved === 'dark' ? 'light' : 'dark';

  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      className={className}
      aria-label={`Switch to ${next} theme`}
      onClick={() => setTheme(next)}
    >
      {resolved === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </Button>
  );
}
