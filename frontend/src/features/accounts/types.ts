import type { Role, TemporaryCredential } from '../../shared/types'

export type Account = {
  id: string
  username: string
  displayName: string
  email: string | null
  roles: Role[]
  status: 'ACTIVE' | 'DISABLED'
  createdAt: string
  revision: number
}

export type CreatedAccount = { account: Account; temporaryCredential: TemporaryCredential | null; invitationExpiresAt: string | null }
