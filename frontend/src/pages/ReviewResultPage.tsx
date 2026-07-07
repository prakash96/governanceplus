import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { fetchJob, JobNotFoundError } from '../api/client';
import type { ReviewJobResponse } from '../api/client';
import { removeRecentJob } from '../recentJobs';
import EngineReportSection from '../components/EngineReportSection';

const POLL_INTERVAL_MS = 2000;

export default function ReviewResultPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const [job, setJob] = useState<ReviewJobResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (!jobId) return;
    let cancelled = false;

    async function poll() {
      try {
        const result = await fetchJob(jobId!);
        if (cancelled) return;
        setJob(result);
        if (result.status === 'QUEUED' || result.status === 'RUNNING') {
          timerRef.current = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch (e) {
        if (cancelled) return;
        if (e instanceof JobNotFoundError) {
          removeRecentJob(jobId!);
          setError('This review no longer exists — it may have expired or the server was restarted.');
        } else {
          setError((e as Error).message);
        }
      }
    }

    poll();

    return () => {
      cancelled = true;
      window.clearTimeout(timerRef.current);
    };
  }, [jobId]);

  if (error) {
    return (
      <div className="page">
        <p className="error">{error}</p>
      </div>
    );
  }

  if (!job) {
    return (
      <div className="page">
        <p>Loading job…</p>
      </div>
    );
  }

  return (
    <div className="page report-page">
      <div className="report-header">
        <div>
          <h1>Review report</h1>
          <p className="job-id">{job.jobId}</p>
        </div>
        <div className="report-header-actions">
          <span className={`status status-${job.status.toLowerCase()}`}>{job.status}</span>
          {job.status === 'COMPLETED' && (
            <button type="button" className="no-print" onClick={() => window.print()}>
              Print / Export
            </button>
          )}
        </div>
      </div>

      {(job.status === 'QUEUED' || job.status === 'RUNNING') && (
        <div className="card">
          <p>Working on it — this page updates automatically.</p>
        </div>
      )}

      {job.status === 'FAILED' && (
        <div className="card">
          <p className="error">{job.errorMessage}</p>
        </div>
      )}

      {job.status === 'COMPLETED' && (
        <EngineReportSection
          title="Governance Review"
          subtitle="Rule engine — deterministic XPath/pom/JSONPath rules against Mule flow XML, pom.xml dependency versions, and Swagger/OpenAPI specs."
          report={job.report}
        />
      )}
    </div>
  );
}
