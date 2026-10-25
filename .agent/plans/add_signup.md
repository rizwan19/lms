# Signup Page Implementation Plan

## 1. Summary and confirmed decisions

Add a public `/signup` page where visitors enter username, display name, email, password, password confirmation, and choose Student or Teacher.

Confirmed behavior:

- The login page links to signup.
- Neither role is selected initially.
- Password confirmation is required but never sent to the backend.
- Successful signup redirects to `/login` with an account-created and email-verification notice.
- Signup does not automatically sign the user in.
- Use the existing auth-page styling and shared UI components.
- Add no tests and modify no existing tests.

The change requires three production files:

| File | Change |
|---|---|
| `frontend/src/features/auth/SignupPage.tsx` | Add signup form, validation, API mutation, and navigation |
| `frontend/src/features/auth/LoginPage.tsx` | Add signup link and registration-success notice |
| `frontend/src/app/routes.tsx` | Register the public `/signup` route |

No new dependencies, backend changes, database migrations, or API contract changes are required.

## 2. Repository findings and API contract

The existing frontend provides everything needed for integration:

- `api.post()` in `shared/api.ts` already uses `/api/v1`, session cookies, and CSRF protection.
- `Role` already represents `'TEACHER' | 'STUDENT'`.
- `RegistrationReceipt` already represents `{ userId: string; nextAction: 'VERIFY_EMAIL' }`.
- `Field`, `ErrorMessage`, `Spinner`, and `fieldErrors` already exist.
- Auth pages use TanStack Query mutations, native form controls, and the `card narrow` layout.
- Login already redirects authenticated users to `/` and first-time installations to `/setup`.

`RegistrationController` accepts:

```http
POST /api/v1/auth/register
Content-Type: application/json
X-XSRF-TOKEN: <handled by the existing API client>
```

```json
{
  "username": "rahim.student",
  "displayName": "রহিম উদ্দিন",
  "email": "rahim@example.com",
  "password": "a sufficiently long password",
  "role": "STUDENT"
}
```

Successful response:

```http
201 Created
```

```json
{
  "userId": "account-uuid",
  "nextAction": "VERIFY_EMAIL"
}
```

The backend creates an unverified account and queues a verification email. Teacher accounts start pending approval and enter the review queue after email verification.

Public signup requires an active workspace and configured email delivery. The backend remains authoritative for these conditions and account uniqueness.

## 3. Implementation

### Task 1: Add `SignupPage`

#### Page layout

Use the existing page structure:

```tsx
<section className="card narrow">
  <h1>Create account</h1>
  {/* Signup form */}
  <p>
    Already have an account? <Link to="/login">Sign in</Link>
  </p>
</section>
```

Display inputs in this order:

1. Username
2. Display name
3. Email
4. Password
5. Confirm password
6. Account role

Use the following attributes and validation rules:

| Field | Input behavior | Validation |
|---|---|---|
| Username | `autoComplete="username"` | Trimmed value; 3–64 ASCII letters, digits, dots, underscores, or hyphens |
| Display name | `autoComplete="name"` | Trimmed, nonempty; at most 150 Unicode code points |
| Email | `type="email"`, `autoComplete="email"` | Required; trimmed, at most 254 characters; match backend email format |
| Password | `type="password"`, `autoComplete="new-password"` | 12–128 characters; preserve whitespace |
| Confirm password | `type="password"`, `autoComplete="new-password"` | Required; must exactly match password |
| Role | Required radio group | Exactly `STUDENT` or `TEACHER`; no default |

Use a semantic radio group:

```tsx
<fieldset
  aria-describedby={errors.role ? 'role-hint role-error' : 'role-hint'}
  aria-invalid={Boolean(errors.role)}
>
  <legend>Account role</legend>

  <label className="inline">
    <input type="radio" name="role" value="STUDENT" required />
    Student
  </label>

  <label className="inline">
    <input type="radio" name="role" value="TEACHER" required />
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
```

Reuse existing CSS for inputs, fieldsets, inline labels, notices, and buttons. No stylesheet changes are planned.

#### Validation

Keep the request type and validation helper local to the signup page:

