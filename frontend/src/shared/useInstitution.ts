import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { Institution } from './types'

export function useInstitution() {
  return useQuery({ queryKey: ['institution'], queryFn: () => api.get<Institution>('/institution'), staleTime: 300_000 })
}

export function useInstitutionTimezone(): string {
  return useInstitution().data?.timezone ?? 'UTC'
}
