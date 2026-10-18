import type { CourseSection } from '../courses/types'

export type LessonSummary = { required: number; completed: number; lastActivityAt: string | null }

export type DashboardEntry = {
  enrollmentId: string
  className: string
  courseTitle: string
  edition: number
  enrollmentStatus: 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'
  classStatus: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  accessOpen: boolean
  startsAt: string | null
  endsAt: string | null
  summary: LessonSummary
}

export type LessonProgress = { lessonId: string; completed: boolean; completedAt: string | null; resumeSeconds: number; revision: number; lastActivityAt: string }

export type Player = {
  enrollmentId: string
  className: string
  course: { versionId: string; title: string; description: string; edition: number; sections: CourseSection[] }
  progress: LessonProgress[]
  summary: LessonSummary
}
