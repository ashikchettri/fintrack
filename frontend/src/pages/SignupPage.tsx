import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api/client';

// Client-side rules mirror the backend Bean Validation (SignupRequest):
// server remains the authority, this is just faster feedback.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_MIN = 12;
const PASSWORD_MAX = 128;

export default function SignupPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!EMAIL_PATTERN.test(email)) errors.email = 'Enter a valid email address';
    if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
      errors.password = `Password must be between ${PASSWORD_MIN} and ${PASSWORD_MAX} characters`;
    }
    return errors;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);

    const clientErrors = validate();
    setFieldErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setSubmitting(true);
    try {
      await api.signup(email, password);
      navigate('/login', { state: { signedUpEmail: email } });
    } catch (error) {
      if (error instanceof ApiError) {
        // 400 carries our field→message extension; 409 = email taken
        if (error.problem.errors) setFieldErrors(error.problem.errors);
        else setFormError(error.problem.detail ?? 'Signup failed');
      } else {
        setFormError('Network error — is the API running?');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="card">
      <h1>Create your account</h1>
      <p className="muted">A household is created for you automatically.</p>
      <form onSubmit={onSubmit} noValidate>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
        />
        {fieldErrors.email && <p className="field-error" role="alert">{fieldErrors.email}</p>}

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
        />
        {fieldErrors.password && <p className="field-error" role="alert">{fieldErrors.password}</p>}

        {formError && <p className="form-error" role="alert">{formError}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating…' : 'Sign up'}
        </button>
      </form>
      <p className="muted">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </main>
  );
}
