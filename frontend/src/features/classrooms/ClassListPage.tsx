import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, query, type Page } from '../../shared/api'
import { zonedInputToInstant } from '../../shared/time'
import { ErrorMessage, Field, Pagination, QueryState, fieldErrors, formatDateTime } from '../../shared/ui'
import { useWorkspaceTimezone } from '../../shared/useWorkspace'
import type { CourseDetail, CourseSummary } from '../courses/types'
import { ScheduleFields } from './ScheduleFields'
import type { ClassDetail, ClassSummary } from './types'

const STATUS_LABELS = { DRAFT: 'Draft', ACTIVE: 'Active', ARCHIVED: 'Archived' }

export function ClassListPage() {
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const timeZone = useWorkspaceTimezone()
  const classes = useQuery({
    queryKey: ['classes', page],
    queryFn: () => api.get<Page<ClassSummary>>(`/classes${query({ page })}`),
    placeholderData: keepPreviousData,
  })

  return (
    <section>
      <div className="page-header">
        <h1>Classes</h1>
        {!creating && (
          <button type="button" className="primary" onClick={() => setCreating(true)}>
            New class
          </button>
        )}
      </div>
      {creating && <NewClassForm onCancel={() => setCreating(false)} />}
      <QueryState query={classes} isEmpty={(data) => data.items.length === 0} empty="No classes yet. Publish a course, then create a class for it.">
        {(data) => (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Class</th>
                    <th scope="col">Course</th>
                    <th scope="col">Status</th>
                    <th scope="col">Opens</th>
                    <th scope="col">Closes</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <Link to={`/teacher/classes/${item.id}`}>{item.name}</Link>
                      </td>
                      <td>
                        {item.courseTitle} (edition {item.edition})
                      </td>
                      <td>{STATUS_LABELS[item.status]}</td>
                      <td>{item.startsAt ? formatDateTime(item.startsAt, timeZone) : 'On activation'}</td>
                      <td>{item.endsAt ? formatDateTime(item.endsAt, timeZone) : 'Self-paced'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />
          </>
        )}
      </QueryState>
    </section>
  )
}

/** Picking a course pre-fills a default self-paced class on its latest edition; the server validates as usual. */
function NewClassForm({ onCancel }: { onCancel: () => void }) {
  const navigate = useNavigate()
  const timeZone = useWorkspaceTimezone()
  const [courseId, setCourseId] = useState('')
  const [name, setName] = useState('')
  const courses = useQuery({ queryKey: ['courses', 'publishable'], queryFn: () => api.get<Page<CourseSummary>>('/courses?size=100') })
  const course = useQuery({ queryKey: ['course', courseId], queryFn: () => api.get<CourseDetail>(`/courses/${courseId}`), enabled: !!courseId })
  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post<ClassDetail>('/classes', body),
    onSuccess: (created) => navigate(`/teacher/classes/${created.id}`),
  })
  const errors = fieldErrors(create.error)
  const published = courses.data?.items.filter((item) => item.latestEdition !== null) ?? []

  return (
    <form
      className="card"
      aria-label="New class"
      onSubmit={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        create.mutate({
          courseVersionId: form.get('courseVersionId'),
          name,
          startsAt: zonedInputToInstant(String(form.get('startsAt')), timeZone),
          endsAt: zonedInputToInstant(String(form.get('endsAt')), timeZone),
        })
      }}
    >
      <h2>New class</h2>
      <Field label="Course" name="course">
        <select
          id="course"
          value={courseId}
          required
          onChange={(event) => {
            setCourseId(event.target.value)
            if (!name) setName(published.find((item) => item.id === event.target.value)?.title ?? '')
          }}
        >
          <option value="">Choose a published course</option>
          {published.map((item) => (
            <option key={item.id} value={item.id}>
              {item.title}
            </option>
          ))}
        </select>
      </Field>
      {course.data && (
        <Field label="Edition" name="courseVersionId" errors={errors}>
          <select id="courseVersionId" name="courseVersionId" required>
            {course.data.editions.map((edition) => (
              <option key={edition.versionId} value={edition.versionId}>
                Edition {edition.edition}
              </option>
            ))}
          </select>
        </Field>
      )}
      <Field label="Class name" name="name" errors={errors}>
        <input id="name" value={name} onChange={(event) => setName(event.target.value)} required maxLength={200} />
      </Field>
      <ScheduleFields timeZone={timeZone} errors={errors} />
      <ErrorMessage error={create.error} />
      <div className="actions">
        <button type="submit" className="primary" disabled={create.isPending || !course.data}>
          Create class
        </button>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
