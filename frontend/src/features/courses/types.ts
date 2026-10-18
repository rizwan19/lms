export type VideoProvider = 'YOUTUBE' | 'VIMEO'
export type LessonVideo = { assetId: string | null; provider: VideoProvider | null; providerRef: string | null }
export type CourseLesson = { id: string; title: string; bodyHtml: string; video: LessonVideo | null; resourceAssetIds: string[]; required: boolean }
export type CourseSection = { id: string; title: string; lessons: CourseLesson[] }

export type CourseSummary = {
  id: string
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  latestEdition: number | null
  draftRevision: number | null
  updatedAt: string
}

export type Edition = { versionId: string; edition: number; title: string; publishedAt: string }

export type CourseDetail = {
  id: string
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  teachers: { id: string; displayName: string }[]
  editions: Edition[]
  draft: { versionId: string; revision: number; basedOnEdition: number | null } | null
}

export type Draft = { versionId: string; courseId: string; revision: number; title: string; description: string; sections: CourseSection[] }

export type CoursePreview = { versionId: string; courseId: string; edition: number | null; title: string; description: string; sections: CourseSection[] }

export type PublishedEdition = { versionId: string; courseId: string; edition: number; title: string; sections: CourseSection[] }
