import { useRef } from 'react'

/**
 * Private lesson video. Starts at the saved resume position and reports playback position periodically and
 * on pause; the saved position on the server only ever moves forward.
 */
export function VideoPlayer({ assetId, title, resumeSeconds, onPosition }: { assetId: string; title: string; resumeSeconds: number; onPosition: (seconds: number) => void }) {
  const lastReported = useRef(0)
  const report = (video: HTMLVideoElement, force: boolean) => {
    const seconds = Math.floor(video.currentTime)
    if (force || seconds - lastReported.current >= 15) {
      lastReported.current = seconds
      onPosition(seconds)
    }
  }
  return (
    <video
      className="lesson-video"
      controls
      preload="metadata"
      src={`/api/v1/media/${assetId}/content`}
      aria-label={`Video: ${title}`}
      onLoadedMetadata={(event) => {
        if (resumeSeconds > 0 && resumeSeconds < event.currentTarget.duration) event.currentTarget.currentTime = resumeSeconds
        lastReported.current = resumeSeconds
      }}
      onTimeUpdate={(event) => report(event.currentTarget, false)}
      onPause={(event) => report(event.currentTarget, true)}
    />
  )
}
