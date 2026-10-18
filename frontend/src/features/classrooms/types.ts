export type ClassStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
export type EnrollmentStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'

export type ClassSummary = { id: string; name: string; status: ClassStatus; courseTitle: string; edition: number; startsAt: string | null; endsAt: string | null }

export type ClassDetail = ClassSummary & {
  courseId: string
  courseVersionId: string
  teachers: { id: string; displayName: string }[]
  revision: number
}

export type RosterRow = { enrollmentId: string; studentId: string; username: string; displayName: string; status: EnrollmentStatus; enrolledAt: string }
