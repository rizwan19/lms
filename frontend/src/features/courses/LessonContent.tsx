import type { ReactNode } from 'react'
import { AssetName } from '../materials/AssetName'
import type { CourseLesson, LessonVideo } from './types'

const EMBED_URLS = {
  YOUTUBE: (id: string) => `https://www.youtube-nocookie.com/embed/${id}`,
  VIMEO: (id: string) => `https://player.vimeo.com/video/${id}`,
}

/** Renders one lesson as students see it. Lesson HTML was sanitized by the server's allowlist. */
export function LessonContent({ lesson, video }: { lesson: CourseLesson; video?: (video: LessonVideo) => ReactNode }) {
  return (
    <article className="lesson">
      <h2>{lesson.title}</h2>
      {lesson.video && (video ? video(lesson.video) : <DefaultVideo video={lesson.video} title={lesson.title} />)}
      {lesson.bodyHtml && <div className="lesson-body" dangerouslySetInnerHTML={{ __html: lesson.bodyHtml }} />}
      {lesson.resourceAssetIds.length > 0 && (
        <section aria-label="Documents">
          <h3>Documents</h3>
          <ul>
            {lesson.resourceAssetIds.map((assetId) => (
              <li key={assetId}>
                <a href={`/api/v1/media/${assetId}/content`} target="_blank" rel="noopener noreferrer">
                  <AssetName id={assetId} />
                </a>{' '}
                (<a href={`/api/v1/media/${assetId}/content?download=true`}>download</a>)
              </li>
            ))}
          </ul>
        </section>
      )}
    </article>
  )
}

function DefaultVideo({ video, title }: { video: LessonVideo; title: string }) {
  if (video.assetId) {
    return <video className="lesson-video" controls preload="metadata" src={`/api/v1/media/${video.assetId}/content`} aria-label={`Video: ${title}`} />
  }
  return <ExternalVideo video={video} title={title} />
}

export function ExternalVideo({ video, title }: { video: LessonVideo; title: string }) {
  if (!video.provider || !video.providerRef) return null
  return (
    <div className="embed">
      <iframe
        src={EMBED_URLS[video.provider](video.providerRef)}
        title={`Video: ${title}`}
        allow="encrypted-media; picture-in-picture; fullscreen"
        referrerPolicy="strict-origin-when-cross-origin"
        sandbox="allow-scripts allow-same-origin allow-presentation"
      />
      <small className="muted">External videos are hosted by {video.provider === 'YOUTUBE' ? 'YouTube' : 'Vimeo'} and are not private.</small>
    </div>
  )
}
