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
  explainRule,
} from '../api/client';
import type { RulesDocument, XmlRule, PomRule, SwaggerRule, RuleCategory } from '../api/client';
import Modal from '../components/Modal';
import { useAssistAvailability } from '../hooks/useAssistAvailability';

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
  const assistAvailable = useAssistAvailability();
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
        flows (XPath), pom.xml dependency versions, and Swagger/OpenAPI specs (JSONPath).{' '}
        {assistAvailable
          ? 'Use the "Test Sample" and "Ask AI" pages in the top menu to try an expression or get help drafting one — the AI never decides pass/fail, only the saved rules below do.'
          : 'Use the "Test Sample" page in the top menu to try an expression against sample input before saving it.'}
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

      {doc && !loading && tab === 'xml' && (
        <XmlRulesPanel rules={doc.rules} onChanged={refresh} assistAvailable={!!assistAvailable} />
      )}
      {doc && !loading && tab === 'pom' && (
        <PomRulesPanel rules={doc.pomRules} onChanged={refresh} assistAvailable={!!assistAvailable} />
      )}
      {doc && !loading && tab === 'swagger' && (
        <SwaggerRulesPanel rules={doc.swaggerRules} onChanged={refresh} assistAvailable={!!assistAvailable} />
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
      <button type="button" className="btn-ghost" onClick={handleExplain} disabled={loading}>
        {loading ? 'Thinking…' : 'Explain'}
      </button>
      {loading && <p className="explanation">Can take a while on CPU — this isn't stuck.</p>}
      {error && <p className="error">{error}</p>}
      {explanation && <p className="explanation">{explanation}</p>}
    </div>
  );
}

// ---- XML rules ----

function XmlRulesPanel({
  rules,
  onChanged,
  assistAvailable,
}: {
  rules: XmlRule[];
  onChanged: () => void;
  assistAvailable: boolean;
}) {
  const [editing, setEditing] = useState<XmlRule | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function closeEditModal() {
    setEditing(null);
    setError(null);
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
                <button type="button" className="btn-ghost" onClick={() => setEditing({ ...rule })}>
                  Edit
                </button>
                <button type="button" className="btn-ghost btn-danger" onClick={() => handleDelete(rule.id)}>
                  Delete
                </button>
                {assistAvailable && <ExplainButton category="xml" rule={rule as unknown as Record<string, unknown>} />}
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

      <button type="button" onClick={() => setEditing({ ...EMPTY_XML_RULE })}>
        Add XML rule
      </button>

      {editing && (
        <Modal
          title={rules.some((r) => r.id === editing.id) ? 'Edit XML rule' : 'Add XML rule'}
          onClose={closeEditModal}
        >
          <form className="rule-form" onSubmit={handleSave}>
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

            <div className="rule-form-actions">
              <button type="submit" disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </button>
              <button type="button" className="btn-secondary" onClick={closeEditModal}>
                Cancel
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  );
}

// ---- POM rules ----

function PomRulesPanel({
  rules,
  onChanged,
  assistAvailable,
}: {
  rules: PomRule[];
  onChanged: () => void;
  assistAvailable: boolean;
}) {
  const [editing, setEditing] = useState<PomRule | null>(null);
  const [originalArtifactId, setOriginalArtifactId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function startEdit(rule: PomRule) {
    setEditing({ ...rule });
    setOriginalArtifactId(rule.artifactId);
  }

  function closeEditModal() {
    setEditing(null);
    setOriginalArtifactId(null);
    setError(null);
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
      closeEditModal();
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
                <button type="button" className="btn-ghost" onClick={() => startEdit(rule)}>
                  Edit
                </button>
                <button type="button" className="btn-ghost btn-danger" onClick={() => handleDelete(rule.artifactId)}>
                  Delete
                </button>
                {assistAvailable && <ExplainButton category="pom" rule={rule as unknown as Record<string, unknown>} />}
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

      <button type="button" onClick={() => setEditing({ ...EMPTY_POM_RULE })}>
        Add pom rule
      </button>

      {editing && (
        <Modal title={originalArtifactId ? 'Edit pom rule' : 'Add pom rule'} onClose={closeEditModal}>
          <form className="rule-form" onSubmit={handleSave}>
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

            <div className="rule-form-actions">
              <button type="submit" disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </button>
              <button type="button" className="btn-secondary" onClick={closeEditModal}>
                Cancel
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  );
}

// ---- Swagger rules ----

function SwaggerRulesPanel({
  rules,
  onChanged,
  assistAvailable,
}: {
  rules: SwaggerRule[];
  onChanged: () => void;
  assistAvailable: boolean;
}) {
  const [editing, setEditing] = useState<SwaggerRule | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function closeEditModal() {
    setEditing(null);
    setError(null);
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
                <button type="button" className="btn-ghost" onClick={() => setEditing({ ...rule })}>
                  Edit
                </button>
                <button type="button" className="btn-ghost btn-danger" onClick={() => handleDelete(rule.id)}>
                  Delete
                </button>
                {assistAvailable && <ExplainButton category="swagger" rule={rule as unknown as Record<string, unknown>} />}
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

      <button type="button" onClick={() => setEditing({ ...EMPTY_SWAGGER_RULE })}>
        Add swagger rule
      </button>

      {editing && (
        <Modal
          title={rules.some((r) => r.id === editing.id) ? 'Edit swagger rule' : 'Add swagger rule'}
          onClose={closeEditModal}
        >
          <form className="rule-form" onSubmit={handleSave}>
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

            <div className="rule-form-actions">
              <button type="submit" disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </button>
              <button type="button" className="btn-secondary" onClick={closeEditModal}>
                Cancel
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  );
}
