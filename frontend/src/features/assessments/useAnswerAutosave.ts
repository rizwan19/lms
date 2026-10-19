import { useEffect, useState, useSyncExternalStore } from 'react'
import { api, ApiError } from '../../shared/api'
import { AnswerStore, type SaveFailure } from './answerStore'
import type { SavedAnswer } from './types'

function classify(error: unknown): SaveFailure {
  if (!(error instanceof ApiError)) return {}
  return { code: error.code, current: (error.body as { current?: SavedAnswer | null } | undefined)?.current }
}

/** Binds an attempt's {@link AnswerStore} to React and warns before leaving with unsaved answers. */
export function useAnswerAutosave(attemptId: string, saved: SavedAnswer[], onClosed: (code: string) => void) {
  const [store] = useState(
    () => new AnswerStore(saved, (questionId, body) => api.put(`/attempts/${attemptId}/answers/${questionId}`, body), classify, onClosed),
  )
  useSyncExternalStore(store.subscribe, store.snapshot)

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (store.unsavedCount() > 0) event.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [store])

  return store
}
