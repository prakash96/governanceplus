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
