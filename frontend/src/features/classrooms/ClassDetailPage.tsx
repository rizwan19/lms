import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, query, type Page } from '../../shared/api'
import type { TemporaryCredential } from '../../shared/types'
import { zonedInputToInstant } from '../../shared/time'
import { ErrorMessage, Field, Pagination, QueryState, fieldErrors, formatDateTime } from '../../shared/ui'
import { useWorkspaceTimezone } from '../../shared/useWorkspace'
import { useSession } from '../../app/session'
import { CreateAccountForm } from '../accounts/CreateAccountForm'
import { CredentialNotice } from '../accounts/CredentialNotice'
import type { Account } from '../accounts/types'
import { ExamSchedule } from '../assessments/ExamScheduleDialog'
import { RosterImportDialog } from './RosterImportDialog'
import { ClassCertificates } from '../certificates/ClassCertificates'
import { ScheduleFields } from './ScheduleFields'
import type { ClassDetail, EnrollmentStatus, RosterRow } from './types'

export function ClassDetailPage() {
  const { id = '' } = useParams()
  const detail = useQuery({ queryKey: ['class', id], queryFn: () => api.get<ClassDetail>(`/classes/${id}`) })
  return (
    <QueryState query={detail}>
      {(item) => (
        <section>
          <div className="page-header">
            <h1>{item.name}</h1>
            <Link to={`/teacher/classes/${item.id}/reports`}>Reports</Link>
          </div>
          <p>
            {item.courseTitle}, edition {item.edition} · <strong>{item.status.toLowerCase()}</strong>
          </p>
          <ClassSettings key={item.revision} item={item} />
          <Teachers item={item} />
          <ExamSchedule classId={item.id} archived={item.status === 'ARCHIVED'} />
          <Roster classId={item.id} archived={item.status === 'ARCHIVED'} />
          <ClassCertificates classId={item.id} />
        </section>
      )}
    </QueryState>
  )
}

function ClassSettings({ item }: { item: ClassDetail }) {
  const queryClient = useQueryClient()
  const timeZone = useWorkspaceTimezone()
  const onSuccess = (updated: ClassDetail) => {
    queryClient.setQueryData(['class', item.id], updated)
    queryClient.invalidateQueries({ queryKey: ['classes'] })
  }
  const save = useMutation({ mutationFn: (body: Record<string, unknown>) => api.put<ClassDetail>(`/classes/${item.id}`, body), onSuccess })
  const transition = useMutation({ mutationFn: (action: 'activate' | 'archive') => api.post<ClassDetail>(`/classes/${item.id}/${action}`), onSuccess })
  const errors = fieldErrors(save.error)

  return (
    <form
      className="card"
      aria-label="Class settings"
      onSubmit={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        save.mutate({
          name: form.get('name'),
          startsAt: zonedInputToInstant(String(form.get('startsAt')), timeZone),
          endsAt: zonedInputToInstant(String(form.get('endsAt')), timeZone),
          expectedRevision: item.revision,
        })
      }}
    >
      <Field label="Class name" name="name" errors={errors}>
        <input id="name" name="name" defaultValue={item.name} required maxLength={200} disabled={item.status === 'ARCHIVED'} />
      </Field>
      <ScheduleFields timeZone={timeZone} startsAt={item.startsAt} endsAt={item.endsAt} errors={errors} />
      <ErrorMessage error={save.error ?? transition.error} />
      {item.status !== 'ARCHIVED' && (
        <div className="actions">
          <button type="submit" disabled={save.isPending}>
            Save schedule
          </button>
          {item.status === 'DRAFT' && (
            <button type="button" className="primary" onClick={() => transition.mutate('activate')}>
              Activate class
            </button>
          )}
          <button type="button" className="danger" onClick={() => transition.mutate('archive')}>
            Archive class
          </button>
        </div>
      )}
    </form>
  )
}

