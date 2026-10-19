import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, QueryState, fieldErrors, formatDateTime, scopedErrors } from '../../shared/ui'
import { fromServer, move, newLesson, newSection, toPayload, type DraftState, type LessonDraft, type SectionDraft } from './draftState'
import { LessonEditor } from './LessonEditor'
import type { CourseDetail, Draft, PublishedEdition } from './types'
import { CourseExams, useCourseExams } from '../assessments/CourseExams'

export function CourseEditorPage() {
  const { id = '' } = useParams()
  const course = useQuery({ queryKey: ['course', id], queryFn: () => api.get<CourseDetail>(`/courses/${id}`) })
  const [published, setPublished] = useState<number | null>(null)

  return (
    <QueryState query={course}>
      {(detail) => (
        <section>
          <div className="page-header">
            <h1>{detail.title}</h1>
            <Link to={`/teacher/courses/${id}/preview`}>Preview</Link>
          </div>
          {published && (
            <p className="notice" role="status">
              Published edition {published}.
            </p>
          )}
          {detail.draft ? <DraftLoader courseId={id} onPublished={setPublished} /> : <Editions course={detail} onStarted={() => setPublished(null)} />}
          <CourseExams courseId={id} />
        </section>
      )}
    </QueryState>
  )
}

function Editions({ course, onStarted }: { course: CourseDetail; onStarted: () => void }) {
  const queryClient = useQueryClient()
  const start = useMutation({
    mutationFn: (versionId: string) => api.post<Draft>(`/courses/${course.id}/draft`, { fromVersionId: versionId }),
    onSuccess: () => {
      onStarted()
      return queryClient.invalidateQueries({ queryKey: ['course', course.id] })
    },
  })
  return (
    <div className="card">
      <h2>Published editions</h2>
      <p className="muted">Published editions never change. To correct one, start a new draft from it and publish a new edition.</p>
      <ul className="plain-list">
        {course.editions.map((edition) => (
          <li key={edition.versionId}>
            <strong>Edition {edition.edition}</strong> — {edition.title} <small className="muted">{formatDateTime(edition.publishedAt)}</small>
            <div className="actions">
              <Link to={`/teacher/courses/${course.id}/preview?versionId=${edition.versionId}`}>Preview edition {edition.edition}</Link>
              <button type="button" onClick={() => start.mutate(edition.versionId)} disabled={start.isPending}>
                Start new draft from edition {edition.edition}
              </button>
            </div>
          </li>
        ))}
      </ul>
      <ErrorMessage error={start.error} />
    </div>
  )
}

function DraftLoader({ courseId, onPublished }: { courseId: string; onPublished: (edition: number) => void }) {
  const draft = useQuery({ queryKey: ['course', courseId, 'draft'], queryFn: () => api.get<Draft>(`/courses/${courseId}/draft`) })
  return <QueryState query={draft}>{(loaded) => <DraftEditor key={loaded.versionId} draft={loaded} onPublished={onPublished} />}</QueryState>
}

