import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  fetchRules,
  createXmlRule,
  updateXmlRule,
  deleteXmlRule,
  createPomRule,
  updatePomRule,
  deletePomRule,
  createSwaggerRule,
  updateSwaggerRule,
  deleteSwaggerRule,
  testXmlRule,
  testPomRule,
  testSwaggerRule,
  generateRuleSuggestion,
  explainRule,
} from '../api/client';
import type { RulesDocument, XmlRule, PomRule, SwaggerRule, RuleTestResult, RuleCategory } from '../api/client';

type Tab = 'xml' | 'pom' | 'swagger';

const EMPTY_XML_RULE: XmlRule = { id: '', category: '', severity: 'MAJOR', description: '', xpath: '', usageAttribute: '', usagePattern: '', projectNamePattern: '' };
const EMPTY_POM_RULE: PomRule = { artifactId: '', minVersion: '', projectNamePattern: '' };
const EMPTY_SWAGGER_RULE: SwaggerRule = { id: '', category: '', severity: 'MAJOR', description: '', jsonPath: '', projectNamePattern: '' };

function ProjectNamePatternField({
  value,
  onChange,
}: {
  value: string | null | undefined;
  onChange: (value: string) => void;
}) {
  return (
    <label className="field">
      <span>Project name pattern (optional)</span>
      <input
        placeholder="e.g. *-api — leave blank to apply to every project"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
      />
      <p className="subtitle">
        Glob matched against the target project's pom.xml artifactId. Blank means this rule
        applies to every project.
      </p>
    </label>
  );
}

function projectScopeLabel(pattern: string | null | undefined): string {
  return pattern && pattern.trim() ? pattern : 'All projects';
}