function Teachers({ item }: { item: ClassDetail }) {
  const { me } = useSession()
  const queryClient = useQueryClient()
  const isAdministrator = me?.capabilities.canAdministerWorkspace ?? false
  const teachers = useQuery({ queryKey: ['users', 'teachers'], queryFn: () => api.get<Page<Account>>('/users?role=TEACHER&size=100'), enabled: isAdministrator })
  const save = useMutation({
    mutationFn: (teacherIds: string[]) => api.put<ClassDetail>(`/classes/${item.id}/teachers`, { teacherIds }),
    onSuccess: (updated) => queryClient.setQueryData(['class', item.id], updated),
  })

  return (
    <section className="card" aria-labelledby="class-teachers">
      <h2 id="class-teachers">Teachers</h2>
      <p>{item.teachers.map((teacher) => teacher.displayName).join(', ') || 'No teachers assigned.'}</p>
      {isAdministrator && teachers.data && (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate(new FormData(event.currentTarget).getAll('teacherIds').map(String))
          }}
        >
          <fieldset>
            <legend>Assigned teachers</legend>
            {teachers.data.items
              .filter((teacher) => teacher.status === 'ACTIVE')
              .map((teacher) => (
                <label key={teacher.id} className="inline">
                  <input type="checkbox" name="teacherIds" value={teacher.id} defaultChecked={item.teachers.some((assigned) => assigned.id === teacher.id)} />
                  {teacher.displayName} ({teacher.username})
                </label>
              ))}
          </fieldset>
          <ErrorMessage error={save.error} />
          <button type="submit">Save teachers</button>
        </form>
      )}
    </section>
  )
}

const STATUSES: EnrollmentStatus[] = ['ACTIVE', 'SUSPENDED', 'WITHDRAWN']

function Roster({ classId, archived }: { classId: string; archived: boolean }) {
  const queryClient = useQueryClient()
  const timeZone = useWorkspaceTimezone()
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState<EnrollmentStatus | ''>('')
  const [creating, setCreating] = useState(false)
  const [importing, setImporting] = useState(false)
  const [credentials, setCredentials] = useState<TemporaryCredential[]>([])
  const roster = useQuery({
    queryKey: ['roster', classId, filter, page],
    queryFn: () => api.get<Page<RosterRow>>(`/classes/${classId}/enrollments${query({ status: filter, page })}`),
    placeholderData: keepPreviousData,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['roster', classId] })
  const enroll = useMutation({ mutationFn: (username: string) => api.post<RosterRow>(`/classes/${classId}/enrollments`, { username }), onSuccess: refresh })
  const change = useMutation({
    mutationFn: ({ row, status }: { row: RosterRow; status: EnrollmentStatus }) => api.put<RosterRow>(`/classes/${classId}/enrollments/${row.enrollmentId}`, { status }),
    onSuccess: refresh,
  })

  return (
    <section className="card" aria-labelledby="roster">
      <h2 id="roster">Roster</h2>
      {!archived && (
        <form
          className="inline-form"
          aria-label="Enroll student"
          onSubmit={(event) => {
            event.preventDefault()
            const form = event.currentTarget
            enroll.mutate(String(new FormData(form).get('username')), { onSuccess: () => form.reset() })
          }}
        >
          <Field label="Student username" name="enroll-username" errors={fieldErrors(enroll.error)}>
            <input id="enroll-username" name="username" required autoComplete="off" />
          </Field>
          <button type="submit" className="primary" disabled={enroll.isPending}>
            Enroll
          </button>
          <button type="button" onClick={() => setCreating(true)}>
            Create new student
          </button>
          <button type="button" onClick={() => setImporting(true)}>
            Import CSV
          </button>
        </form>
      )}
      {importing && <RosterImportDialog classId={classId} onImported={refresh} onClose={() => setImporting(false)} />}
      {creating && (
        <CreateAccountForm
          isAdministrator={false}
          onCancel={() => setCreating(false)}
          onCreated={(created) => {
            setCreating(false)
            if (created.temporaryCredential) setCredentials([created.temporaryCredential])
            enroll.mutate(created.account.username)
          }}
        />
      )}
      {credentials.length > 0 && <CredentialNotice credentials={credentials} onDismiss={() => setCredentials([])} />}
      <ErrorMessage error={enroll.error ?? change.error} />
      <label className="search">
        Show
        <select
          value={filter}
          onChange={(event) => {
            setFilter(event.target.value as EnrollmentStatus | '')
            setPage(0)
          }}
        >
          <option value="">All students</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {status.toLowerCase()}
            </option>
          ))}
        </select>
      </label>
      <QueryState query={roster} isEmpty={(data) => data.items.length === 0} empty="No students on this roster.">
        {(data) => (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Student</th>
                    <th scope="col">Username</th>
                    <th scope="col">Enrolled</th>
                    <th scope="col">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((row) => (
                    <tr key={row.enrollmentId}>
                      <td>{row.displayName}</td>
                      <td>{row.username}</td>
                      <td>{formatDateTime(row.enrolledAt, timeZone)}</td>
                      <td>
                        <select
                          aria-label={`Status of ${row.username}`}
                          value={row.status}
                          disabled={archived || change.isPending}
                          onChange={(event) => change.mutate({ row, status: event.target.value as EnrollmentStatus })}
                        >
                          {STATUSES.map((status) => (
                            <option key={status} value={status}>
                              {status.toLowerCase()}
                            </option>
                          ))}
                        </select>
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
