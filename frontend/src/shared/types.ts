export type Role = 'ADMINISTRATOR' | 'TEACHER' | 'STUDENT'

export type Me = {
  id: string
  username: string
  displayName: string
  email: string | null
  roles: Role[]
  passwordChangeRequired: boolean
}

export type Institution = {
  name: string
  timezone: string
  logoAssetId: string | null
  certificateVerification: boolean
  verificationShowsStudent: boolean
  revision: number
}

export type TemporaryCredential = { username: string; temporaryPassword: string; expiresAt: string }
