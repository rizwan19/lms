import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, query, type Page } from '../../shared/api'
import { Pagination, QueryState, formatDateTime } from '../../shared/ui'
import { useInstitutionTimezone } from '../../shared/useInstitution'
import type { ProgressRow } from './types'

/** Progress matrix: one row per student with explanations instead of bare numbers. */
export function ClassProgressPage({ classId }: { classId: string }) {
  const timeZone = useInstitutionTimezone()
  const [page, setPage] = useState(0)
  const rows = useQuery({
    queryKey: ['report', 'progress', classId, page],
    queryFn: () => api.get<Page<ProgressRow>>(`/reports/classes/${classId}/progress${query({ page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <QueryState query={rows} isEmpty={(data) => data.items.length === 0} empty="No students enrolled.">
      {(data) => (
        <>
          <div className="table-scroll">
            <table>
              <caption className="visually-hidden">Student progress</caption>
              <thead>
                <tr>
                  <th scope="col">Student</th>
                  <th scope="col">Required lessons</th>
                  <th scope="col">Required exams passed</th>
                  <th scope="col">Waiting for grading</th>
                  <th scope="col">Last activity</th>
                  <th scope="col">Completion</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((row) => (
                  <tr key={row.enrollmentId}>
                    <th scope="row">
                      {row.displayName} <small className="muted">{row.username}</small>
                      {row.enrollmentStatus !== 'ACTIVE' && <small className="muted"> ({row.enrollmentStatus.toLowerCase()})</small>}
                    </th>
                    <td>{row.lessonPercent === null ? 'No required lessons' : `${row.completedRequiredLessons}/${row.requiredLessons} (${row.lessonPercent}%)`}</td>
                    <td>
                      {row.passedRequiredExams}/{row.requiredExams}
                    </td>
                    <td>{row.pendingGrading}</td>
                    <td>
                      {row.lastActivityAt ? formatDateTime(row.lastActivityAt, timeZone) : 'Never'}
                      {row.inactive && <strong className="error"> · inactive</strong>}
                    </td>
                    <td>{row.completionStatus.replace('_', ' ').toLowerCase()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </>
      )}
    </QueryState>
  )
}
