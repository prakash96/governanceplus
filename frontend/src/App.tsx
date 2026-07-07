import { Routes, Route, Link } from 'react-router-dom';
import NewReviewPage from './pages/NewReviewPage';
import ReviewResultPage from './pages/ReviewResultPage';
import RulesPage from './pages/RulesPage';

export default function App() {
  return (
    <div className="app">
      <header className="app-header">
        <Link to="/" className="app-title">
          GovernancePlus — Rules-Based Governance for Mule, POM &amp; Swagger
        </Link>
        <nav className="app-nav no-print">
          <Link to="/">Review</Link>
          <Link to="/rules">Rules</Link>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<NewReviewPage />} />
          <Route path="/reviews/:jobId" element={<ReviewResultPage />} />
          <Route path="/rules" element={<RulesPage />} />
        </Routes>
      </main>
    </div>
  );
}
