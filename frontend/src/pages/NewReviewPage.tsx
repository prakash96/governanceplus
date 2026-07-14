import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { submitReview } from '../api/client';
import type { ProjectSource, ReviewReport } from '../api/client';
import EngineReportSection from '../components/EngineReportSection';

type SourceMode = 'zip' | 'git';

export default function NewReviewPage() {
  const [sourceMode, setSourceMode] = useState<SourceMode>('zip');
  const [file, setFile] = useState<File | null>(null);
  const [gitUrl, setGitUrl] = useState('');
  const [gitBranch, setGitBranch] = useState('');
  const [gitUsername, setGitUsername] = useState('');
  const [gitToken, setGitToken] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<ReviewReport | null>(null);

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
      if (!gitUrl.trim() || !gitBranch.trim()) {
        setError('Enter both a Git URL and a branch first.');
        return;
      }
      source = {
        kind: 'git',
        gitUrl: gitUrl.trim(),
        gitBranch: gitBranch.trim(),
        gitUsername: gitUsername.trim() || undefined,
        gitToken: gitToken.trim() || undefined,
      };
    }

    setSubmitting(true);
    setError(null);
    setReport(null);
    try {
      const result = await submitReview(source);
      setReport(result);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="page-narrow">
        <h1>Mulesoft Governance Review</h1>
        <p className="subtitle">
          Upload a Mulesoft project as a zip file, or point at a Git repository to clone. XML
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
                  className={`chip ${sourceMode === 'git' ? 'chip-active' : ''}`}
                  onClick={() => setSourceMode('git')}
                >
                  Git repository
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
              <>
                <label className="field">
                  <span>Git URL</span>
                  <input
                    type="text"
                    placeholder="e.g. https://github.com/org/repo.git"
                    value={gitUrl}
                    onChange={(e) => setGitUrl(e.target.value)}
                  />
                </label>
                <label className="field">
                  <span>Branch</span>
                  <input
                    type="text"
                    placeholder="e.g. main"
                    value={gitBranch}
                    onChange={(e) => setGitBranch(e.target.value)}
                  />
                  <p className="subtitle">
                    Cloned shallowly (just this branch's latest commit) over HTTPS on the server.
                  </p>
                </label>
                <details>
                  <summary>Private repository?</summary>
                  <label className="field">
                    <span>Username</span>
                    <input
                      type="text"
                      placeholder="any non-empty value usually works alongside a token"
                      value={gitUsername}
                      onChange={(e) => setGitUsername(e.target.value)}
                      autoComplete="off"
                    />
                  </label>
                  <label className="field">
                    <span>Personal access token</span>
                    <input
                      type="password"
                      value={gitToken}
                      onChange={(e) => setGitToken(e.target.value)}
                      autoComplete="off"
                    />
                    <p className="subtitle">
                      Sent once with this request, not stored. Leave both fields blank to use the
                      server's own GIT_USERNAME/GIT_TOKEN configuration instead, if set.
                    </p>
                  </label>
                </details>
              </>
            )}

            {error && <p className="error">{error}</p>}

            <button type="submit" disabled={submitting}>
              {submitting ? 'Running…' : 'Run review'}
            </button>
          </form>
        </div>
      </div>

      {report && (
        <EngineReportSection
          title="Governance Review"
          subtitle="Rule engine — deterministic XPath/pom/JSONPath rules against Mule flow XML, pom.xml dependency versions, and Swagger/OpenAPI specs."
          report={report}
        />
      )}
    </div>
  );
}
