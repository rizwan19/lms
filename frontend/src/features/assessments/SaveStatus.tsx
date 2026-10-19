import type { SaveState } from './answerStore'

const LABELS: Record<SaveState, string> = {
  saved: 'Saved',
  unsaved: 'Not saved yet',
  saving: 'Saving…',
  error: 'Not saved — check your connection',
  conflict: 'Updated from another tab',
}

export function SaveStatus({ state, onRetry }: { state: SaveState; onRetry: () => void }) {
  return (
    <span className={`save-status ${state}`}>
      {LABELS[state]}
      {state === 'error' && (
        <button type="button" onClick={onRetry}>
          Retry
        </button>
      )}
    </span>
  )
}
