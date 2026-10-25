import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { Workspace } from './types'

export function useWorkspace() {
  return useQuery({ queryKey: ['workspace'], queryFn: () => api.get<Workspace>('/workspace'), staleTime: 300_000 })
}

export function useWorkspaceTimezone(): string {
  return useWorkspace().data?.timezone ?? 'UTC'
}