function DraftEditor({ draft, onPublished }: { draft: Draft; onPublished: (edition: number) => void }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<DraftState>(() => fromServer(draft))
  const [revision, setRevision] = useState(draft.revision)
  const [dirty, setDirty] = useState(false)

  const save = useMutation({
    mutationFn: () => api.put<Draft>(`/courses/${draft.courseId}/draft`, toPayload(state, revision)),
    onSuccess: (saved) => {
      queryClient.setQueryData(['course', draft.courseId, 'draft'], saved)
      setState(fromServer(saved))
      setRevision(saved.revision)
      setDirty(false)
    },
  })
  const publish = useMutation({
    mutationFn: () => api.post<PublishedEdition>(`/courses/${draft.courseId}/publish`, { expectedRevision: revision }),
    onSuccess: (edition) => {
      onPublished(edition.edition)
      queryClient.invalidateQueries({ queryKey: ['course', draft.courseId] })
      queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
  })
  const errors = fieldErrors(save.error ?? publish.error)

  const update = (next: DraftState) => {
    setState(next)
    setDirty(true)
  }
  const updateSection = (index: number, patch: Partial<SectionDraft>) =>
    update({ ...state, sections: state.sections.map((section, i) => (i === index ? { ...section, ...patch } : section)) })
  const updateLesson = (s: number, l: number, patch: Partial<LessonDraft>) =>
    updateSection(s, { lessons: state.sections[s].lessons.map((lesson, i) => (i === l ? { ...lesson, ...patch } : lesson)) })

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate()
      }}
    >
      <div className="card">
        <div className="field">
          <label htmlFor="course-title">Course title</label>
          <input id="course-title" value={state.title} maxLength={200} onChange={(event) => update({ ...state, title: event.target.value })} />
          {errors.title && <small className="error">{errors.title}</small>}
        </div>
        <div className="field">
          <label htmlFor="course-description">Description</label>
          <textarea id="course-description" value={state.description} maxLength={5000} onChange={(event) => update({ ...state, description: event.target.value })} />
        </div>
      </div>

      {state.sections.map((section, s) => (
        <div className="card" key={section.key}>
          <div className="field">
            <label htmlFor={`${section.key}-title`}>Section {s + 1} title</label>
            <input id={`${section.key}-title`} value={section.title} maxLength={200} onChange={(event) => updateSection(s, { title: event.target.value })} />
            {errors[`sections[${s}].title`] && <small className="error">{errors[`sections[${s}].title`]}</small>}
          </div>
          {section.lessons.map((lesson, l) => (
            <LessonEditor
              key={lesson.key}
              name={`Lesson ${s + 1}.${l + 1}`}
              lesson={lesson}
              errors={scopedErrors(errors, `sections[${s}].lessons[${l}].`)}
              onChange={(patch) => updateLesson(s, l, patch)}
              onMove={(offset) => updateSection(s, { lessons: move(section.lessons, l, offset) })}
              onRemove={() => updateSection(s, { lessons: section.lessons.filter((_, i) => i !== l) })}
            />
          ))}
          <div className="actions">
            <button type="button" onClick={() => updateSection(s, { lessons: [...section.lessons, newLesson()] })}>
              Add lesson to section {s + 1}
            </button>
            <button type="button" onClick={() => update({ ...state, sections: move(state.sections, s, -1) })}>
              Move section up
            </button>
            <button type="button" onClick={() => update({ ...state, sections: move(state.sections, s, 1) })}>
              Move section down
            </button>
            <button type="button" className="danger" onClick={() => update({ ...state, sections: state.sections.filter((_, i) => i !== s) })}>
              Remove section
            </button>
          </div>
        </div>
      ))}

      <div className="actions sticky-actions">
        <button type="button" onClick={() => update({ ...state, sections: [...state.sections, newSection()] })}>
          Add section
        </button>
        <button type="submit" className="primary" disabled={save.isPending}>
          Save draft
        </button>
        <button type="button" disabled={dirty || publish.isPending} onClick={() => publish.mutate()} title={dirty ? 'Save the draft first' : undefined}>
          Publish edition
        </button>
        {dirty && <span className="muted">Unsaved changes</span>}
        {save.isSuccess && !dirty && <span role="status">Draft saved.</span>}
      </div>
      <RequiredExams
        courseId={draft.courseId}
        selected={state.requiredExamVersionIds}
        error={errors.requiredExamVersionIds}
        onChange={(requiredExamVersionIds) => update({ ...state, requiredExamVersionIds })}
      />
      {errors.sections && <p className="error">{errors.sections}</p>}
      <ErrorMessage error={save.error ?? publish.error} />
    </form>
  )
}

/** Required exams are part of the edition's completion policy; each refers to a published exam edition. */
function RequiredExams({ courseId, selected, error, onChange }: { courseId: string; selected: string[]; error?: string; onChange: (ids: string[]) => void }) {
  const exams = useCourseExams(courseId)
  const published = exams.data?.filter((exam) => exam.latestVersionId) ?? []
  const latest = new Set(published.map((exam) => exam.latestVersionId))
  return (
    <fieldset className="card">
      <legend>Required exams</legend>
      {published.length === 0 && <p className="muted">Publish an exam to make it a completion requirement.</p>}
      {published.map((exam) => (
        <label key={exam.id} className="inline">
          <input
            type="checkbox"
            checked={selected.includes(exam.latestVersionId!)}
            onChange={(event) => onChange(event.target.checked ? [...selected, exam.latestVersionId!] : selected.filter((id) => id !== exam.latestVersionId))}
          />
          {exam.title} (edition {exam.latestEdition})
        </label>
      ))}
      {selected
        .filter((id) => !latest.has(id))
        .map((id) => (
          <label key={id} className="inline">
            <input type="checkbox" checked onChange={() => onChange(selected.filter((other) => other !== id))} /> An earlier exam edition
          </label>
        ))}
      {error && <small className="error">{error}</small>}
    </fieldset>
  )
}
