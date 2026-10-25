import type { Role, TeacherStatus, TemporaryCredential } from '../../shared/types'

export type Account = {
  id: string
  username: string
  displayName: string
  email: string
  role: Role
  status: 'ACTIVE' | 'SUSPENDED'
  emailVerified: boolean
  teacherStatus: TeacherStatus | null
  createdAt: string
  revision: number
}

export type CreatedAccount = { account: Account; temporaryCredential: TemporaryCredential | null; invitationExpiresAt: string | null }
