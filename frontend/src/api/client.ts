// Built from the browser's own address bar (protocol/hostname/port) rather than hardcoded, so the
// same build works whether it's opened as localhost, a LAN IP, or a real hostname — no rebuild or
// per-environment config needed. VITE_API_BASE_URL still wins when explicitly set (e.g. pointing
// a local dev server at a remote/staging backend).
const API_BASE =
  import.meta.env.VITE_API_BASE_URL ??
  `${window.location.protocol}//${window.location.hostname}${window.location.port ? `:${window.location.port}` : ''}`;

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

async function readErrorMessage(res: Response): Promise<string> {
  const text = await res.text();
  return text || `Request failed with status ${res.status}`;
}

// ---- Admin auth ----
//
// Deliberately simple: the backend has no session store either (see reviewer-mule's auth.xml) —
// "logging in" just verifies a username/password against Mule global properties and hands back a
// token that IS the HTTP Basic-Auth encoding of those same credentials. There's nothing to expire
// or invalidate server-side; the token is re-verified on every mutating rules request. Stored in
// sessionStorage (not localStorage) so it doesn't outlive the browser tab.

const ADMIN_TOKEN_STORAGE_KEY = 'governanceplus.adminToken';

let adminToken: string | null = sessionStorage.getItem(ADMIN_TOKEN_STORAGE_KEY);

export function getAdminToken(): string | null {
  return adminToken;
}

function setAdminToken(token: string | null): void {
  adminToken = token;
  if (token) {
    sessionStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, token);
  } else {
    sessionStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY);
  }
}

export interface LoginResponse {
  role: string;
  token: string;
}

export async function login(username: string, password: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error(await readErrorMessage(res));
  const result: LoginResponse = await res.json();
  setAdminToken(result.token);
}

export function logout(): void {
  setAdminToken(null);
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (adminToken) {
    headers['Authorization'] = `Basic ${adminToken}`;
  }

  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (!res.ok) {
    // A token that the server no longer accepts (wrong/stale) is useless going forward — drop it
    // so the UI reverts to a logged-out state instead of silently retrying with the same bad token.
    if (res.status === 401) {
      setAdminToken(null);
    }
    throw new Error(await readErrorMessage(res));
  }
  // Empty-body success responses (204, or a void-returning endpoint that Spring
  // serializes as 200 with nothing to parse) — check the actual body, not just
  // the status code, so this doesn't break if a handler's response shape changes.
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export type ProjectSource =
  | { kind: 'zip'; file: File }
  | { kind: 'path'; projectPath: string }
  | { kind: 'git'; gitUrl: string; gitBranch: string; gitUsername?: string; gitToken?: string };

/** Runs synchronously on the server — the report comes back directly, no job to poll. */
export async function submitReview(source: ProjectSource): Promise<ReviewReport> {
  const formData = new FormData();
  if (source.kind === 'zip') {
    formData.append('file', source.file);
  } else if (source.kind === 'git') {
    formData.append('gitUrl', source.gitUrl);
    formData.append('gitBranch', source.gitBranch);
    // Only sent for a private repo — omitted entirely (not even as empty strings) when not
    // provided, so the server's own GIT_USERNAME/GIT_TOKEN env-var fallback still applies.
    if (source.gitUsername) formData.append('gitUsername', source.gitUsername);
    if (source.gitToken) formData.append('gitToken', source.gitToken);
  } else {
    formData.append('projectPath', source.projectPath);
  }

  const res = await fetch(`${API_BASE}/api/reviews`, { method: 'POST', body: formData });
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

/**
 * A swagger rule's `category` is free text as far as the backend/rule engine are
 * concerned, but the Rules UI constrains it to these to drive the condition
 * builder's scope (which part of the spec it guides you into, and which quick
 * checks it offers). "Other" is the escape hatch for anything that doesn't fit
 * — and for rules authored before this categorization existed.
 */
export type SwaggerRuleCategory = 'Request Schema Validation' | 'Response Schema Validation' | 'Header Validation' | 'Other';

export const SWAGGER_RULE_CATEGORIES: SwaggerRuleCategory[] = [
  'Request Schema Validation',
  'Response Schema Validation',
  'Header Validation',
  'Other',
];

export interface SwaggerRule {
  id: string;
  category: string;
  severity: string;
  description: string;
  jsonPath: string;
  projectNamePattern?: string | null;
  /**
   * Set only by the Rules UI's Selection + Assertion condition builder, so
   * reopening a rule for editing can restore that state instead of falling
   * back to a plain composed JSONPath. The rule engine never reads these —
   * jsonPath (composed from them) is the only field it evaluates.
   */
  selection?: string | null;
  operator?: string | null;
  value?: string | null;
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
