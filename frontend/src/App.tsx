import { Routes, Route, NavLink, Link } from 'react-router-dom';
import NewReviewPage from './pages/NewReviewPage';
import RulesPage from './pages/RulesPage';
import TestSamplePage from './pages/TestSamplePage';
import AskAiPage from './pages/AskAiPage';
import { useAssistAvailability } from './hooks/useAssistAvailability';
import { AuthProvider, useAuth } from './hooks/useAuth';
import LoginModal from './components/LoginModal';

function AdminStatus() {
  const { isAdmin, openLogin, logout } = useAuth();

  if (isAdmin) {
    return (
      <div className="admin-status">
        <span className="badge badge-admin">Admin</span>
        <button type="button" className="btn-ghost" onClick={logout}>
          Log out
        </button>
      </div>
    );
  }

  return (
    <button type="button" className="btn-ghost" onClick={openLogin}>
      Admin login
    </button>
  );
}

function AppShell() {
  const assistAvailable = useAssistAvailability();

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark">G+</span>
            <span className="brand-text">
              <span className="brand-name">GovernancePlus</span>
              <span className="brand-tagline">Rules-based governance for Mule, POM &amp; Swagger</span>
            </span>
          </Link>
          <nav className="app-nav no-print">
            <NavLink to="/" end className={({ isActive }) => (isActive ? 'active-nav-link' : '')}>
              Review
            </NavLink>
            <NavLink to="/rules" className={({ isActive }) => (isActive ? 'active-nav-link' : '')}>
              Rules
            </NavLink>
            <NavLink to="/test" className={({ isActive }) => (isActive ? 'active-nav-link' : '')}>
              Test Sample
            </NavLink>
            {assistAvailable && (
              <NavLink to="/ask-ai" className={({ isActive }) => (isActive ? 'active-nav-link' : '')}>
                Ask AI
              </NavLink>
            )}
          </nav>
          <div className="no-print">
            <AdminStatus />
          </div>
        </div>
      </header>
      <main>
        <div className="app-main">
          <Routes>
            <Route path="/" element={<NewReviewPage />} />
            <Route path="/rules" element={<RulesPage />} />
            <Route path="/test" element={<TestSamplePage />} />
            <Route path="/ask-ai" element={<AskAiPage />} />
          </Routes>
        </div>
      </main>
      <LoginModal />
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}
