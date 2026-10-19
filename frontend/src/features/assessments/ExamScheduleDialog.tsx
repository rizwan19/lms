import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../shared/api'
import { instantToZonedInput, zonedInputToInstant } from '../../shared/time'
import { ErrorMessage, QueryState, fieldErrors, formatDateTime } from '../../shared/ui'
import { useInstitutionTimezone } from '../../shared/useInstitution'
import type { ExamAssignment, ExamSummary, FeedbackPolicy, ResultPolicy } from './types'

const FEEDBACK_LABELS: Record<FeedbackPolicy, string> = {
  SCORE_AND_COMMENTS: 'Score and comments only',
  AFTER_CLOSE: 'Answers and explanations after closing',
  AFTER_SUBMISSION: 'Answers right after submission (practice)',
}

/** A class's exam schedule: required exams from the course edition plus optional practice. */
export function ExamSchedule({ classId, archived }: { classId: string; archived: boolean }) {
  const timeZone = useInstitutionTimezone()
  const [editing, setEditing] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const assignments = useQuery({ queryKey: ['assignments', classId], queryFn: () => api.get<ExamAssignment[]>(`/exam-assignments?classId=${classId}`) })

  return (
    <section className="card" aria-labelledby="exam-schedule">
      <h2 id="exam-schedule">Exams</h2>
      <QueryState query={assignments} isEmpty={(items) => items.length === 0} empty="No exams scheduled.">
        {(items) => (
          <ul className="plain-list">
            {items.map((assignment) => (
              <li key={assignment.id}>
                <strong>{assignment.examTitle}</strong> (edition {assignment.edition}) · {assignment.required ? 'required' : 'practice'}
                <br />
                <small className="muted">
                  Opens {assignment.schedule.opensAt ? formatDateTime(assignment.schedule.opensAt, timeZone) : 'with the class'} · closes{' '}
                  {assignment.schedule.closesAt ? formatDateTime(assignment.schedule.closesAt, timeZone) : 'never'} ·{' '}
                  {assignment.schedule.durationMinutes ? `${assignment.schedule.durationMinutes} minutes` : 'untimed'} · {assignment.schedule.maxAttempts} attempt(s)
                </small>
                {!archived && editing !== assignment.id && (
                  <button type="button" onClick={() => setEditing(assignment.id)}>
                    Edit schedule for {assignment.examTitle}
                  </button>
                )}
                {editing === assignment.id && <ScheduleForm assignment={assignment} onDone={() => setEditing(null)} />}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
      {!archived && !adding && (
        <button type="button" onClick={() => setAdding(true)}>
          Add practice exam
        </button>
      )}
      {adding && <ScheduleForm classId={classId} onDone={() => setAdding(false)} />}
    </section>
  )
}

function ScheduleForm({ assignment, classId, onDone }: { assignment?: ExamAssignment; classId?: string; onDone: () => void }) {
  const queryClient = useQueryClient()
  const timeZone = useInstitutionTimezone()
  const targetClass = assignment?.classId ?? classId!
  const available = useQuery({
    queryKey: ['assignments', targetClass, 'available'],
    queryFn: () => api.get<ExamSummary[]>(`/exam-assignments/available?classId=${targetClass}`),
    enabled: !assignment,
  })
  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      assignment ? api.put<ExamAssignment>(`/exam-assignments/${assignment.id}`, { ...body, expectedRevision: assignment.revision }) : api.post<ExamAssignment>('/exam-assignments', { ...body, classId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assignments', targetClass] })
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const schedule = assignment?.schedule

  return (
    <form
      aria-label={assignment ? `Schedule for ${assignment.examTitle}` : 'New practice exam'}
      onSubmit={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        const duration = String(form.get('durationMinutes'))
        save.mutate({
          examVersionId: form.get('examVersionId') ?? undefined,
          opensAt: zonedInputToInstant(String(form.get('opensAt')), timeZone),
          closesAt: zonedInputToInstant(String(form.get('closesAt')), timeZone),
          durationMinutes: duration ? Number(duration) : null,
          maxAttempts: Number(form.get('maxAttempts')),
          resultPolicy: form.get('resultPolicy') || null,
          feedbackPolicy: form.get('feedbackPolicy'),
        })
      }}
    >
      {!assignment && (
        <div className="field">
          <label htmlFor="examVersionId">Exam</label>
          <select id="examVersionId" name="examVersionId" required>
            {available.data?.map((exam) => (
              <option key={exam.id} value={exam.latestVersionId!}>
                {exam.title} (edition {exam.latestEdition})
              </option>
            ))}
          </select>
          {errors.examVersionId && <small className="error">{errors.examVersionId}</small>}
        </div>
      )}
      <div className="inline-form">
        <div className="field">
          <label htmlFor="opensAt">Exam opens ({timeZone})</label>
          <input id="opensAt" name="opensAt" type="datetime-local" defaultValue={instantToZonedInput(schedule?.opensAt ?? null, timeZone)} />
        </div>
        <div className="field">
          <label htmlFor="closesAt">Exam closes ({timeZone})</label>
          <input id="closesAt" name="closesAt" type="datetime-local" defaultValue={instantToZonedInput(schedule?.closesAt ?? null, timeZone)} />
          {errors.closesAt && <small className="error">{errors.closesAt}</small>}
        </div>
        <div className="field">
          <label htmlFor="durationMinutes">Time limit (minutes, empty for untimed)</label>
          <input id="durationMinutes" name="durationMinutes" type="number" min={1} max={240} defaultValue={schedule ? (schedule.durationMinutes ?? '') : 60} />
          {errors.durationMinutes && <small className="error">{errors.durationMinutes}</small>}
        </div>
        <div className="field">
          <label htmlFor="maxAttempts">Attempts allowed</label>
          <input id="maxAttempts" name="maxAttempts" type="number" min={1} max={10} defaultValue={schedule?.maxAttempts ?? 1} />
          {errors.maxAttempts && <small className="error">{errors.maxAttempts}</small>}
        </div>
      </div>
      <div className="inline-form">
        <div className="field">
          <label htmlFor="resultPolicy">Results</label>
          <select id="resultPolicy" name="resultPolicy" defaultValue={schedule?.resultPolicy ?? ''}>
            <option value="">Default for this exam</option>
            <option value={'AUTO_PUBLISH' satisfies ResultPolicy}>Publish automatically (objective only)</option>
            <option value={'TEACHER_PUBLISH' satisfies ResultPolicy}>Teacher publishes</option>
          </select>
          {errors.resultPolicy && <small className="error">{errors.resultPolicy}</small>}
        </div>
        <div className="field">
          <label htmlFor="feedbackPolicy">Feedback</label>
          <select id="feedbackPolicy" name="feedbackPolicy" defaultValue={schedule?.feedbackPolicy ?? 'SCORE_AND_COMMENTS'}>
            {Object.entries(FEEDBACK_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          {errors.feedbackPolicy && <small className="error">{errors.feedbackPolicy}</small>}
          <small className="muted">Showing answers right after submission with several attempts lets students see keys before retrying.</small>
        </div>
      </div>
      <ErrorMessage error={save.error} />
      <div className="actions">
        <button type="submit" className="primary" disabled={save.isPending}>
          Save exam schedule
        </button>
        <button type="button" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  )
}
