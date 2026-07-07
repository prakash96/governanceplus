import { useState } from 'react';
import { testXmlRule, testPomRule, testSwaggerRule } from '../api/client';
import type { RuleTestResult } from '../api/client';

type Tab = 'xml' | 'pom' | 'swagger';

function testVerdict(result: RuleTestResult) {
  return (
    <p className={result.matched ? 'error' : ''}>
      {result.matched
        ? `Matched — ${result.violations.length} violation(s) would be reported.`
        : 'No match — this rule would not flag the sample.'}
    </p>
  );
}

export default function TestSamplePage() {
  const [tab, setTab] = useState<Tab>('xml');

  return (
    <div className="page">
      <h1>Test on Sample</h1>
      <p className="subtitle">
        Try an XPath, pom, or JSONPath expression against pasted sample input without saving
        anything — handy while drafting a rule or checking why an existing one does or doesn't
        fire.
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

      {tab === 'xml' && <XmlTestPanel />}
      {tab === 'pom' && <PomTestPanel />}
      {tab === 'swagger' && <SwaggerTestPanel />}
    </div>
  );
}

function XmlTestPanel() {
  const [xpath, setXpath] = useState('');
  const [usageAttribute, setUsageAttribute] = useState('');
  const [usagePattern, setUsagePattern] = useState('');
  const [sampleXml, setSampleXml] = useState('');
  const [result, setResult] = useState<RuleTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRunTest() {
    if (!xpath.trim() || !sampleXml.trim()) return;
    setTesting(true);
    setError(null);
    try {
      setResult(
        await testXmlRule({
          xpath,
          usageAttribute: usageAttribute || undefined,
          usagePattern: usagePattern || undefined,
          sampleXml,
        }),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="card">
      <label className="field">
        <span>XPath</span>
        <textarea rows={3} value={xpath} onChange={(e) => setXpath(e.target.value)} />
      </label>
      <details>
        <summary>Advanced: cross-file usage check</summary>
        <label className="field">
          <span>Usage attribute (e.g. variableName)</span>
          <input value={usageAttribute} onChange={(e) => setUsageAttribute(e.target.value)} />
        </label>
        <label className="field">
          <span>Usage pattern (e.g. vars.$&#123;variableName&#125;)</span>
          <input value={usagePattern} onChange={(e) => setUsagePattern(e.target.value)} />
        </label>
      </details>
      <label className="field">
        <span>Sample XML</span>
        <p className="subtitle">Paste a full XML document with any xmlns: prefixes your XPath uses declared on the root element.</p>
        <textarea rows={10} className="rules-json" value={sampleXml} onChange={(e) => setSampleXml(e.target.value)} />
      </label>
      <div>
        <button type="button" onClick={handleRunTest} disabled={testing || !xpath.trim() || !sampleXml.trim()}>
          {testing ? 'Testing…' : 'Run test'}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {result && testVerdict(result)}
    </div>
  );
}

function PomTestPanel() {
  const [artifactId, setArtifactId] = useState('');
  const [minVersion, setMinVersion] = useState('');
  const [samplePom, setSamplePom] = useState('');
  const [result, setResult] = useState<RuleTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRunTest() {
    if (!artifactId.trim() || !minVersion.trim() || !samplePom.trim()) return;
    setTesting(true);
    setError(null);
    try {
      setResult(await testPomRule({ artifactId, minVersion, samplePom }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="card">
      <label className="field">
        <span>Artifact ID</span>
        <input value={artifactId} onChange={(e) => setArtifactId(e.target.value)} />
      </label>
      <label className="field">
        <span>Minimum version</span>
        <input value={minVersion} onChange={(e) => setMinVersion(e.target.value)} />
      </label>
      <label className="field">
        <span>Sample pom.xml</span>
        <textarea rows={10} className="rules-json" value={samplePom} onChange={(e) => setSamplePom(e.target.value)} />
      </label>
      <div>
        <button
          type="button"
          onClick={handleRunTest}
          disabled={testing || !artifactId.trim() || !minVersion.trim() || !samplePom.trim()}
        >
          {testing ? 'Testing…' : 'Run test'}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {result && testVerdict(result)}
    </div>
  );
}

function SwaggerTestPanel() {
  const [jsonPath, setJsonPath] = useState('');
  const [sampleSpec, setSampleSpec] = useState('');
  const [result, setResult] = useState<RuleTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRunTest() {
    if (!jsonPath.trim() || !sampleSpec.trim()) return;
    setTesting(true);
    setError(null);
    try {
      setResult(await testSwaggerRule({ jsonPath, sampleSpec }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="card">
      <label className="field">
        <span>JSONPath</span>
        <textarea rows={3} value={jsonPath} onChange={(e) => setJsonPath(e.target.value)} />
      </label>
      <label className="field">
        <span>Sample spec (YAML or JSON)</span>
        <textarea rows={10} className="rules-json" value={sampleSpec} onChange={(e) => setSampleSpec(e.target.value)} />
      </label>
      <div>
        <button type="button" onClick={handleRunTest} disabled={testing || !jsonPath.trim() || !sampleSpec.trim()}>
          {testing ? 'Testing…' : 'Run test'}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {result && testVerdict(result)}
    </div>
  );
}
