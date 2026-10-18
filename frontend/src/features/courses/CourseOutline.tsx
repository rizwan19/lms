import type { ReactNode } from 'react'
import type { CourseSection } from './types'

/** Section/lesson navigation for preview and the student player. {@code marker} renders per-lesson status. */
export function CourseOutline({
  sections,
  currentId,
  onSelect,
  marker,
}: {
  sections: CourseSection[]
  currentId?: string
  onSelect: (lessonId: string) => void
  marker?: (lessonId: string) => ReactNode
}) {
  return (
    <nav className="outline" aria-label="Lessons">
      {sections.map((section) => (
        <div key={section.id}>
          <h3>{section.title}</h3>
          <ol>
            {section.lessons.map((lesson) => (
              <li key={lesson.id}>
                <button type="button" aria-current={lesson.id === currentId ? 'page' : undefined} onClick={() => onSelect(lesson.id)}>
                  {marker?.(lesson.id)} {lesson.title}
                  {!lesson.required && <small className="muted"> (optional)</small>}
                </button>
              </li>
            ))}
          </ol>
        </div>
      ))}
    </nav>
  )
}
