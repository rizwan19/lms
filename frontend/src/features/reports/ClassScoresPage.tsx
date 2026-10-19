import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, query, type Page } from '../../shared/api'
import { Pagination, QueryState } from '../../shared/ui'
import type { ScoreRow } from './types'

/** Best published scores; unpublished work shows as "pending", never as a score. */
export function ClassScoresPage({ classId }: { classId: string }) {
  const [page, setPage] = useState(0)
  const rows = useQuery({
    queryKey: ['report', 'scores', classId, page],
    queryFn: () => api.get<Page<ScoreRow>>(`/reports/classes/${classId}/scores${query({ page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <QueryState query={rows} isEmpty={(data) => data.items.length === 0} empty="No students enrolled.">
      {(data) => (
        <>
          <div className="table-scroll">
            <table>
              <caption className="visually-hidden">Best published scores</caption>
              <thead>
                <tr>
                  <th scope="col">Student</th>
                  {data.items[0]?.scores.map((score) => (
                    <th scope="col" key={score.assignmentId}>
                      {score.examTitle} {score.required ? '' : '(practice)'}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.items.map((row) => (
                  <tr key={row.enrollmentId}>
                    <th scope="row">{row.displayName}</th>
                    {row.scores.map((score) => (
                      <td key={score.assignmentId}>
                        {score.bestEarned === null ? '—' : `${score.bestEarned}/${score.maximum} ${score.passed ? '✓' : '✗'}`}
                        {score.pendingGrading && <small className="muted"> pending</small>}
                      </td>
                    ))}
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
