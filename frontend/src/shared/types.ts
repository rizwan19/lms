export type Role = 'TEACHER' | 'STUDENT'

export type TeacherStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type Profile = {
  id: string
  username: string
  displayName: string
  email: string
  role: Role
  emailVerified: boolean
  teacherStatus: TeacherStatus | null
  capabilities: { canAdministerWorkspace: boolean; canPublishCourses: boolean; canLearn: boolean }
}

export type WorkspaceState = 'REQUIRED' | 'OWNER_VERIFICATION_PENDING' | 'ACTIVE'

export type Workspace = {
  name: string
  timezone: string
  state: WorkspaceState
  logoAssetId: string | null
  certificateVerification: boolean
  verificationShowsStudent: boolean
  revision: number
}

export type RegistrationReceipt = { userId: string; nextAction: 'VERIFY_EMAIL' }

export type TemporaryCredential = { username: string; temporaryPassword: string; expiresAt: string }
