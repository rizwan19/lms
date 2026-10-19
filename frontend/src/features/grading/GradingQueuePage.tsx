import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, query, type Page } from '../../shared/api'
import { Pagination, QueryState, formatDateTime } from '../../shared/ui'
import { useInstitutionTimezone } from '../../shared/useInstitution'
import type { ClassSummary } from '../classrooms/types'
import type { QueueRow } from './types'

const STATUS_LABELS: Record<QueueRow['gradeStatus'], string> = { DRAFT: 'Needs grading', FINALIZED: 'Ready to publish', PUBLISHED: 'Published', SUPERSEDED: 'Superseded' }

export function GradingQueuePage() {
  const [params, setParams] = useSearchParams()
  const timeZone = useInstitutionTimezone()
  const classId = params.get('classId') ?? ''
  const status = params.get('status') ?? 'PENDING'
  const [page, setPage] = useState(0)
  const classes = useQuery({ queryKey: ['classes', 'all'], queryFn: () => api.get<Page<ClassSummary>>('/classes?size=100') })
  const queue = useQuery({
    queryKey: ['grading', classId, status, page],
    queryFn: () => api.get<Page<QueueRow>>(`/grading${query({ classId, status, page })}`),
    enabled: !!classId,
    placeholderData: keepPreviousData,
  })

  return (
    <section>
      <h1>Grading</h1>
      <div className="inline-form">
        <label className="field">
          Class
          <select value={classId} onChange={(event) => setParams({ classId: event.target.value, status })}>
            <option value="">Choose a class</option>
            {classes.data?.items.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Show
          <select value={status} onChange={(event) => setParams({ classId, status: event.target.value })}>
            <option value="PENDING">Waiting for grading or publication</option>
            <option value="PUBLISHED">Published</option>
            <option value="ALL">All</option>
          </select>
        </label>
      </div>
      {classId && (
        <QueryState query={queue} isEmpty={(data) => data.items.length === 0} empty="Nothing to grade.">
          {(data) => (
            <>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Student</th>
                      <th scope="col">Exam</th>
                      <th scope="col">Submitted</th>
                      <th scope="col">State</th>
                      <th scope="col">Published score</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.items.map((row) => (
                      <tr key={row.attemptId}>
                        <td>
                          <Link to={`/teacher/grading/${row.attemptId}`}>{row.studentName}</Link>
                        </td>
                        <td>
                          {row.examTitle} (attempt {row.attemptNumber})
                        </td>
                        <td>{formatDateTime(row.finalizedAt, timeZone)}</td>
                        <td>{STATUS_LABELS[row.gradeStatus]}</td>
                        <td>{row.publishedEarned === null ? '—' : `${row.publishedEarned} / ${row.maximum}`}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />
            </>
          )}
        </QueryState>
      )}
    </section>
  )
}
