import { useState } from 'react';
import { Link } from 'react-router-dom';
import { generateRuleSuggestion } from '../api/client';
import type { RuleCategory } from '../api/client';
import { useAssistAvailability } from '../hooks/useAssistAvailability';

export default function AskAiPage() {
  const assistAvailable = useAssistAvailability();
  const [category, setCategory] = useState<RuleCategory>('xml');
  const [instruction, setInstruction] = useState('');
  const [suggestion, setSuggestion] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleAsk() {
    if (!instruction.trim()) return;
    setAsking(true);
    setError(null);
    try {
      setSuggestion(await generateRuleSuggestion(category, instruction.trim()));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setAsking(false);
    }
  }

  if (assistAvailable === false) {
    return (
      <div className="page page-narrow">
        <h1>Ask AI</h1>
        <div className="card">
          <p>
            AI assist isn't configured on this server — no model is set up. Ask whoever runs this
            server to set <code>governanceplus.model.path</code> to a local <code>.gguf</code> model
            (or <code>governanceplus.model.mock=true</code> for a canned response). Everything else
            — Review, Rules, and Test Sample — works independently of this.
          </p>
        </div>
      </div>
    );
  }

  if (assistAvailable === null) {
    return (
      <div className="page page-narrow">
        <h1>Ask AI</h1>
        <p className="subtitle">Checking availability…</p>
      </div>
    );
  }

  return (
    <div className="page page-narrow">
      <h1>Ask AI</h1>
      <p className="subtitle">
        Describe what you want a rule to check and get a drafting suggestion — free text to read
        and adapt, not saved automatically. The AI never decides pass/fail; only the rules saved in{' '}
        <Link to="/rules">Rules</Link> do.
      </p>

      <div className="filter-chips">
        <button
          type="button"
          className={`chip ${category === 'xml' ? 'chip-active' : ''}`}
          onClick={() => setCategory('xml')}
        >
          XML / Mule
        </button>
        <button
          type="button"
          className={`chip ${category === 'pom' ? 'chip-active' : ''}`}
          onClick={() => setCategory('pom')}
        >
          POM
        </button>
        <button
          type="button"
          className={`chip ${category === 'swagger' ? 'chip-active' : ''}`}
          onClick={() => setCategory('swagger')}
        >
          Swagger
        </button>
      </div>

      <div className="card">
        <label className="field">
          <span>Instruction</span>
          <textarea
            rows={3}
            placeholder="e.g. flag any HTTP listener that doesn't require HTTPS"
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
          />
        </label>
        <div>
          <button type="button" onClick={handleAsk} disabled={asking || !instruction.trim()}>
            {asking ? 'Thinking…' : 'Ask AI'}
          </button>
        </div>
        {asking && (
          <p className="subtitle">
            Generating on CPU can take a while (up to a minute or so) depending on server
            hardware — this isn't stuck.
          </p>
        )}
        {error && <p className="error">{error}</p>}
        {suggestion && (
          <div className="ai-suggestion">
            <pre>{suggestion}</pre>
          </div>
        )}
      </div>
    </div>
  );
}
