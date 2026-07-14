import { useState } from 'react';
import { testSwaggerRule } from '../api/client';
import type { SwaggerRuleCategory } from '../api/client';

/**
 * Splits swagger rule authoring into two plain fields instead of one opaque
 * JSONPath expression:
 *   Selection — a JSONPath ending at the field you care about (e.g.
 *     "$.paths.*.*.requestBody.content.*.schema.additionalProperties").
 *   Assertion — what must be true about that field (is missing / equals /
 *     contains / etc.).
 * The two compose into the JSONPath the rule engine actually evaluates
 * (reviewer-core's SwaggerRuleEvaluator only ever reads jsonPath — it has no
 * idea Selection/Assertion exist). Editing the composed JSONPath directly
 * detaches it from Selection/Assertion, for compound expressions (like the
 * quick checks below) that don't reduce to "one field, one condition".
 *
 * There's no spec-tree browser here — paste a sample spec (YAML or JSON) and
 * use "Test against sample" to check a Selection/Assertion against it instead
 * of clicking through a tree.
 */

type ConditionOperator =
  | 'EXISTS'
  | 'NOT_EXISTS'
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'CONTAINS'
  | 'MATCHES'
  | 'GREATER_THAN'
  | 'LESS_THAN';

const OPERATOR_OPTIONS: { value: ConditionOperator; label: string; needsValue: boolean }[] = [
  { value: 'NOT_EXISTS', label: 'is missing (flag when a required field is absent)', needsValue: false },
  { value: 'EXISTS', label: 'is present (flag when found)', needsValue: false },
  { value: 'EQUALS', label: 'equals', needsValue: true },
  { value: 'NOT_EQUALS', label: 'does not equal', needsValue: true },
  { value: 'CONTAINS', label: 'contains', needsValue: true },
  { value: 'MATCHES', label: 'matches regex', needsValue: true },
  { value: 'GREATER_THAN', label: 'is greater than', needsValue: true },
  { value: 'LESS_THAN', label: 'is less than', needsValue: true },
];

function isSimpleIdentifier(key: string): boolean {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(key);
}

function atExpr(key: string): string {
  if (key === '*') return '@';
  return isSimpleIdentifier(key) ? `@.${key}` : `@['${key.replace(/'/g, "\\'")}']`;
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
}

function valueLiteral(value: string): string {
  const trimmed = value.trim();
  if (trimmed !== '' && !Number.isNaN(Number(trimmed))) return trimmed;
  if (trimmed === 'true' || trimmed === 'false') return trimmed;
  return `'${value.replace(/'/g, "\\'")}'`;
}

/** Splits a Selection's last segment off so the Assertion can be composed as
 * `parent[?(condition-on-lastKey)]` — the shape Jayway JsonPath filters need. */
