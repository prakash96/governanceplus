const STORAGE_KEY = 'governanceplus.recentJobs';
const MAX_RECENT = 10;

export function getRecentJobs(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

export function pushRecentJob(jobId: string): void {
  const jobs = [jobId, ...getRecentJobs().filter((id) => id !== jobId)].slice(0, MAX_RECENT);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(jobs));
}

/** Drops a job (e.g. one the server 404s on — expired or gone after a restart) from the list. */
export function removeRecentJob(jobId: string): void {
  const jobs = getRecentJobs().filter((id) => id !== jobId);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(jobs));
}
