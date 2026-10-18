import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError } from '../../shared/api'
import { ErrorMessage, Spinner } from '../../shared/ui'
import { CourseOutline } from '../courses/CourseOutline'
import { ExternalVideo, LessonContent } from '../courses/LessonContent'
import { DocumentViewer } from './DocumentViewer'
import { ProgressBar } from './ProgressBar'
import type { Player } from './types'
import { useProgressSaver } from './useProgressSaver'
import { VideoPlayer } from './VideoPlayer'

export function CoursePlayerPage() {
  const { id = '' } = useParams()
  const player = useQuery({ queryKey: ['player', id], queryFn: () => api.get<Player>(`/me/enrollments/${id}`), retry: false })
  const progress = useProgressSaver(id)
  const [selected, setSelected] = useState<string | null>(null)

  if (player.isPending) return <Spinner />
  if (player.error) {
    return (
      <section>
        {player.error instanceof ApiError && player.error.status === 403 ? (
          <p className="notice">{player.error.message}</p>
        ) : (
          <ErrorMessage error={player.error} />
        )}
        <Link to="/student">Back to my learning</Link>
      </section>
    )
  }

  const { course } = player.data
  const lessons = course.sections.flatMap((section) => section.lessons)
  const firstOpen = lessons.find((lesson) => !player.data.progress.some((record) => record.lessonId === lesson.id && record.completed))
  const lesson = lessons.find((candidate) => candidate.id === selected) ?? firstOpen ?? lessons[0]
  const record = player.data.progress.find((candidate) => candidate.lessonId === lesson?.id)
  const done = (lessonId: string) => player.data.progress.some((candidate) => candidate.lessonId === lessonId && candidate.completed)
  const required = lessons.filter((candidate) => candidate.required)
  const summary = { ...player.data.summary, required: required.length, completed: required.filter((candidate) => done(candidate.id)).length }

  return (
    <section>
      <div className="page-header">
        <h1>{course.title}</h1>
        <Link to="/student">My learning</Link>
      </div>
      <ProgressBar summary={summary} />
      <div className="player">
        <CourseOutline
          sections={course.sections}
          currentId={lesson?.id}
          onSelect={setSelected}
          marker={(lessonId) => <span aria-label={done(lessonId) ? 'Completed' : 'Not completed'}>{done(lessonId) ? '✓' : '○'}</span>}
        />
        {lesson && (
          <div>
            <LessonContent
              key={lesson.id}
              lesson={{ ...lesson, resourceAssetIds: [] }}
              video={(video) =>
                video.assetId ? (
                  <VideoPlayer
                    assetId={video.assetId}
                    title={lesson.title}
                    resumeSeconds={record?.resumeSeconds ?? 0}
                    onPosition={(seconds) => progress.save(lesson.id, { resumeSeconds: seconds })}
                  />
                ) : (
                  <ExternalVideo video={video} title={lesson.title} />
                )
              }
            />
            {lesson.resourceAssetIds.length > 0 && (
              <section className="card" aria-label="Documents">
                <h3>Documents</h3>
                {lesson.resourceAssetIds.map((assetId) => (
                  <DocumentViewer key={assetId} assetId={assetId} />
                ))}
              </section>
            )}
            <div className="actions">
              <button
                type="button"
                className={record?.completed ? undefined : 'primary'}
                disabled={progress.pending}
                onClick={() => {
                  setSelected(lesson.id)
                  progress.save(lesson.id, { completed: !record?.completed })
                }}
              >
                {record?.completed ? 'Mark as not completed' : 'Mark as completed'}
              </button>
              {record?.completed && <span role="status">Lesson completed.</span>}
            </div>
            <ErrorMessage error={progress.error} />
          </div>
        )}
      </div>
    </section>
  )
}
