import type { FindingStatus } from '../api/client';

const LABELS: Record<FindingStatus, string> = {
  PASS: 'Pass',
  FAIL: 'Fail',
  WARNING: 'Warning',
};

export default function StatusBadge({ status }: { status: FindingStatus }) {
  return <span className={`badge badge-status-${status.toLowerCase()}`}>{LABELS[status]}</span>;
}
