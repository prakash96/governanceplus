import type { Severity } from '../api/client';

const LABELS: Record<Severity, string> = {
  CRITICAL: 'Critical',
  MAJOR: 'Major',
  MINOR: 'Minor',
};

export default function SeverityBadge({ severity }: { severity: Severity | null }) {
  if (!severity) return null;
  return <span className={`badge badge-severity-${severity.toLowerCase()}`}>{LABELS[severity]}</span>;
}
