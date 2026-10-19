import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { api, ApiError } from '../../shared/api'
import type { LessonProgress, Player } from './types'

type Save = { lessonId: string; completed: boolean; resumeSeconds: number }

/**
 * Serializes progress writes: one request at a time, each carrying the last acknowledged revision. A revision
 * conflict (another tab) reloads the player instead of overwriting newer progress.
 */
export function useProgressSaver(enrollmentId: string) {
  const queryClient = useQueryClient()
  const queue = useRef(Promise.resolve())
  const key = ['player', enrollmentId]

  const current = (lessonId: string): LessonProgress | undefined =>
    queryClient.getQueryData<Player>(key)?.progress.find((record) => record.lessonId === lessonId)

  const mutation = useMutation({
    mutationFn: (save: Save) => {
      const run = queue.current.then(() =>
        api.put<LessonProgress>(`/enrollments/${enrollmentId}/lessons/${save.lessonId}/progress`, {
          expectedRevision: current(save.lessonId)?.revision ?? 0,
          completed: save.completed,
          resumeSeconds: save.resumeSeconds,
        }),
      )
      queue.current = run.then(
        () => undefined,
        () => undefined,
      )
      return run
    },
    onSuccess: (saved) => {
      queryClient.setQueryData<Player>(key, (player) =>
        player && { ...player, progress: [...player.progress.filter((record) => record.lessonId !== saved.lessonId), saved] },
      )
      queryClient.invalidateQueries({ queryKey: ['me', 'learning'] })
      queryClient.invalidateQueries({ queryKey: ['completion', enrollmentId] })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'PROGRESS_REVISION_CONFLICT') queryClient.invalidateQueries({ queryKey: key })
    },
  })

  return {
    save: (lessonId: string, change: Partial<Omit<Save, 'lessonId'>>) => {
      const record = current(lessonId)
      mutation.mutate({ lessonId, completed: change.completed ?? record?.completed ?? false, resumeSeconds: change.resumeSeconds ?? record?.resumeSeconds ?? 0 })
    },
    error: mutation.error,
    pending: mutation.isPending,
  }
}
