import { useMemo, useState } from 'react';
import type { FindingStatus, ReviewFinding, ReviewReport } from '../api/client';
import StatusBadge from './StatusBadge';
import SeverityBadge from './SeverityBadge';

type StatusFilter = 'ALL' | FindingStatus;

const STATUS_FILTERS: StatusFilter[] = ['ALL', 'FAIL', 'WARNING', 'PASS'];

function groupByCategory(findings: ReviewFinding[]): Map<string, ReviewFinding[]> {
  const groups = new Map<string, ReviewFinding[]>();
  for (const finding of findings) {
    const category = finding.category || 'General';
    const existing = groups.get(category);
    if (existing) {
      existing.push(finding);
    } else {
      groups.set(category, [finding]);
    }
  }
  return groups;
}

interface Props {
  title: string;
  subtitle?: string;
  report: ReviewReport | null;
  /** Shown instead of the dashboard when report is null. */
  emptyMessage?: string;
}

/** Renders one engine's report as its own dashboard (stats, filters, findings) — used once per engine. */
export default function EngineReportSection({ title, subtitle, report, emptyMessage }: Props) {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const findings = report?.findings;

  const counts = useMemo(() => {
    if (!findings) return null;
    return {
      total: findings.length,
      pass: findings.filter((f) => f.status === 'PASS').length,
      fail: findings.filter((f) => f.status === 'FAIL').length,
      warning: findings.filter((f) => f.status === 'WARNING').length,
      critical: findings.filter((f) => f.severity === 'CRITICAL').length,
      major: findings.filter((f) => f.severity === 'MAJOR').length,
      minor: findings.filter((f) => f.severity === 'MINOR').length,
    };
  }, [findings]);

  const groupedFindings = useMemo(() => {
    if (!findings) return null;
    const filtered = statusFilter === 'ALL' ? findings : findings.filter((f) => f.status === statusFilter);
    return groupByCategory(filtered);
  }, [findings, statusFilter]);

  return (
    <section className="engine-report-section">
      <h2>{title}</h2>
      {subtitle && <p className="subtitle">{subtitle}</p>}

      {!report && <p className="engine-report-empty">{emptyMessage ?? 'No results.'}</p>}

      {report && findings && counts && groupedFindings && (
        <>
          {report.summary && <p className="report-summary">{report.summary}</p>}

          <div className="stat-strip">
            <div className="stat-card">
              <div className="stat-number">{counts.total}</div>
              <div className="stat-label">Rules checked</div>
            </div>
            <div className="stat-card stat-pass">
              <div className="stat-number">{counts.pass}</div>
              <div className="stat-label">Passed</div>
            </div>
            <div className="stat-card stat-fail">
              <div className="stat-number">{counts.fail}</div>
              <div className="stat-label">Failed</div>
            </div>
            <div className="stat-card stat-warning">
              <div className="stat-number">{counts.warning}</div>
              <div className="stat-label">Warnings</div>
            </div>
          </div>

          {(counts.critical > 0 || counts.major > 0 || counts.minor > 0) && (
            <div className="severity-strip">
              {counts.critical > 0 && <span className="badge badge-severity-critical">{counts.critical} Critical</span>}
              {counts.major > 0 && <span className="badge badge-severity-major">{counts.major} Major</span>}
              {counts.minor > 0 && <span className="badge badge-severity-minor">{counts.minor} Minor</span>}
            </div>
          )}

          <div className="filter-chips no-print">
            {STATUS_FILTERS.map((filter) => (
              <button
                key={filter}
                type="button"
                className={`chip ${statusFilter === filter ? 'chip-active' : ''}`}
                onClick={() => setStatusFilter(filter)}
              >
                {filter === 'ALL' ? 'All' : filter.charAt(0) + filter.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          <div className="findings">
            {groupedFindings.size === 0 && <p>No findings match this filter.</p>}
            {Array.from(groupedFindings.entries()).map(([category, categoryFindings]) => (
              <details key={category} className="category-group" open>
                <summary className="category-title">
                  {category} <span className="category-count">({categoryFindings.length})</span>
                </summary>
                {categoryFindings.map((finding, index) => (
                  <details key={index} className="finding-row" open>
                    <summary className="finding-summary">
                      <StatusBadge status={finding.status} />
                      <SeverityBadge severity={finding.severity} />
                      <span className="finding-rule">{finding.rule}</span>
                      {finding.file && <span className="finding-file">{finding.file}</span>}
                    </summary>
                    <div className="finding-details">
                      {finding.explanation && <p>{finding.explanation}</p>}
                      {finding.recommendation && (
                        <p className="finding-recommendation">
                          <strong>Recommendation:</strong> {finding.recommendation}
                        </p>
                      )}
                    </div>
                  </details>
                ))}
              </details>
            ))}
          </div>
        </>
      )}
    </section>
  );
}
