import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, QueryState, fieldErrors } from '../../shared/ui'
import type { GradeView } from './types'

export function GradeAttemptPage() {
  const { attemptId = '' } = useParams()
  const view = useQuery({ queryKey: ['grade', attemptId], queryFn: () => api.get<GradeView>(`/grading/attempts/${attemptId}`) })
  return <QueryState query={view}>{(loaded) => <Grader view={loaded} />}</QueryState>
}

function Grader({ view }: { view: GradeView }) {
  const queryClient = useQueryClient()
  const { attempt, revision } = view
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['grade', attempt.attemptId] })
  const action = useMutation({
    mutationFn: ({ path, body }: { path: string; body: unknown }) => api.post(`/grading/attempts/${attempt.attemptId}/${path}`, body),
    onSuccess: refresh,
  })
  const [reason, setReason] = useState('')
  const editable = revision.status === 'DRAFT' || revision.status === 'FINALIZED'
  const grade = (questionId: string) => view.grades.find((row) => row.questionId === questionId)

  return (
    <section>
      <div className="page-header">
        <h1>
          {attempt.examTitle} · attempt {attempt.attemptNumber}
        </h1>
        <Link to="/teacher/grading">Back to grading</Link>
      </div>
      <p role="status">
        Revision {revision.number}: <strong>{revision.status.toLowerCase()}</strong>
        {revision.earned !== null && ` · ${revision.earned} / ${revision.maximum}`}
        {revision.reason && ` · regrade reason: ${revision.reason}`}
      </p>
      <ol className="questions">
        {attempt.questions.map((question) => (
          <li key={question.questionId} className="card">
            <h2>
              Question {question.position + 1} ({question.points} points)
            </h2>
            <p className="prompt">{question.prompt}</p>
            {question.type === 'WRITTEN' ? (
              <>
                <blockquote className="prompt">{question.writtenText || <em>No answer</em>}</blockquote>
                {question.gradingNotes && <p className="muted">Grading notes: {question.gradingNotes}</p>}
              </>
            ) : (
              <ul>
                {question.options.map((option) => (
                  <li key={option.id}>
                    {question.selectedOptionIds.includes(option.id) ? '☑' : '☐'} {option.label} {option.correct && <strong>(correct)</strong>}
                  </li>
                ))}
              </ul>
            )}
            <GradeForm
              key={`${revision.id}-${revision.version}`}
              attemptId={attempt.attemptId}
              questionId={question.questionId}
              written={question.type === 'WRITTEN'}
              maxPoints={question.points}
              revision={revision.version}
              points={grade(question.questionId)?.points ?? null}
              feedback={grade(question.questionId)?.feedback ?? ''}
              editable={editable}
              onSaved={refresh}
            />
          </li>
        ))}
      </ol>
      <ErrorMessage error={action.error} />
      <div className="actions sticky-actions">
        {revision.status === 'DRAFT' && (
          <button type="button" className="primary" onClick={() => action.mutate({ path: 'finalize', body: { expectedRevision: revision.version } })}>
            Finalize grades
          </button>
        )}
        {revision.status === 'FINALIZED' && (
          <button type="button" className="primary" onClick={() => action.mutate({ path: 'publish', body: { expectedRevision: revision.version } })}>
            Publish result
          </button>
        )}
        {revision.status === 'PUBLISHED' && (
          <>
            <label className="field">
              Regrade reason
              <input value={reason} maxLength={1000} onChange={(event) => setReason(event.target.value)} />
            </label>
            <button type="button" onClick={() => action.mutate({ path: 'regrade', body: { reason } })}>
              Start regrade
            </button>
          </>
        )}
      </div>
    </section>
  )
}

type GradeFormProps = {
  attemptId: string
  questionId: string
  written: boolean
  maxPoints: number
  revision: number
  points: number | null
  feedback: string
  editable: boolean
  onSaved: () => void
}

function GradeForm({ attemptId, questionId, written, maxPoints, revision, points, feedback, editable, onSaved }: GradeFormProps) {
  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.put(`/grading/attempts/${attemptId}/answers/${questionId}`, { ...body, expectedRevision: revision }),
    onSuccess: onSaved,
  })
  const errors = fieldErrors(save.error)
  return (
    <form
      className="inline-form"
      aria-label={`Grade for question ${questionId}`}
      onSubmit={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        save.mutate({ points: written ? form.get('points') : null, feedback: form.get('feedback') })
      }}
    >
      <div className="field">
        <label htmlFor={`${questionId}-points`}>Points (of {maxPoints})</label>
        <input id={`${questionId}-points`} name="points" inputMode="decimal" defaultValue={points ?? ''} disabled={!editable || !written} />
        {points === null && <small className="error">Not graded yet</small>}
        {errors.points && <small className="error">{errors.points}</small>}
      </div>
      <div className="field">
        <label htmlFor={`${questionId}-feedback`}>Feedback</label>
        <textarea id={`${questionId}-feedback`} name="feedback" maxLength={5000} defaultValue={feedback} disabled={!editable} />
      </div>
      {editable && (
        <button type="submit" disabled={save.isPending}>
          Save grade
        </button>
      )}
      <ErrorMessage error={save.error} />
    </form>
  )
}
