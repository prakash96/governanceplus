import { useEffect, useState } from 'react';
import { fetchAssistAvailable } from '../api/client';

/**
 * Whether AI assist (Ask AI / Explain) is usable on this server.
 * `null` while the check is in flight — treat that as "not yet", not "no",
 * so a briefly-loading page doesn't flash an unavailable message it has to
 * immediately retract.
 */
export function useAssistAvailability(): boolean | null {
  const [available, setAvailable] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchAssistAvailable()
      .then((result) => {
        if (!cancelled) setAvailable(result);
      })
      .catch(() => {
        if (!cancelled) setAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return available;
}
