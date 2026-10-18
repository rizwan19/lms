import type { LessonSummary } from './types'

/** Required-lesson progress; with no required lessons we say so instead of claiming 100%. */
export function ProgressBar({ summary }: { summary: LessonSummary }) {
  if (summary.required === 0) return <p className="muted">No required lessons</p>
  const percent = Math.round((summary.completed / summary.required) * 100)
  return (
    <div className="progress">
      <progress max={summary.required} value={summary.completed} aria-label="Required lessons completed" />
      <span>
        {summary.completed} of {summary.required} required lessons ({percent}%)
      </span>
    </div>
  )
}
