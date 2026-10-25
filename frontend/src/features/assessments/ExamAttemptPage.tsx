import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, QueryState } from '../../shared/ui'
import { SaveStatus } from './SaveStatus'
import type { Attempt } from './types'
import { useAnswerAutosave } from './useAnswerAutosave'

export function ExamAttemptPage() {
  const { id = '' } = useParams()
  const attempt = useQuery({ queryKey: ['attempt', id], queryFn: () => api.get<Attempt>(`/attempts/${id}`), refetchOnWindowFocus: false })
  return (
    <QueryState query={attempt}>
      {(loaded) => (loaded.status === 'IN_PROGRESS' ? <AttemptForm key={loaded.id} attempt={loaded} fetchedAt={attempt.dataUpdatedAt} /> : <Finished attempt={loaded} />)}
    </QueryState>
  )
}

function Finished({ attempt }: { attempt: Attempt }) {
  return (
    <section className="card narrow">
      <h1>{attempt.examTitle}</h1>
      <p role="status">
        {attempt.status === 'EXPIRED'
          ? 'Time ran out; your saved answers were submitted.'
          : attempt.status === 'AWAITING_GRADING'
            ? 'Submitted. Your teacher will grade the written answers.'
            : 'Submitted.'}{' '}
        Results appear once they are published.
      </p>
      <Link to={`/student/enrollments/${attempt.enrollmentId}`}>Back to course</Link>
    </section>
  )
}

/**
 * Remaining time from the server's clock: the deadline minus the server time of the latest fetch, less the time that
 * has passed locally since that fetch. Refetches (e.g. on reconnect) re-anchor without double counting.
 */
function useRemainingSeconds(attempt: Attempt, fetchedAt: number) {
  const remainingAtFetch = (Date.parse(attempt.expiresAt) - Date.parse(attempt.serverNow)) / 1000
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return Math.max(0, Math.floor(remainingAtFetch - Math.max(0, now - fetchedAt) / 1000))
}

const ANNOUNCEMENTS = [600, 300, 60]

function AttemptForm({ attempt, fetchedAt }: { attempt: Attempt; fetchedAt: number }) {
  const queryClient = useQueryClient()
  const [closedMessage, setClosedMessage] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [submitError, setSubmitError] = useState<unknown>(null)
  const reload = useCallback(() => queryClient.invalidateQueries({ queryKey: ['attempt', attempt.id] }), [queryClient, attempt.id])
  const onClosed = useCallback(
    (code: string) => {
      setClosedMessage(code === 'ATTEMPT_EXPIRED' ? 'Time is up. Your saved answers were submitted.' : 'This attempt was submitted.')
      void reload()
    },
    [reload],
  )
  const answers = useAnswerAutosave(attempt.id, attempt.answers, onClosed)
  const remaining = useRemainingSeconds(attempt, fetchedAt)
  const announcement = ANNOUNCEMENTS.find((threshold) => remaining <= threshold && remaining > threshold - 5)

  useEffect(() => {
    if (remaining === 0) void reload()
  }, [remaining, reload])

  async function submit() {
    setSubmitError(null)
    try {
      await answers.flush()
      await api.post(`/attempts/${attempt.id}/submit`)
      await reload()
    } catch (error) {
      setSubmitError(error)
    }
  }

  const minutes = Math.floor(remaining / 60)
  const seconds = String(remaining % 60).padStart(2, '0')

  return (
    <section>
      <div className="page-header exam-header">
        <h1>{attempt.examTitle}</h1>
        <p className="timer" aria-label={`Time remaining ${minutes} minutes ${seconds} seconds`}>
          {minutes}:{seconds}
        </p>
        <p className="visually-hidden" aria-live="assertive">
          {announcement ? `${Math.round(announcement / 60)} minutes remaining` : ''}
        </p>
      </div>
      {closedMessage && (
        <p className="notice" role="alert">
          {closedMessage}
        </p>
      )}
      {attempt.instructions && <p>{attempt.instructions}</p>}
      <ol className="questions">
        {attempt.questions.map((question) => {
          const value = answers.value(question.id)
          return (
            <li key={question.id} className="card">
              <fieldset>
                <legend>
                  Question {question.position + 1} ({question.points} points)
                </legend>
                <p className="prompt">{question.prompt}</p>
                {question.type === 'WRITTEN' ? (
                  <textarea
                    aria-label={`Answer to question ${question.position + 1}`}
                    maxLength={20000}
                    value={value.writtenText ?? ''}
                    onChange={(event) => answers.change(question.id, { selectedOptionIds: [], writtenText: event.target.value }, false)}
                  />
                ) : (
                  question.options.map((option) => {
                    const multiple = question.type === 'MULTIPLE_SELECT'
                    return (
                      <label key={option.id} className="inline">
                        <input
                          type={multiple ? 'checkbox' : 'radio'}
                          name={question.id}
                          checked={value.selectedOptionIds.includes(option.id)}
                          onChange={(event) => {
                            const selected = multiple
                              ? event.target.checked
                                ? [...value.selectedOptionIds, option.id]
                                : value.selectedOptionIds.filter((other) => other !== option.id)
                              : [option.id]
                            answers.change(question.id, { selectedOptionIds: selected, writtenText: null }, true)
                          }}
                        />
                        {option.label}
                      </label>
                    )
                  })
                )}
                <SaveStatus state={answers.state(question.id)} onRetry={() => answers.retry(question.id)} />
              </fieldset>
            </li>
          )
        })}
      </ol>
      <ErrorMessage error={submitError} />
      {confirming ? (
        <div className="card" role="alertdialog" aria-labelledby="confirm-submit">
          <p id="confirm-submit">
            Submit your answers?{' '}
            {answers.unsavedCount() > 0 ? `${answers.unsavedCount()} answer(s) are not saved yet and will be saved first.` : 'All answers are saved.'} You cannot change
            them afterwards.
          </p>
          <div className="actions">
            <button type="button" className="primary" onClick={() => void submit()}>
              Submit answers
            </button>
            <button type="button" onClick={() => setConfirming(false)}>
              Keep working
            </button>
          </div>
        </div>
      ) : (
        <button type="button" className="primary" onClick={() => setConfirming(true)}>
          Finish exam
        </button>
      )}
    </section>
  )
}
