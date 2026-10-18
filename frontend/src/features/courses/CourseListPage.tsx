import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, query, type Page } from '../../shared/api'
import { ErrorMessage, Field, Pagination, QueryState, fieldErrors, formatDateTime } from '../../shared/ui'
import type { CourseDetail, CourseSummary } from './types'

export function CourseListPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [status, setStatus] = useState<CourseSummary['status']>('ACTIVE')
  const [page, setPage] = useState(0)
  const courses = useQuery({
    queryKey: ['courses', status, page],
    queryFn: () => api.get<Page<CourseSummary>>(`/courses${query({ status, page })}`),
    placeholderData: keepPreviousData,
  })
  const create = useMutation({
    mutationFn: (title: string) => api.post<CourseDetail>('/courses', { title }),
    onSuccess: (course) => navigate(`/teacher/courses/${course.id}/edit`),
  })
  const changeStatus = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'archive' | 'restore' }) => api.post(`/courses/${id}/${action}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses'] }),
  })

  return (
    <section>
      <h1>Courses</h1>
      <form
        className="card inline-form"
        onSubmit={(event) => {
          event.preventDefault()
          create.mutate(String(new FormData(event.currentTarget).get('title')))
        }}
      >
        <Field label="New course title" name="title" errors={fieldErrors(create.error)}>
          <input id="title" name="title" required maxLength={200} />
        </Field>
        <button type="submit" className="primary" disabled={create.isPending}>
          Create course
        </button>
      </form>
      <div className="tabs" role="group" aria-label="Course status">
        {(['ACTIVE', 'ARCHIVED'] as const).map((value) => (
          <button
            key={value}
            type="button"
            aria-pressed={status === value}
            onClick={() => {
              setStatus(value)
              setPage(0)
            }}
          >
            {value === 'ACTIVE' ? 'Active' : 'Archived'}
          </button>
        ))}
      </div>
      <ErrorMessage error={changeStatus.error} />
      <QueryState query={courses} isEmpty={(data) => data.items.length === 0} empty={status === 'ACTIVE' ? 'No courses yet. Create your first course above.' : 'No archived courses.'}>
        {(data) => (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Course</th>
                    <th scope="col">Latest edition</th>
                    <th scope="col">Draft</th>
                    <th scope="col">Updated</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((course) => (
                    <tr key={course.id}>
                      <td>
                        <Link to={`/teacher/courses/${course.id}/edit`}>{course.title}</Link>
                      </td>
                      <td>{course.latestEdition ?? 'Not published'}</td>
                      <td>{course.draftRevision === null ? '—' : 'Yes'}</td>
                      <td>{formatDateTime(course.updatedAt)}</td>
                      <td className="actions">
                        <Link to={`/teacher/courses/${course.id}/preview`}>Preview</Link>
                        <button type="button" onClick={() => changeStatus.mutate({ id: course.id, action: course.status === 'ACTIVE' ? 'archive' : 'restore' })}>
                          {course.status === 'ACTIVE' ? 'Archive' : 'Restore'}
                        </button>
                      </td>
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