export default function RulesPage() {
  const [doc, setDoc] = useState<RulesDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('xml');

  function refresh() {
    setLoading(true);
    fetchRules()
      .then(setDoc)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  return (
    <div className="page">
      <h1>Rules</h1>
      <p className="subtitle">
        View, add, edit, and delete the rules the review engine checks projects against — XML/Mule
        flows (XPath), pom.xml dependency versions, and Swagger/OpenAPI specs (JSONPath). Test any
        rule against pasted sample input before saving it, and use "Ask AI" for help writing or
        understanding a rule — the AI never decides pass/fail, only the rules below do.
      </p>

      <div className="filter-chips">
        <button type="button" className={`chip ${tab === 'xml' ? 'chip-active' : ''}`} onClick={() => setTab('xml')}>
          XML / Mule
        </button>
        <button type="button" className={`chip ${tab === 'pom' ? 'chip-active' : ''}`} onClick={() => setTab('pom')}>
          POM
        </button>
        <button type="button" className={`chip ${tab === 'swagger' ? 'chip-active' : ''}`} onClick={() => setTab('swagger')}>
          Swagger
        </button>
      </div>

      {error && <p className="error">{error}</p>}
      {loading && <p>Loading rules…</p>}

      {doc && !loading && tab === 'xml' && <XmlRulesPanel rules={doc.rules} onChanged={refresh} />}
      {doc && !loading && tab === 'pom' && <PomRulesPanel rules={doc.pomRules} onChanged={refresh} />}
      {doc && !loading && tab === 'swagger' && <SwaggerRulesPanel rules={doc.swaggerRules} onChanged={refresh} />}
    </div>
  );
}

// ---- Ask AI panel, reused by all three categories ----

function AskAiPanel({ category, onInsert }: { category: RuleCategory; onInsert: (suggestion: string) => void }) {
  const [instruction, setInstruction] = useState('');
  const [suggestion, setSuggestion] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleAsk() {
    if (!instruction.trim()) return;
    setAsking(true);
    setError(null);
    try {
      const result = await generateRuleSuggestion(category, instruction.trim());
      setSuggestion(result);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setAsking(false);
    }
  }

  return (
    <div className="ask-ai-panel">
      <h3>Ask AI</h3>
      <p className="subtitle">
        Describe what you want the rule to check. The suggestion is free text to read and adapt —
        it's not saved automatically.
      </p>
      <textarea
        rows={2}
        placeholder="e.g. flag any HTTP listener that doesn't require HTTPS"
        value={instruction}
        onChange={(e) => setInstruction(e.target.value)}
      />
      <button type="button" onClick={handleAsk} disabled={asking || !instruction.trim()}>
        {asking ? 'Asking…' : 'Ask AI'}
      </button>
      {error && <p className="error">{error}</p>}
      {suggestion && (
        <div className="ai-suggestion">
          <pre>{suggestion}</pre>
          <button type="button" onClick={() => onInsert(suggestion)}>
            Copy into description
          </button>
        </div>
      )}
    </div>
  );
}

function ExplainButton({ category, rule }: { category: RuleCategory; rule: Record<string, unknown> }) {
  const [explanation, setExplanation] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleExplain() {
    setLoading(true);
    setError(null);
    try {
      setExplanation(await explainRule(category, rule));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="explain-cell">
      <button type="button" className="chip" onClick={handleExplain} disabled={loading}>
        {loading ? 'Explaining…' : 'Explain'}
      </button>
      {error && <p className="error">{error}</p>}
      {explanation && <p className="explanation">{explanation}</p>}
    </div>
  );
}

// ---- XML rules ----

function XmlRulesPanel({ rules, onChanged }: { rules: XmlRule[]; onChanged: () => void }) {
  const [editing, setEditing] = useState<XmlRule | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<RuleTestResult | null>(null);
  const [sampleXml, setSampleXml] = useState('');
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);

  function startAdd() {
    setEditing({ ...EMPTY_XML_RULE });
    setTestResult(null);
    setShowForm(true);
  }

  function startEdit(rule: XmlRule) {
    setEditing({ ...rule });
    setTestResult(null);
    setShowForm(true);
  }

  async function handleDelete(id: string) {
    if (!confirm(`Delete XML rule "${id}"?`)) return;
    try {
      await deleteXmlRule(id);
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function handleTest() {
    if (!editing || !sampleXml.trim()) return;
    setTesting(true);
    setError(null);
    try {
      setTestResult(
        await testXmlRule({
          xpath: editing.xpath,
          usageAttribute: editing.usageAttribute ?? undefined,
          usagePattern: editing.usagePattern ?? undefined,
          sampleXml,
        }),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setTesting(false);
    }
  }

  async function handleSave(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setSaving(true);
    setError(null);
    try {
      const existed = rules.find((r) => r.id === editing.id);
      if (existed) {
        await updateXmlRule(existed.id, editing);
      } else {
        await createXmlRule(editing);
      }
      setShowForm(false);
      setEditing(null);
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      {error && <p className="error">{error}</p>}
      <table className="rules-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Category</th>
            <th>Severity</th>
            <th>Description</th>
            <th>XPath</th>
            <th>Applies to</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule) => (
            <tr key={rule.id}>
              <td>{rule.id}</td>
              <td>{rule.category}</td>
              <td>{rule.severity}</td>
              <td>{rule.description}</td>
              <td>
                <code>{rule.xpath}</code>
              </td>
              <td>{projectScopeLabel(rule.projectNamePattern)}</td>
              <td className="rules-table-actions">
                <button type="button" className="chip" onClick={() => startEdit(rule)}>
                  Edit
                </button>
                <button type="button" className="chip" onClick={() => handleDelete(rule.id)}>
                  Delete
                </button>
                <ExplainButton category="xml" rule={rule as unknown as Record<string, unknown>} />
              </td>
            </tr>
          ))}
          {rules.length === 0 && (
            <tr>
              <td colSpan={7}>No XML rules yet.</td>
            </tr>
          )}
        </tbody>
      </table>

      {!showForm && (
        <button type="button" onClick={startAdd}>
          Add XML rule
        </button>
      )}

      {showForm && editing && (
        <form className="rule-form" onSubmit={handleSave}>
          <h2>{rules.some((r) => r.id === editing.id) ? 'Edit' : 'Add'} XML rule</h2>
          <label className="field">
            <span>ID</span>
            <input value={editing.id} onChange={(e) => setEditing({ ...editing, id: e.target.value })} required />
          </label>
          <label className="field">
            <span>Category</span>
            <input value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })} />
          </label>
          <label className="field">
            <span>Severity</span>
            <input value={editing.severity} onChange={(e) => setEditing({ ...editing, severity: e.target.value })} />
          </label>
          <label className="field">
            <span>Description</span>
            <input
              value={editing.description}
              onChange={(e) => setEditing({ ...editing, description: e.target.value })}
            />
          </label>
          <label className="field">
            <span>XPath</span>
            <textarea rows={3} value={editing.xpath} onChange={(e) => setEditing({ ...editing, xpath: e.target.value })} required />
          </label>
          <details>
            <summary>Advanced: cross-file usage check</summary>
            <label className="field">
              <span>Usage attribute (e.g. variableName)</span>
              <input
                value={editing.usageAttribute ?? ''}
                onChange={(e) => setEditing({ ...editing, usageAttribute: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Usage pattern (e.g. vars.$&#123;variableName&#125;)</span>
              <input
                value={editing.usagePattern ?? ''}
                onChange={(e) => setEditing({ ...editing, usagePattern: e.target.value })}
              />
            </label>
          </details>

          <ProjectNamePatternField
            value={editing.projectNamePattern}
            onChange={(v) => setEditing({ ...editing, projectNamePattern: v })}
          />

          <section className="test-panel">
            <h3>Test on sample</h3>
            <p className="subtitle">
              Paste a full XML document (with any xmlns: prefixes your XPath uses declared on the
              root element).
            </p>
            <textarea rows={8} className="rules-json" value={sampleXml} onChange={(e) => setSampleXml(e.target.value)} />
            <button type="button" onClick={handleTest} disabled={testing || !sampleXml.trim()}>
              {testing ? 'Testing…' : 'Run test'}
            </button>
            {testResult && (
              <p className={testResult.matched ? 'error' : ''}>
                {testResult.matched
                  ? `Matched — ${testResult.violations.length} violation(s) would be reported.`
                  : 'No match — this rule would not flag the sample.'}
              </p>
            )}
          </section>

          <AskAiPanel category="xml" onInsert={(s) => setEditing({ ...editing, description: s })} />

          <div className="rule-form-actions">
            <button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setEditing(null);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

// ---- POM rules ----

function PomRulesPanel({ rules, onChanged }: { rules: PomRule[]; onChanged: () => void }) {
  const [editing, setEditing] = useState<PomRule | null>(null);
  const [originalArtifactId, setOriginalArtifactId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<RuleTestResult | null>(null);
  const [samplePom, setSamplePom] = useState('');
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);

  function startAdd() {
    setEditing({ ...EMPTY_POM_RULE });
    setOriginalArtifactId(null);
    setTestResult(null);
    setShowForm(true);
  }

  function startEdit(rule: PomRule) {
    setEditing({ ...rule });
    setOriginalArtifactId(rule.artifactId);
    setTestResult(null);
    setShowForm(true);
  }

  async function handleDelete(artifactId: string) {
    if (!confirm(`Delete pom rule "${artifactId}"?`)) return;
    try {
      await deletePomRule(artifactId);
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function handleTest() {
    if (!editing || !samplePom.trim()) return;
    setTesting(true);
    setError(null);
    try {
      setTestResult(await testPomRule({ artifactId: editing.artifactId, minVersion: editing.minVersion, samplePom }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setTesting(false);
    }
  }

  async function handleSave(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setSaving(true);
    setError(null);
    try {
      if (originalArtifactId) {
        await updatePomRule(originalArtifactId, editing);
      } else {
        await createPomRule(editing);
      }
      setShowForm(false);
      setEditing(null);
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      {error && <p className="error">{error}</p>}
      <table className="rules-table">
        <thead>
          <tr>
            <th>Artifact ID</th>
            <th>Min version</th>
            <th>Applies to</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule) => (
            <tr key={rule.artifactId}>
              <td>{rule.artifactId}</td>
              <td>{rule.minVersion}</td>
              <td>{projectScopeLabel(rule.projectNamePattern)}</td>
              <td className="rules-table-actions">
                <button type="button" className="chip" onClick={() => startEdit(rule)}>
                  Edit
                </button>
                <button type="button" className="chip" onClick={() => handleDelete(rule.artifactId)}>
                  Delete
                </button>
                <ExplainButton category="pom" rule={rule as unknown as Record<string, unknown>} />
              </td>
            </tr>
          ))}
          {rules.length === 0 && (
            <tr>
              <td colSpan={4}>No pom rules yet.</td>
            </tr>
          )}
        </tbody>
      </table>

      {!showForm && (
        <button type="button" onClick={startAdd}>
          Add pom rule
        </button>
      )}

      {showForm && editing && (
        <form className="rule-form" onSubmit={handleSave}>
          <h2>{originalArtifactId ? 'Edit' : 'Add'} pom rule</h2>
          <label className="field">
            <span>Artifact ID</span>
            <input value={editing.artifactId} onChange={(e) => setEditing({ ...editing, artifactId: e.target.value })} required />
          </label>
          <label className="field">
            <span>Minimum version</span>
            <input value={editing.minVersion} onChange={(e) => setEditing({ ...editing, minVersion: e.target.value })} required />
          </label>

          <ProjectNamePatternField
            value={editing.projectNamePattern}
            onChange={(v) => setEditing({ ...editing, projectNamePattern: v })}
          />

          <section className="test-panel">
            <h3>Test on sample</h3>
            <p className="subtitle">Paste a full, valid pom.xml.</p>
            <textarea rows={8} className="rules-json" value={samplePom} onChange={(e) => setSamplePom(e.target.value)} />
            <button type="button" onClick={handleTest} disabled={testing || !samplePom.trim()}>
              {testing ? 'Testing…' : 'Run test'}
            </button>
            {testResult && (
              <p className={testResult.matched ? 'error' : ''}>
                {testResult.matched
                  ? `Matched — ${testResult.violations.length} violation(s) would be reported.`
                  : 'No match — this rule would not flag the sample.'}
              </p>
            )}
          </section>

          <AskAiPanel category="pom" onInsert={(s) => setEditing({ ...editing, minVersion: editing.minVersion || s })} />

          <div className="rule-form-actions">
            <button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setEditing(null);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

// ---- Swagger rules ----

function SwaggerRulesPanel({ rules, onChanged }: { rules: SwaggerRule[]; onChanged: () => void }) {
  const [editing, setEditing] = useState<SwaggerRule | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<RuleTestResult | null>(null);
  const [sampleSpec, setSampleSpec] = useState('');
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);

  function startAdd() {
    setEditing({ ...EMPTY_SWAGGER_RULE });
    setTestResult(null);
    setShowForm(true);
  }

  function startEdit(rule: SwaggerRule) {
    setEditing({ ...rule });
    setTestResult(null);
    setShowForm(true);
  }

  async function handleDelete(id: string) {
    if (!confirm(`Delete swagger rule "${id}"?`)) return;
    try {
      await deleteSwaggerRule(id);
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function handleTest() {
    if (!editing || !sampleSpec.trim()) return;
    setTesting(true);
    setError(null);
    try {
      setTestResult(await testSwaggerRule({ jsonPath: editing.jsonPath, sampleSpec }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setTesting(false);
    }
  }

  async function handleSave(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setSaving(true);
    setError(null);
    try {
      const existed = rules.find((r) => r.id === editing.id);
      if (existed) {
        await updateSwaggerRule(existed.id, editing);
      } else {
        await createSwaggerRule(editing);
      }
      setShowForm(false);
      setEditing(null);
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      {error && <p className="error">{error}</p>}
      <table className="rules-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Category</th>
            <th>Severity</th>
            <th>Description</th>
            <th>JSONPath</th>
            <th>Applies to</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule) => (
            <tr key={rule.id}>
              <td>{rule.id}</td>
              <td>{rule.category}</td>
              <td>{rule.severity}</td>
              <td>{rule.description}</td>
              <td>
                <code>{rule.jsonPath}</code>
              </td>
              <td>{projectScopeLabel(rule.projectNamePattern)}</td>
              <td className="rules-table-actions">
                <button type="button" className="chip" onClick={() => startEdit(rule)}>
                  Edit
                </button>
                <button type="button" className="chip" onClick={() => handleDelete(rule.id)}>
                  Delete
                </button>
                <ExplainButton category="swagger" rule={rule as unknown as Record<string, unknown>} />
              </td>
            </tr>
          ))}
          {rules.length === 0 && (
            <tr>
              <td colSpan={7}>No swagger rules yet.</td>
            </tr>
          )}
        </tbody>
      </table>

      {!showForm && (
        <button type="button" onClick={startAdd}>
          Add swagger rule
        </button>
      )}

      {showForm && editing && (
        <form className="rule-form" onSubmit={handleSave}>
          <h2>{rules.some((r) => r.id === editing.id) ? 'Edit' : 'Add'} swagger rule</h2>
          <label className="field">
            <span>ID</span>
            <input value={editing.id} onChange={(e) => setEditing({ ...editing, id: e.target.value })} required />
          </label>
          <label className="field">
            <span>Category</span>
            <input value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })} />
          </label>
          <label className="field">
            <span>Severity</span>
            <input value={editing.severity} onChange={(e) => setEditing({ ...editing, severity: e.target.value })} />
          </label>
          <label className="field">
            <span>Description</span>
            <input
              value={editing.description}
              onChange={(e) => setEditing({ ...editing, description: e.target.value })}
            />
          </label>
          <label className="field">
            <span>JSONPath</span>
            <textarea rows={3} value={editing.jsonPath} onChange={(e) => setEditing({ ...editing, jsonPath: e.target.value })} required />
          </label>

          <ProjectNamePatternField
            value={editing.projectNamePattern}
            onChange={(v) => setEditing({ ...editing, projectNamePattern: v })}
          />

          <section className="test-panel">
            <h3>Test on sample</h3>
            <p className="subtitle">Paste a full OpenAPI spec (YAML or JSON).</p>
            <textarea rows={8} className="rules-json" value={sampleSpec} onChange={(e) => setSampleSpec(e.target.value)} />
            <button type="button" onClick={handleTest} disabled={testing || !sampleSpec.trim()}>
              {testing ? 'Testing…' : 'Run test'}
            </button>
            {testResult && (
              <p className={testResult.matched ? 'error' : ''}>
                {testResult.matched
                  ? `Matched — ${testResult.violations.length} violation(s) would be reported.`
                  : 'No match — this rule would not flag the sample.'}
              </p>
            )}
          </section>

          <AskAiPanel category="swagger" onInsert={(s) => setEditing({ ...editing, description: s })} />

          <div className="rule-form-actions">
            <button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setEditing(null);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
