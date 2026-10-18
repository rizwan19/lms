import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { api, query } from '../../shared/api'
import { QueryState } from '../../shared/ui'
import { CourseOutline } from './CourseOutline'
import { LessonContent } from './LessonContent'
import type { CoursePreview } from './types'

export function CoursePreviewPage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const versionId = params.get('versionId')
  const preview = useQuery({
    queryKey: ['course', id, 'preview', versionId],
    queryFn: () => api.get<CoursePreview>(`/courses/${id}/preview${query({ versionId })}`),
  })
  const [selected, setSelected] = useState<string | null>(null)

  return (
    <QueryState query={preview}>
      {(course) => {
        const lessons = course.sections.flatMap((section) => section.lessons)
        const lesson = lessons.find((candidate) => candidate.id === selected) ?? lessons[0]
        return (
          <section>
            <div className="page-header">
              <h1>
                {course.title} <small className="muted">{course.edition ? `Edition ${course.edition}` : 'Draft preview'}</small>
              </h1>
              <Link to={`/teacher/courses/${id}/edit`}>Back to editor</Link>
            </div>
            {course.description && <p>{course.description}</p>}
            <div className="player">
              <CourseOutline sections={course.sections} currentId={lesson?.id} onSelect={setSelected} />
              <div>{lesson ? <LessonContent lesson={lesson} /> : <p className="empty">This edition has no lessons yet.</p>}</div>
            </div>
          </section>
        )
      }}
    </QueryState>
  )
}
