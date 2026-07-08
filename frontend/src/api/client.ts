const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

export type ReviewStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type FindingStatus = 'PASS' | 'FAIL' | 'WARNING';
export type Severity = 'CRITICAL' | 'MAJOR' | 'MINOR';

export interface ReviewFinding {
  rule: string;
  category: string;
  status: FindingStatus;
  severity: Severity | null;
  file: string;
  explanation: string;
  recommendation: string;
}

export interface ReviewReport {
  summary: string | null;
  findings: ReviewFinding[];
}

export interface ReviewJobResponse {
  jobId: string;
  status: ReviewStatus;
  /** Deterministic rule-engine result (XML flows + pom.xml + Swagger/OpenAPI specs). */
  report: ReviewReport | null;
  errorMessage: string | null;
}

async function readErrorMessage(res: Response): Promise<string> {
  const text = await res.text();
  return text || `Request failed with status ${res.status}`;
}

/** Thrown by fetchJob specifically for a 404, so callers can tell "gone" apart from other failures. */
export class JobNotFoundError extends Error {}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) throw new Error(await readErrorMessage(res));
  // Empty-body success responses (204, or a void-returning endpoint that Spring
  // serializes as 200 with nothing to parse) — check the actual body, not just
  // the status code, so this doesn't break if a handler's response shape changes.
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export type ProjectSource = { kind: 'zip'; file: File } | { kind: 'path'; projectPath: string };

export async function submitReview(source: ProjectSource): Promise<ReviewJobResponse> {
  const formData = new FormData();
  if (source.kind === 'zip') {
    formData.append('file', source.file);
  } else {
    formData.append('projectPath', source.projectPath);
  }

  const res = await fetch(`${API_BASE}/api/reviews`, { method: 'POST', body: formData });
  if (!res.ok) throw new Error(await readErrorMessage(res));
  return res.json();
}

export async function fetchJob(jobId: string): Promise<ReviewJobResponse> {
  const res = await fetch(`${API_BASE}/api/reviews/${jobId}`);
  if (res.status === 404) {
    throw new JobNotFoundError(await readErrorMessage(res));
  }
  if (!res.ok) throw new Error(await readErrorMessage(res));
  return res.json();
}

// ---- Rules management ----

export interface XmlRule {
  id: string;
  category: string;
  severity: string;
  description: string;
  xpath: string;
  usageAttribute?: string | null;
  usagePattern?: string | null;
  /** Optional glob (e.g. "*-api") matched against the target project's pom.xml artifactId; blank = all projects. */
  projectNamePattern?: string | null;
}

export interface PomRule {
  artifactId: string;
  minVersion: string;
  projectNamePattern?: string | null;
}

export interface SwaggerRule {
  id: string;
  category: string;
  severity: string;
  description: string;
  jsonPath: string;
  projectNamePattern?: string | null;
}

export interface RulesDocument {
  rules: XmlRule[];
  pomRules: PomRule[];
  swaggerRules: SwaggerRule[];
}

export function fetchRules(): Promise<RulesDocument> {
  return requestJson('/api/rules');
}

export function createXmlRule(rule: XmlRule): Promise<XmlRule> {
  return requestJson('/api/rules/xml', { method: 'POST', body: JSON.stringify(rule) });
}

export function updateXmlRule(id: string, rule: XmlRule): Promise<XmlRule> {
  return requestJson(`/api/rules/xml/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(rule) });
}

export function deleteXmlRule(id: string): Promise<void> {
  return requestJson(`/api/rules/xml/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function createPomRule(rule: PomRule): Promise<PomRule> {
  return requestJson('/api/rules/pom', { method: 'POST', body: JSON.stringify(rule) });
}

export function updatePomRule(artifactId: string, rule: PomRule): Promise<PomRule> {
  return requestJson(`/api/rules/pom/${encodeURIComponent(artifactId)}`, { method: 'PUT', body: JSON.stringify(rule) });
}

export function deletePomRule(artifactId: string): Promise<void> {
  return requestJson(`/api/rules/pom/${encodeURIComponent(artifactId)}`, { method: 'DELETE' });
}

export function createSwaggerRule(rule: SwaggerRule): Promise<SwaggerRule> {
  return requestJson('/api/rules/swagger', { method: 'POST', body: JSON.stringify(rule) });
}

export function updateSwaggerRule(id: string, rule: SwaggerRule): Promise<SwaggerRule> {
  return requestJson(`/api/rules/swagger/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(rule) });
}

export function deleteSwaggerRule(id: string): Promise<void> {
  return requestJson(`/api/rules/swagger/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

// ---- Test a rule against pasted sample input, without saving it ----

export interface RuleTestViolation {
  message: string;
  line: number;
}

export interface RuleTestResult {
  matched: boolean;
  violations: RuleTestViolation[];
}

export function testXmlRule(params: {
  xpath: string;
  usageAttribute?: string;
  usagePattern?: string;
  sampleXml: string;
}): Promise<RuleTestResult> {
  return requestJson('/api/rules/xml/test', { method: 'POST', body: JSON.stringify(params) });
}

export function testPomRule(params: { artifactId: string; minVersion: string; samplePom: string }): Promise<RuleTestResult> {
  return requestJson('/api/rules/pom/test', { method: 'POST', body: JSON.stringify(params) });
}

export function testSwaggerRule(params: { jsonPath: string; sampleSpec: string }): Promise<RuleTestResult> {
  return requestJson('/api/rules/swagger/test', { method: 'POST', body: JSON.stringify(params) });
}

// ---- AI assist: helps author or explain a rule; never used in the review pipeline itself ----

export type RuleCategory = 'xml' | 'pom' | 'swagger';

/** False when the server has no usable model configured (e.g. governanceplus.model.path unset/missing). */
export function fetchAssistAvailable(): Promise<boolean> {
  return requestJson<{ available: boolean }>('/api/rules/assist/status').then((r) => r.available);
}

export function generateRuleSuggestion(category: RuleCategory, instruction: string): Promise<string> {
  return requestJson<{ suggestion: string }>('/api/rules/assist/generate', {
    method: 'POST',
    body: JSON.stringify({ category, instruction }),
  }).then((r) => r.suggestion);
}

export function explainRule(category: RuleCategory, rule: Record<string, unknown>): Promise<string> {
  return requestJson<{ explanation: string }>('/api/rules/assist/explain', {
    method: 'POST',
    body: JSON.stringify({ category, rule }),
  }).then((r) => r.explanation);
}