```ts
import { ApiError, type FieldErrors } from '../../shared/api'
import type { Role } from '../../shared/types'

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

function validateSignup(values: SignupForm): FieldErrors {
  const errors: FieldErrors = {}

  if (!/^[A-Za-z0-9._-]{3,64}$/.test(values.username.trim())) {
    errors.username =
      'Use 3–64 letters, digits, dots, underscores or hyphens'
  }

  const displayName = values.displayName.trim()
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

  if (error.code === 'USERNAME_TAKEN') {
    errors.username = error.message
  }
  if (error.code === 'EMAIL_TAKEN') {
    errors.email = error.message
  }

  return errors
}
```

Use `Array.from(displayName).length` because the backend counts Unicode code points. A native `maxLength={150}` would count UTF-16 units and could reject otherwise valid names.

Use `<form noValidate>` and run this validation on submission, giving consistent inline messages. Retain semantic attributes such as `required`, input types, and password length limits.

Normalization:

- Trim username, display name, and email before submission.
- Leave username/email case normalization to the backend.
- Never trim or transform either password field.

#### Mutation and success navigation

Use TanStack Query directly in the page, following existing auth patterns:

```tsx
const navigate = useNavigate()

const register = useMutation({
  mutationFn: (body: RegistrationRequest) =>
    api.post<RegistrationReceipt>('/auth/register', body),

  onSuccess: () => {
    navigate('/login', {
      replace: true,
      state: { registrationComplete: true },
    })
  },
})
```

Do not call `/auth/login`, set the `['me']` query, or treat the receipt as a profile.

Submission should follow this shape:

```tsx
function handleSubmit(event: FormEvent<HTMLFormElement>) {
  event.preventDefault()
  if (register.isPending) return

  const form = event.currentTarget
  const data = new FormData(form)

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
  setErrors(nextErrors)

  if (Object.keys(nextErrors).length > 0) {
    const firstInvalid = form.elements.namedItem(
      Object.keys(nextErrors)[0],
    )

    if (firstInvalid instanceof HTMLElement) {
      firstInvalid.focus()
    } else {
      // A radio group is returned as a RadioNodeList.
      form.querySelector<HTMLInputElement>('input[name="role"]')?.focus()
    }
    return
  }

  const role = values.role
  if (role !== 'STUDENT' && role !== 'TEACHER') return

  register.mutate(
    {
      username: values.username.trim(),
      displayName: values.displayName.trim(),
      email: values.email.trim(),
      password: values.password,
      role,
    },
    {
      onError: (error) => {
        setErrors(registrationFieldErrors(error))
      },
    },
  )
}
```

Import `FormEvent` as a type from React. Use `useState<FieldErrors>({})` for the page’s inline errors.

For server errors mapped to fields, focus the first invalid field after rendering the errors. Keep this behavior local to the signup page.

#### Error and pending behavior

- Render inline errors through `Field`.
- Render `<ErrorMessage error={register.error} />` for the server’s overall explanation.
- Render an accessible validation summary when client validation fails.
- Connect each invalid control to its existing `${name}-error` element with `aria-describedby`; set `aria-invalid`.
- Clear an edited field’s stale inline error when its value changes. Changing the password should also clear a stale confirmation error.
- Clear the previous mutation error before a new submission.
- Disable form controls and submission while registration is pending.
- Change button text from `Create account` to `Creating account…`.
- Preserve entered values after unsuccessful requests.
- Never automatically retry registration.

Expected error presentation:

| Backend error | UI behavior |
|---|---|
| `VALIDATION_FAILED` | Show returned field errors and overall message |
| `USERNAME_TAKEN` | Show username error and overall message |
| `EMAIL_TAKEN` | Show email error and overall message |
| `REGISTRATION_CLOSED` | Show backend explanation; remain on signup |
| `EMAIL_NOT_CONFIGURED` | Show backend explanation; remain on signup |
| `RATE_LIMITED` | Show backend explanation; allow a later manual retry |
| `DUPLICATE` | Show generic conflict explanation; do not guess which field conflicted |
| Network or unexpected error | Use existing `ErrorMessage` fallback |

