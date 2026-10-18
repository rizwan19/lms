import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api, type Page } from '../../shared/api'
import { formatDateTime, QueryState } from '../../shared/ui'

export type Notification = { id: string; type: string; title: string; body: string; link: string | null; createdAt: string; readAt: string | null }

/** Own in-app notifications in a disclosure menu, with unread count. */
export function NotificationList() {
  const queryClient = useQueryClient()
  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<Page<Notification>>('/notifications?size=20'),
    refetchInterval: 60_000,
  })
  const markRead = useMutation({
    mutationFn: (id: string) => api.put(`/notifications/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const unread = notifications.data?.items.filter((item) => !item.readAt).length ?? 0

  return (
    <details className="popover notifications">
      <summary aria-label={`Notifications, ${unread} unread`}>
        Notifications{unread > 0 && <span className="badge">{unread}</span>}
      </summary>
      <div className="menu">
        <QueryState query={notifications} isEmpty={(data) => data.items.length === 0} empty="No notifications.">
          {(data) => (
            <ul className="plain-list">
              {data.items.map((item) => (
                <li key={item.id} className={item.readAt ? 'read' : 'unread'}>
                  <strong>{item.link ? <Link to={item.link}>{item.title}</Link> : item.title}</strong>
                  {item.body && <p>{item.body}</p>}
                  <small className="muted">{formatDateTime(item.createdAt)}</small>
                  {!item.readAt && (
                    <button type="button" onClick={() => markRead.mutate(item.id)} aria-label={`Mark "${item.title}" read`}>
                      Mark read
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </QueryState>
      </div>
    </details>
  )
}
