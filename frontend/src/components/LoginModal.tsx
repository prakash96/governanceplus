import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from './Modal';
import { useAuth } from '../hooks/useAuth';

export default function LoginModal() {
  const { isLoginOpen, closeLogin, login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!isLoginOpen) {
    return null;
  }

  function handleClose() {
    setUsername('');
    setPassword('');
    setError(null);
    closeLogin();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
      setUsername('');
      setPassword('');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="Admin login" onClose={handleClose}>
      <form className="rule-form" onSubmit={handleSubmit}>
        <p className="subtitle">
          Everyone can browse rules and run reviews as a guest. Logging in as admin additionally
          lets you add, edit, and delete rules.
        </p>
        <label className="field">
          <span>Username</span>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
            autoComplete="username"
            required
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error && <p className="error">{error}</p>}

        <div className="rule-form-actions">
          <button type="submit" disabled={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
          <button type="button" className="btn-secondary" onClick={handleClose}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