Do not add username/email availability requests.

#### Route guards and setup behavior

Reuse the login page’s setup-query pattern:

```tsx
const { me } = useSession()
const setup = useQuery({
  queryKey: ['setup'],
  queryFn: () => api.get<boolean>('/setup'),
})

if (me) return <Navigate to="/" replace />
if (setup.isPending) return <Spinner />
if (setup.error) return <ErrorMessage error={setup.error} />
if (setup.data) return <Navigate to="/setup" replace />
```

The signup form renders only after setup status loads successfully.

`GET /setup` currently returns a boolean indicating whether initial setup is required. It does not expose registration availability or SMTP configuration. Continue to handle `REGISTRATION_CLOSED` and `EMAIL_NOT_CONFIGURED` from the registration request rather than inventing an availability API.

### Task 2: Register the public route

Add the import and route in `frontend/src/app/routes.tsx`:

```tsx
import { SignupPage } from '../features/auth/SignupPage'
```

Place signup alongside login:

```tsx
{ path: '/login', element: <LoginPage /> },
{ path: '/signup', element: <SignupPage /> },
```

Keep it under `AppShell`, outside `RequireAuth`.

Direct navigation and browser refresh at `/signup` must work through the existing SPA routing setup.

### Task 3: Update the login page

Add `useLocation` to the existing router imports and inspect a narrowly typed navigation marker:

```tsx
type LoginLocationState = {
  registrationComplete?: boolean
}

const location = useLocation()
const registrationComplete =
  (location.state as LoginLocationState | null)?.registrationComplete === true
```

Show this notice between the heading and login form:

```tsx
{registrationComplete && (
  <p className="notice" role="status">
    Account created. Check your email for a verification link.
    You can sign in to resend the verification email if needed.
  </p>
)}
```

Add a signup link alongside the existing recovery link:

```tsx
<p>
  Don’t have an account? <Link to="/signup">Create account</Link>
</p>
```

Keep existing login, CSRF refresh, session caching, setup redirection, and forgot-password behavior.

The success marker is informational router state only:

- Do not put passwords, tokens, account IDs, or form values into navigation state.
- Do not put registration details into URL parameters or browser storage.
- A direct visit to `/login` has no signup notice.
- The notice can remain for the current login history entry; it requires no persistence mechanism.

## 4. Verification and acceptance

**Do not add or modify tests.**

During implementation, install the locked frontend dependencies if needed, then run existing checks:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

The attempted planning-time typecheck and existing auth/accessibility tests could not run because `tsc` and `vitest` are not installed. Their status is unverified.

Manually verify:

1. `/login` → `Create account` opens `/signup`; the signup sign-in link returns to login.
2. All six inputs appear with accessible labels; neither role is selected.
3. Empty fields, invalid username/email, whitespace-only display name, password-length violations, mismatched passwords, and missing role block the API request.
4. A Unicode display name is accepted within the backend’s 150-code-point limit.
5. Student and Teacher submissions send exactly the five backend fields, with the selected uppercase role.
6. Password whitespace is preserved and confirmation is excluded from the request.
7. Pending submission disables the form and prevents repeat submission.
8. A real `201` response redirects to `/login` and shows the verification notice without signing in.
9. Duplicate username/email and registration-availability errors display clearly and preserve form values.
10. A signed-in visitor opening `/signup` is redirected through the existing authenticated flow.
11. An installation requiring setup redirects `/signup` to `/setup`.
12. Keyboard navigation, role selection, error focus, and a narrow mobile viewport remain usable.

Use an active workspace with configured email delivery for the real signup check. Existing Playwright account-flow and port inconsistencies are outside this change.

## 5. Boundaries and defaults

- Public URL: `/signup`; backend URL remains `/api/v1/auth/register`.
- Keep request and validation helpers local to `SignupPage`; reuse existing shared response and role types.
- Keep the current email-verification and teacher-approval workflows.
- Add no resend functionality to signup; users can sign in to access the existing verification screen.
- Add no password-strength meter, availability checks, generic form abstraction, or visual redesign.
- Make no changes to backend contracts, shared API transport, deployment configuration, or existing tests.
