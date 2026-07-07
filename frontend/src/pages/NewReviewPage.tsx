import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { fetchJob, JobNotFoundError, submitReview } from '../api/client';
import type { ProjectSource } from '../api/client';
import { getRecentJobs, pushRecentJob, removeRecentJob } from '../recentJobs';

type SourceMode = 'zip' | 'path';

export default function NewReviewPage() {
  const [sourceMode, setSourceMode] = useState<SourceMode>('zip');
  const [file, setFile] = useState<File | null>(null);
  const [projectPath, setProjectPath] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recentJobs, setRecentJobs] = useState<string[]>(() => getRecentJobs());
  const navigate = useNavigate();

  // Job records live in-memory on the server and are purged after a TTL (or
  // lost on restart), but the recent-jobs list is client-side localStorage —
  // so it can drift out of sync. Drop entries the server no longer knows
  // about rather than leaving dead links in the list.
  useEffect(() => {
    let cancelled = false;

    async function pruneStaleJobs() {
      for (const jobId of getRecentJobs()) {
        try {
          await fetchJob(jobId);
        } catch (e) {
          if (e instanceof JobNotFoundError) {
            removeRecentJob(jobId);
          }
        }
      }
      if (!cancelled) setRecentJobs(getRecentJobs());
    }

    pruneStaleJobs();

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    let source: ProjectSource;
    if (sourceMode === 'zip') {
      if (!file) {
        setError('Choose a project zip file first.');
        return;
      }
      source = { kind: 'zip', file };
    } else {
      if (!projectPath.trim()) {
        setError('Enter a project path first.');
        return;
      }
      source = { kind: 'path', projectPath: projectPath.trim() };
    }

    setSubmitting(true);
    setError(null);
    try {
      const job = await submitReview(source);
      pushRecentJob(job.jobId);
      navigate(`/reviews/${job.jobId}`);
    } catch (e) {
      setError((e as Error).message);
      setSubmitting(false);
    }
  }

  return (
    <div className="page page-narrow">
      <h1>Mulesoft Governance Review</h1>
      <p className="subtitle">
        Upload a Mulesoft project (or point at one already on the server) as a zip file. XML
        flows, pom.xml, and Swagger/OpenAPI specs are checked by a deterministic rule engine
        against the rules currently saved in <Link to="/rules">Rules</Link> — edit rules there
        before running if needed.
      </p>

      <div className="card">
        <form onSubmit={handleSubmit} className="review-form">
          <div className="field">
            <span>Project source</span>
            <div className="filter-chips">
              <button
                type="button"
                className={`chip ${sourceMode === 'zip' ? 'chip-active' : ''}`}
                onClick={() => setSourceMode('zip')}
              >
                Upload zip
              </button>
              <button
                type="button"
                className={`chip ${sourceMode === 'path' ? 'chip-active' : ''}`}
                onClick={() => setSourceMode('path')}
              >
                Filesystem path
              </button>
            </div>
          </div>

          {sourceMode === 'zip' ? (
            <label className="field">
              <span>Project zip</span>
              <input
                type="file"
                accept=".zip"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </label>
          ) : (
            <label className="field">
              <span>Project path</span>
              <input
                type="text"
                placeholder="e.g. C:\projects\my-mule-app or /home/me/my-mule-app"
                value={projectPath}
                onChange={(e) => setProjectPath(e.target.value)}
              />
              <p className="subtitle">
                A path on the machine running the backend, not your own machine (unless they're
                the same). Read directly, with no access restriction beyond the backend
                process's own filesystem permissions — only use this where you already trust
                whoever can reach this page with local file access.
              </p>
            </label>
          )}

          {error && <p className="error">{error}</p>}

          <button type="submit" disabled={submitting}>
            {submitting ? 'Submitting…' : 'Run review'}
          </button>
        </form>
      </div>

      {recentJobs.length > 0 && (
        <div className="card recent-jobs">
          <h2>Recent reviews (this browser)</h2>
          <ul>
            {recentJobs.map((jobId) => (
              <li key={jobId}>
                <Link to={`/reviews/${jobId}`}>{jobId}</Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