function splitLastSegment(selection: string): { parent: string; lastKey: string } {
  const bracketQuoted = selection.match(/^(.*)\['((?:[^'\\]|\\.)*)'\]$/);
  if (bracketQuoted) return { parent: bracketQuoted[1], lastKey: bracketQuoted[2].replace(/\\'/g, "'") };
  const bracketStar = selection.match(/^(.*)\[\*\]$/);
  if (bracketStar) return { parent: bracketStar[1], lastKey: '*' };
  const dotted = selection.match(/^(.*)\.([A-Za-z_][A-Za-z0-9_]*)$/);
  if (dotted) return { parent: dotted[1], lastKey: dotted[2] };
  return { parent: selection, lastKey: '' };
}

function composeJsonPath(selection: string, operator: ConditionOperator, value: string): string {
  if (operator === 'EXISTS') return selection;
  const { parent, lastKey } = splitLastSegment(selection);
  const key = atExpr(lastKey);
  switch (operator) {
    case 'NOT_EXISTS':
      return `${parent}[?(!${key})]`;
    case 'EQUALS':
      return `${parent}[?(${key} == ${valueLiteral(value)})]`;
    case 'NOT_EQUALS':
      return `${parent}[?(${key} != ${valueLiteral(value)})]`;
    case 'CONTAINS':
      return `${parent}[?(${key} =~ /.*${escapeRegex(value)}.*/i)]`;
    case 'MATCHES':
      return `${parent}[?(${key} =~ /${value}/)]`;
    case 'GREATER_THAN':
      return `${parent}[?(${key} > ${Number(value) || 0})]`;
    case 'LESS_THAN':
      return `${parent}[?(${key} < ${Number(value) || 0})]`;
    default:
      return selection;
  }
}

/**
 * Canned expressions for governance checks that don't reduce to "one field,
 * one condition" — e.g. because they need two ANDed conditions, or because
 * the field they target may be written inline OR reached only via $ref
 * (which SwaggerRefResolver resolves before evaluation, but which a single
 * Selection + Assertion pair can't express on its own). Applying one sets
 * jsonPath directly and clears Selection/Assertion.
 */
interface QuickCheckPreset {
  key: string;
  label: string;
  hint: string;
  jsonPath: string;
  suggestedDescription: string;
}

const QUICK_CHECKS_BY_CATEGORY: Record<SwaggerRuleCategory, QuickCheckPreset[]> = {
  'Request Schema Validation': [
    {
      key: 'request-no-additional-properties',
      label: 'Request body schemas must disallow extra properties',
      hint:
        "Flags request body schemas (any endpoint, method, and content type) that don't explicitly " +
        'set additionalProperties: false. Follows $ref into components.schemas.',
      jsonPath: "$.paths.*.*.requestBody.content.*.schema[?(@.type == 'object' && @.additionalProperties != false)]",
      suggestedDescription: 'Request body schemas must set additionalProperties: false',
    },
  ],
  'Response Schema Validation': [
    {
      key: 'response-no-additional-properties',
      label: 'Response body schemas must disallow extra properties',
      hint:
        "Flags response body schemas (any endpoint, method, status code, and content type) that don't " +
        'explicitly set additionalProperties: false. Follows $ref into components.schemas.',
      jsonPath: "$.paths.*.*.responses.*.content.*.schema[?(@.type == 'object' && @.additionalProperties != false)]",
      suggestedDescription: 'Response body schemas must set additionalProperties: false',
    },
  ],
  'Header Validation': [
    {
      key: 'header-requires-schema',
      label: 'Header parameters must declare a schema',
      hint: 'Flags any header parameter (in: header), on any operation or path, that has no schema.',
      jsonPath: "$..parameters[?(@.in == 'header' && !@.schema)]",
      suggestedDescription: 'Header parameters must declare a schema',
    },
    {
      key: 'header-required',
      label: 'Header parameters must be required',
      hint: 'Flags any header parameter (in: header) that is not explicitly marked required: true.',
      jsonPath: "$..parameters[?(@.in == 'header' && @.required != true)]",
      suggestedDescription: 'Header parameters must be marked required',
    },
  ],
  Other: [
    {
      key: 'no-additional-properties-anywhere',
      label: 'Object schemas anywhere must disallow extra properties',
      hint:
        "Flags any object schema anywhere in the spec (requests, responses, and components.schemas) that " +
        "doesn't explicitly set additionalProperties: false — the most thorough option.",
      jsonPath: "$..[?(@.type == 'object' && @.additionalProperties != false)]",
      suggestedDescription: 'Object schemas must set additionalProperties: false',
    },
  ],
};

const CATEGORY_SCOPE_HINT: Record<SwaggerRuleCategory, string> = {
  'Request Schema Validation':
    'Selection should end at the field you want to check on a request body schema. Endpoint, method, and media type are usually wildcarded (*) so the rule applies to every operation.',
  'Response Schema Validation':
    'Selection should end at the field you want to check on a response body schema. Endpoint, method, status code, and media type are usually wildcarded (*).',
  'Header Validation':
    "Selection should pick out header parameters (in == 'header') on an operation, or headers under a response's headers object.",
  Other: 'Selection can be any JSONPath expression.',
};

const CATEGORY_EXAMPLE_SELECTION: Record<SwaggerRuleCategory, string | null> = {
  'Request Schema Validation': "$.paths.*.*.requestBody.content.*.schema.additionalProperties",
  'Response Schema Validation': "$.paths.*.*.responses.*.content.*.schema.additionalProperties",
  'Header Validation': "$..parameters[?(@.in == 'header')].schema",
  Other: null,
};

/**
 * Request/Response Schema Validation are meant to check ONE side of the
 * contract. A document-wide selector (e.g. the "Other" category's recursive
 * "$..[?(@.type == 'object' ...)]") or one that references the wrong side's
 * key will quietly also match the other side — this is a plain substring
 * check on the literal "requestBody"/"responses" keys, not real JSONPath
 * parsing, so it can't catch every case, but it catches the common mistake:
 * copying/typing a document-wide check under a category that should be scoped.
 */
function describeScopeMismatch(category: SwaggerRuleCategory, path: string): string | null {
  const trimmed = path.trim();
  if (!trimmed) return null;

  if (category === 'Request Schema Validation') {
    if (trimmed.includes('responses')) {
      return 'This JSONPath references "responses" — it will also match response schemas, not just request bodies.';
    }
    if (!trimmed.includes('requestBody')) {
      return 'This JSONPath doesn\'t scope to requestBody — a document-wide selector will also match response and component schemas, not just request bodies.';
    }
  }

  if (category === 'Response Schema Validation') {
    if (trimmed.includes('requestBody')) {
      return 'This JSONPath references "requestBody" — it will also match request body schemas, not just responses.';
    }
    if (!trimmed.includes('responses')) {
      return 'This JSONPath doesn\'t scope to responses — a document-wide selector will also match request and component schemas, not just responses.';
    }
  }

  return null;
}

interface SwaggerConditionBuilderProps {
  category: SwaggerRuleCategory;
  jsonPath: string;
  description: string;
  selection: string | null | undefined;
  operator: string | null | undefined;
  value: string | null | undefined;
  onChange: (patch: {
    jsonPath: string;
    description?: string;
    selection: string | null;
    operator: string | null;
    value: string | null;
  }) => void;
}

export default function SwaggerConditionBuilder({
  category,
  jsonPath,
  description,
  selection,
  operator,
  value,
  onChange,
}: SwaggerConditionBuilderProps) {
  const [sampleSpecText, setSampleSpecText] = useState('');
  const [testResult, setTestResult] = useState<{ matched: boolean; count: number } | null>(null);
  const [testError, setTestError] = useState<string | null>(null);
  const [testing, setTesting] = useState(false);

  const selectedOperator = (operator as ConditionOperator) || 'NOT_EXISTS';
  const needsValue = OPERATOR_OPTIONS.find((o) => o.value === selectedOperator)?.needsValue ?? false;
  const categoryPresets = QUICK_CHECKS_BY_CATEGORY[category];
  const exampleSelection = CATEGORY_EXAMPLE_SELECTION[category];
  const scopeMismatch = describeScopeMismatch(category, jsonPath);

  function applyPatch(nextSelection: string | null, nextOperator: ConditionOperator, nextValue: string) {
    const composed = nextSelection ? composeJsonPath(nextSelection, nextOperator, nextValue) : jsonPath;
    onChange({ jsonPath: composed, selection: nextSelection, operator: nextOperator, value: nextValue });
    setTestResult(null);
  }

  function handleSelectionChange(next: string) {
    applyPatch(next || null, selectedOperator, value ?? '');
  }

  function handleOperatorChange(next: ConditionOperator) {
    applyPatch(selection ?? null, next, value ?? '');
  }

  function handleValueChange(next: string) {
    applyPatch(selection ?? null, selectedOperator, next);
  }

  function handleJsonPathEdit(next: string) {
    onChange({ jsonPath: next, selection: null, operator: null, value: null });
    setTestResult(null);
  }

  function useExampleSelection() {
    if (!exampleSelection) return;
    applyPatch(exampleSelection, selectedOperator, value ?? '');
  }

  function applyPreset(preset: QuickCheckPreset) {
    setTestResult(null);
    onChange({
      jsonPath: preset.jsonPath,
      selection: null,
      operator: null,
      value: null,
      ...(description.trim() ? {} : { description: preset.suggestedDescription }),
    });
  }

  async function handleTest() {
    if (!sampleSpecText.trim() || !jsonPath.trim()) return;
    setTesting(true);
    setTestError(null);
    try {
      const result = await testSwaggerRule({ jsonPath, sampleSpec: sampleSpecText });
      setTestResult({ matched: result.matched, count: result.violations.length });
    } catch (e) {
      setTestError((e as Error).message);
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="swagger-builder">
      {categoryPresets.length > 0 && (
        <div className="field">
          <span>Common checks for {category}</span>
          <div className="condition-row">
            {categoryPresets.map((preset) => (
              <button key={preset.key} type="button" className="btn-ghost" title={preset.hint} onClick={() => applyPreset(preset)}>
                {preset.label}
              </button>
            ))}
          </div>
        </div>
      )}

      <p className="subtitle">
        {CATEGORY_SCOPE_HINT[category]}
        {exampleSelection && (
          <>
            {' '}
            <button type="button" className="btn-ghost" onClick={useExampleSelection}>
              Use example: <code>{exampleSelection}</code>
            </button>
          </>
        )}
      </p>

      <div className="condition-row">
        <label className="field">
          <span>Selection</span>
          <textarea
            rows={2}
            value={selection ?? ''}
            onChange={(e) => handleSelectionChange(e.target.value)}
            placeholder="$.paths.*.*.requestBody.content.*.schema.additionalProperties"
          />
        </label>
      </div>

      <div className="condition-row">
        <label className="field">
          <span>Assertion</span>
          <select value={selectedOperator} onChange={(e) => handleOperatorChange(e.target.value as ConditionOperator)}>
            {OPERATOR_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>

        {needsValue && (
          <label className="field">
            <span>Value</span>
            <input value={value ?? ''} onChange={(e) => handleValueChange(e.target.value)} />
          </label>
        )}
      </div>

      <label className="field">
        <span>JSONPath (composed from Selection + Assertion — editing this directly clears them, for compound expressions)</span>
        <textarea rows={3} value={jsonPath} onChange={(e) => handleJsonPathEdit(e.target.value)} required />
      </label>
      {scopeMismatch && <p className="warning-text">⚠ {scopeMismatch}</p>}

      <label className="field">
        <span>Tester: sample spec (YAML or JSON)</span>
        <textarea
          rows={6}
          className="rules-json"
          placeholder="paste a sample OpenAPI spec here to test the JSONPath above against it"
          value={sampleSpecText}
          onChange={(e) => setSampleSpecText(e.target.value)}
        />
      </label>

      <div className="condition-row">
        <button type="button" className="btn-ghost" onClick={handleTest} disabled={testing || !sampleSpecText.trim() || !jsonPath.trim()}>
          {testing ? 'Testing…' : 'Test against sample'}
        </button>
      </div>
      {testError && <p className="error">{testError}</p>}
      {testResult && (
        <p className={testResult.matched ? 'error' : ''}>
          {testResult.matched
            ? `Matched — ${testResult.count} violation(s) would be reported.`
            : 'No match — this rule would not flag the sample.'}
        </p>
      )}
    </div>
  );
}
