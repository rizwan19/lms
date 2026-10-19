export type ProgressRow = {
  enrollmentId: string
  displayName: string
  username: string
  enrollmentStatus: string
  requiredLessons: number
  completedRequiredLessons: number
  lessonPercent: number | null
  requiredExams: number
  passedRequiredExams: number
  pendingGrading: number
  lastActivityAt: string | null
  inactive: boolean
  completionStatus: string
}

export type ScoreRow = {
  enrollmentId: string
  displayName: string
  username: string
  scores: { assignmentId: string; examTitle: string; required: boolean; bestEarned: number | null; maximum: number | null; passed: boolean | null; pendingGrading: boolean }[]
}

export type ExportView = { id: string; type: 'PROGRESS' | 'SCORES'; status: 'PENDING' | 'READY' | 'FAILED'; createdAt: string }
