import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../shared/api'
import { QueryState, formatDateTime } from '../../shared/ui'
import { useWorkspaceTimezone } from '../../shared/useWorkspace'

type ResultSummary = {
  attemptId: string
  examTitle: string
  required: boolean
  attemptNumber: number
  finalizedAt: string
  state: 'PENDING' | 'PUBLISHED'
  earned: number | null
  maximum: number | null
  passed: boolean | null
  publishedAt: string | null
}

type ResultDetail = {
  answersReleased: boolean
  questions: {
    position: number
    prompt: string
    maxPoints: number
    points: number
    feedback: string
    yourChoices: string[]
    yourText: string | null
    correctAnswers: string[] | null
    explanation: string | null
  }[]
}

export function StudentResultsPage() {
  const timeZone = useWorkspaceTimezone()
  const [open, setOpen] = useState<string | null>(null)
  const results = useQuery({ queryKey: ['me', 'results'], queryFn: () => api.get<ResultSummary[]>('/me/results') })
  return (
    <section>
      <h1>My results</h1>
      <QueryState query={results} isEmpty={(items) => items.length === 0} empty="No submitted exams yet.">
        {(items) => (
          <ul className="plain-list">
            {items.map((result) => (
              <li key={result.attemptId} className="card">
                <h2>
                  {result.examTitle} <small className="muted">attempt {result.attemptNumber}</small>
                </h2>
                {result.state === 'PENDING' ? (
                  <p>Waiting for your teacher to publish the result.</p>
                ) : (
                  <>
                    <p>
                      <strong>
                        {result.earned} / {result.maximum}
                      </strong>{' '}
                      · {result.passed ? 'Passed' : 'Not passed'} · published {formatDateTime(result.publishedAt, timeZone)}
                    </p>
                    <button type="button" aria-expanded={open === result.attemptId} onClick={() => setOpen(open === result.attemptId ? null : result.attemptId)}>
                      {open === result.attemptId ? 'Hide details' : `Show details for ${result.examTitle}`}
                    </button>
                    {open === result.attemptId && <Detail attemptId={result.attemptId} />}
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </section>
  )
}

function Detail({ attemptId }: { attemptId: string }) {
  const detail = useQuery({ queryKey: ['me', 'results', attemptId], queryFn: () => api.get<ResultDetail>(`/me/results/${attemptId}`) })
  return (
    <QueryState query={detail}>
      {(result) => (
        <ol className="questions">
          {result.questions.map((question) => (
            <li key={question.position}>
              <p className="prompt">{question.prompt}</p>
              <p>
                {question.points} / {question.maxPoints} points
              </p>
              <p className="muted">Your answer: {question.yourText ?? (question.yourChoices.join(', ') || 'no answer')}</p>
              {question.feedback && <p>Feedback: {question.feedback}</p>}
              {question.correctAnswers && question.correctAnswers.length > 0 && <p>Correct answer: {question.correctAnswers.join(', ')}</p>}
              {question.explanation && <p className="muted">{question.explanation}</p>}
            </li>
          ))}
        </ol>
      )}
    </QueryState>
  )
}
