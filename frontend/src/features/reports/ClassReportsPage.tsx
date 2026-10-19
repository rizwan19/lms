import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, QueryState, formatDateTime } from '../../shared/ui'
import { ClassProgressPage } from './ClassProgressPage'
import { ClassScoresPage } from './ClassScoresPage'
import type { ExportView } from './types'

export function ClassReportsPage() {
  const { id = '' } = useParams()
  const [tab, setTab] = useState<'progress' | 'scores'>('progress')
  return (
    <section>
      <div className="page-header">
        <h1>Class reports</h1>
        <Link to={`/teacher/classes/${id}`}>Back to class</Link>
      </div>
      <div className="tabs" role="group" aria-label="Report">
        <button type="button" aria-pressed={tab === 'progress'} onClick={() => setTab('progress')}>
          Progress
        </button>
        <button type="button" aria-pressed={tab === 'scores'} onClick={() => setTab('scores')}>
          Scores
        </button>
      </div>
      {tab === 'progress' ? <ClassProgressPage classId={id} /> : <ClassScoresPage classId={id} />}
      <Exports classId={id} />
    </section>
  )
}

function Exports({ classId }: { classId: string }) {
  const queryClient = useQueryClient()
  const exports = useQuery({
    queryKey: ['exports', classId],
    queryFn: () => api.get<ExportView[]>(`/reports/classes/${classId}/exports`),
    refetchInterval: (state) => (state.state.data?.some((item) => item.status === 'PENDING') ? 2000 : false),
  })
  const request = useMutation({
    mutationFn: (type: ExportView['type']) => api.post(`/reports/classes/${classId}/export`, { type }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['exports', classId] }),
  })
  return (
    <section className="card" aria-labelledby="exports">
      <h2 id="exports">CSV exports</h2>
      <div className="actions">
        <button type="button" onClick={() => request.mutate('PROGRESS')}>
          Export progress
        </button>
        <button type="button" onClick={() => request.mutate('SCORES')}>
          Export scores
        </button>
      </div>
      <ErrorMessage error={request.error} />
      <QueryState query={exports} isEmpty={(items) => items.length === 0} empty="No exports yet.">
        {(items) => (
          <ul className="plain-list">
            {items.map((item) => (
              <li key={item.id}>
                {item.type.toLowerCase()} · {formatDateTime(item.createdAt)} ·{' '}
                {item.status === 'READY' ? <a href={`/api/v1/reports/exports/${item.id}/download`}>Download {item.type.toLowerCase()} CSV</a> : item.status.toLowerCase()}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </section>
  )
}
