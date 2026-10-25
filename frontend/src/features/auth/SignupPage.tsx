import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, api, type FieldErrors } from '../../shared/api'
import type { RegistrationReceipt, Role } from '../../shared/types'
import { ErrorMessage, Field, Spinner } from '../../shared/ui'
import { useSession } from '../../app/session'

type RegistrationRequest = {
  username: string
  displayName: string
  email: string
  password: string
  role: Role
}

type SignupForm = Omit<RegistrationRequest, 'role'> & {
  confirmPassword: string
  role: string
}

const FIELD_ORDER = ['username', 'displayName', 'email', 'password', 'confirmPassword', 'role']

function validateSignup(values: SignupForm): FieldErrors {
  const errors: FieldErrors = {}
  if (!/^[A-Za-z0-9._-]{3,64}$/.test(values.username.trim())) {
    errors.username = 'Use 3–64 letters, digits, dots, underscores or hyphens'
  }
  const displayName = values.displayName.trim()
  // The backend counts code points, so a UTF-16 maxLength would reject valid names.
  if (!displayName || Array.from(displayName).length > 150) {
    errors.displayName = 'Display name must be 1–150 characters'
  }
  const email = values.email.trim()
  if (email.length > 254 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    errors.email = 'Enter a valid email address'
  }
  if (values.password.length < 12 || values.password.length > 128) {
    errors.password = 'Password must be 12–128 characters'
  }
  if (!values.confirmPassword) {
    errors.confirmPassword = 'Confirm your password'
  } else if (values.confirmPassword !== values.password) {
    errors.confirmPassword = 'Passwords do not match'
  }
  if (values.role !== 'STUDENT' && values.role !== 'TEACHER') {
    errors.role = 'Choose Student or Teacher'
  }
  return errors
}

function registrationFieldErrors(error: unknown): FieldErrors {
  if (!(error instanceof ApiError)) return {}
  const errors = { ...error.fieldErrors }
  if (error.code === 'USERNAME_TAKEN') errors.username = error.message
  if (error.code === 'EMAIL_TAKEN') errors.email = error.message
  return errors
}

export function SignupPage() {
  const { me } = useSession()
  const navigate = useNavigate()
  const formRef = useRef<HTMLFormElement>(null)
  const focusFirstError = useRef(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const setup = useQuery({ queryKey: ['setup'], queryFn: () => api.get<boolean>('/setup') })
  const register = useMutation({
    mutationFn: (body: RegistrationRequest) => api.post<RegistrationReceipt>('/auth/register', body),
    onSuccess: () => navigate('/login', { replace: true, state: { registrationComplete: true } }),
  })
  const pending = register.isPending

  useEffect(() => {
    const form = formRef.current
    if (!focusFirstError.current || pending || !form) return
    focusFirstError.current = false
    const first = FIELD_ORDER.find((name) => errors[name])
    if (!first) return
    const element = form.elements.namedItem(first)
    if (element instanceof HTMLElement) element.focus()
    // A radio group is returned as a RadioNodeList.
    else form.querySelector<HTMLInputElement>(`input[name="${first}"]`)?.focus()
  }, [errors, pending])

  if (me) return <Navigate to="/" replace />
  if (setup.isPending) return <Spinner />
  if (setup.error) return <ErrorMessage error={setup.error} />
  if (setup.data) return <Navigate to="/setup" replace />

  function showErrors(next: FieldErrors) {
    focusFirstError.current = true
    setErrors(next)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const data = new FormData(event.currentTarget)
    const values: SignupForm = {
      username: String(data.get('username') ?? ''),
      displayName: String(data.get('displayName') ?? ''),
      email: String(data.get('email') ?? ''),
      password: String(data.get('password') ?? ''),
      confirmPassword: String(data.get('confirmPassword') ?? ''),
      role: String(data.get('role') ?? ''),
    }
    register.reset()
    const nextErrors = validateSignup(values)
    showErrors(nextErrors)
    const role = values.role
    if (Object.keys(nextErrors).length > 0 || (role !== 'STUDENT' && role !== 'TEACHER')) return
    register.mutate(
      {
        username: values.username.trim(),
        displayName: values.displayName.trim(),
        email: values.email.trim(),
        password: values.password,
        role,
      },
      { onError: (error) => showErrors(registrationFieldErrors(error)) },
    )
  }

  function clearError(name: string) {
    const stale = name === 'password' ? [name, 'confirmPassword'] : [name]
    if (!stale.some((key) => errors[key])) return
    setErrors((current) => Object.fromEntries(Object.entries(current).filter(([key]) => !stale.includes(key))))
  }

  const invalid = (name: string) => ({
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': errors[name] ? `${name}-error` : undefined,
  })
  const clientInvalid = !register.error && Object.keys(errors).length > 0

  return (
    <section className="card narrow">
      <h1>Create account</h1>
      <form
        ref={formRef}
        noValidate
        onSubmit={handleSubmit}
        onChange={(event) => {
          if (event.target instanceof HTMLInputElement) clearError(event.target.name)
        }}
      >
        <Field label="Username" name="username" errors={errors}>
          <input id="username" name="username" autoComplete="username" required disabled={pending} {...invalid('username')} />
        </Field>
        <Field label="Display name" name="displayName" errors={errors}>
          <input id="displayName" name="displayName" autoComplete="name" required disabled={pending} {...invalid('displayName')} />
        </Field>
        <Field label="Email" name="email" errors={errors}>
          <input id="email" name="email" type="email" autoComplete="email" required maxLength={254} disabled={pending} {...invalid('email')} />
        </Field>
        <Field label="Password" name="password" errors={errors} hint="12 to 128 characters">
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
            disabled={pending}
            {...invalid('password')}
          />
        </Field>
        <Field label="Confirm password" name="confirmPassword" errors={errors}>
          <input
            id="confirmPassword"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            required
            disabled={pending}
            {...invalid('confirmPassword')}
          />
        </Field>
        <fieldset aria-describedby={errors.role ? 'role-hint role-error' : 'role-hint'} aria-invalid={Boolean(errors.role)}>
          <legend>Account role</legend>
          <label className="inline">
            <input type="radio" name="role" value="STUDENT" required disabled={pending} />
            Student
          </label>
          <label className="inline">
            <input type="radio" name="role" value="TEACHER" required disabled={pending} />
            Teacher
          </label>
          <small id="role-hint" className="muted">
            Teachers must verify their email and receive approval before publishing courses.
          </small>
          {errors.role && (
            <small id="role-error" className="error">
              {errors.role}
            </small>
          )}
        </fieldset>
        {clientInvalid && (
          <p className="error" role="alert">
            Fix the highlighted fields and try again.
          </p>
        )}
        <ErrorMessage error={register.error} />
        <button type="submit" className="primary" disabled={pending}>
          {pending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p>
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}
