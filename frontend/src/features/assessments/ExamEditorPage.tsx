import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, QueryState, fieldErrors, formatDateTime, scopedErrors } from '../../shared/ui'
import { move } from '../courses/draftState'
import { fromContent, newQuestion, toExamPayload, type ExamDraftState, type QuestionDraft } from './examDraft'
import { QuestionEditor } from './QuestionEditor'
import type { ExamContent, ExamDetail } from './types'

export function ExamEditorPage() {
  const { id = '' } = useParams()
  const exam = useQuery({ queryKey: ['exam', id], queryFn: () => api.get<ExamDetail>(`/exams/${id}`) })
  const [published, setPublished] = useState<number | null>(null)

  return (
    <QueryState query={exam}>
      {(detail) => (
        <section>
          <div className="page-header">
            <h1>{detail.title}</h1>
            <Link to={`/teacher/courses/${detail.courseId}/edit`}>Back to course</Link>
          </div>
          {published && (
            <p className="notice" role="status">
              Published exam edition {published}.
            </p>
          )}
          {detail.draft ? <DraftLoader examId={id} onPublished={setPublished} /> : <Editions exam={detail} onStarted={() => setPublished(null)} />}
        </section>
      )}
    </QueryState>
  )
}

function Editions({ exam, onStarted }: { exam: ExamDetail; onStarted: () => void }) {
  const queryClient = useQueryClient()
  const start = useMutation({
    mutationFn: (versionId: string) => api.post<ExamContent>(`/exams/${exam.id}/draft`, { fromVersionId: versionId }),
    onSuccess: () => {
      onStarted()
      return queryClient.invalidateQueries({ queryKey: ['exam', exam.id] })
    },
  })
  return (
    <div className="card">
      <h2>Published editions</h2>
      <p className="muted">Published exams never change. Classes and attempts keep the edition they started with.</p>
      <ul className="plain-list">
        {exam.editions.map((edition) => (
          <li key={edition.versionId}>
            <strong>Edition {edition.edition}</strong> <small className="muted">{formatDateTime(edition.publishedAt)}</small>{' '}
            <button type="button" onClick={() => start.mutate(edition.versionId)} disabled={start.isPending}>
              Start new draft from edition {edition.edition}
            </button>
          </li>
        ))}
      </ul>
      <ErrorMessage error={start.error} />
    </div>
  )
}

function DraftLoader({ examId, onPublished }: { examId: string; onPublished: (edition: number) => void }) {
  const draft = useQuery({ queryKey: ['exam', examId, 'draft'], queryFn: () => api.get<ExamContent>(`/exams/${examId}/draft`) })
  return <QueryState query={draft}>{(content) => <DraftEditor key={content.versionId} content={content} onPublished={onPublished} />}</QueryState>
}

function DraftEditor({ content, onPublished }: { content: ExamContent; onPublished: (edition: number) => void }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<ExamDraftState>(() => fromContent(content))
  const [revision, setRevision] = useState(content.revision)
  const [dirty, setDirty] = useState(false)
  const save = useMutation({
    mutationFn: () => api.put<ExamContent>(`/exams/${content.examId}/draft`, toExamPayload(state, revision)),
    onSuccess: (saved) => {
      setState(fromContent(saved))
      setRevision(saved.revision)
      setDirty(false)
    },
  })
  const publish = useMutation({
    mutationFn: () => api.post<ExamContent>(`/exams/${content.examId}/publish`, { expectedRevision: revision }),
    onSuccess: (edition) => {
      onPublished(edition.edition ?? 0)
      queryClient.invalidateQueries({ queryKey: ['exam', content.examId] })
      queryClient.invalidateQueries({ queryKey: ['exams'] })
    },
  })
  const errors = fieldErrors(save.error ?? publish.error)
  const update = (next: ExamDraftState) => {
    setState(next)
    setDirty(true)
  }
  const updateQuestion = (index: number, patch: Partial<QuestionDraft>) =>
    update({ ...state, questions: state.questions.map((question, i) => (i === index ? { ...question, ...patch } : question)) })

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate()
      }}
    >
      <div className="card">
        <div className="field">
          <label htmlFor="exam-title">Exam title</label>
          <input id="exam-title" value={state.title} maxLength={200} onChange={(event) => update({ ...state, title: event.target.value })} />
          {errors.title && <small className="error">{errors.title}</small>}
        </div>
        <div className="field">
          <label htmlFor="exam-instructions">Instructions</label>
          <textarea id="exam-instructions" value={state.instructions} onChange={(event) => update({ ...state, instructions: event.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="exam-pass">Pass mark (%)</label>
          <input id="exam-pass" inputMode="decimal" value={state.passPercent} onChange={(event) => update({ ...state, passPercent: event.target.value })} />
          {errors.passPercent && <small className="error">{errors.passPercent}</small>}
        </div>
      </div>
      {state.questions.map((question, index) => (
        <QuestionEditor
          key={question.key}
          name={`Question ${index + 1}`}
          question={question}
          errors={scopedErrors(errors, `questions[${index}].`)}
          onChange={(patch) => updateQuestion(index, patch)}
          onMove={(offset) => update({ ...state, questions: move(state.questions, index, offset) })}
          onRemove={() => update({ ...state, questions: state.questions.filter((_, i) => i !== index) })}
        />
      ))}
      {errors.questions && <p className="error">{errors.questions}</p>}
      <div className="actions sticky-actions">
        <button type="button" onClick={() => update({ ...state, questions: [...state.questions, newQuestion()] })}>
          Add question
        </button>
        <button type="submit" className="primary" disabled={save.isPending}>
          Save exam draft
        </button>
        <button type="button" disabled={dirty || publish.isPending} onClick={() => publish.mutate()}>
          Publish exam
        </button>
        {dirty && <span className="muted">Unsaved changes</span>}
        {save.isSuccess && !dirty && <span role="status">Exam draft saved.</span>}
      </div>
      <ErrorMessage error={save.error ?? publish.error} />
    </form>
  )
}
