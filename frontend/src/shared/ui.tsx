import type { ReactNode } from 'react'
import { ApiError, type FieldErrors } from './api'

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="muted" role="status" aria-live="polite">
      {label}
    </p>
  )
}

export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Try again.'
  return (
    <p className="error" role="alert">
      {message}
    </p>
  )
}

/** Renders loading, error, empty and content states for one query. */
export function QueryState<T>({
  query,
  isEmpty,
  empty,
  children,
}: {
  query: { isPending: boolean; error: unknown; data: T | undefined }
  isEmpty?: (data: T) => boolean
  empty?: ReactNode
  children: (data: T) => ReactNode
}) {
  if (query.isPending) return <Spinner />
  if (query.error) return <ErrorMessage error={query.error} />
  const data = query.data as T
  if (isEmpty?.(data)) return <p className="empty">{empty ?? 'Nothing here yet.'}</p>
  return <>{children(data)}</>
}

export function Field({
  label,
  name,
  errors,
  hint,
  children,
}: {
  label: string
  name: string
  errors?: FieldErrors
  hint?: string
  children: ReactNode
}) {
  const error = errors?.[name]
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      {children}
      {hint && <small className="muted">{hint}</small>}
      {error && (
        <small className="error" id={`${name}-error`}>
          {error}
        </small>
      )}
    </div>
  )
}

export function fieldErrors(error: unknown): FieldErrors {
  return error instanceof ApiError ? error.fieldErrors : {}
}

export function Pagination({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size))
  if (pages <= 1) return null
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {pages}
      </span>
      <button type="button" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
        Next
      </button>
    </nav>
  )
}

export function formatDateTime(instant: string | null | undefined, timeZone?: string): string {
  if (!instant) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(new Date(instant))
}
