import type { FieldErrors } from '../../shared/api'
import { AssetName } from '../materials/AssetName'
import { UploadField } from '../materials/UploadField'
import type { LessonDraft, VideoKind } from './draftState'
import { RichTextEditor } from './RichTextEditor'

const VIDEO_KINDS: { value: VideoKind; label: string }[] = [
  { value: 'none', label: 'No video' },
  { value: 'upload', label: 'Uploaded video' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'VIMEO', label: 'Vimeo' },
]

type Props = {
  name: string
  lesson: LessonDraft
  errors: FieldErrors
  onChange: (patch: Partial<LessonDraft>) => void
  onMove: (offset: -1 | 1) => void
  onRemove: () => void
}

/** Edits one lesson; {@code errors} are keyed relative to the lesson (e.g. "title", "videoRef"). */
export function LessonEditor({ name, lesson, errors, onChange, onMove, onRemove }: Props) {
  const id = (field: string) => `${lesson.key}-${field}`
  const error = (field: string) => errors[field] && <small className="error">{errors[field]}</small>

  return (
    <fieldset className="lesson-editor">
      <legend>{name}</legend>
      <div className="field">
        <label htmlFor={id('title')}>Lesson title</label>
        <input id={id('title')} value={lesson.title} maxLength={200} onChange={(event) => onChange({ title: event.target.value })} />
        {error('title')}
      </div>
      <label className="inline">
        <input type="checkbox" checked={lesson.required} onChange={(event) => onChange({ required: event.target.checked })} /> Required for completion
      </label>
      <RichTextEditor id={id('body')} label="Lesson text" value={lesson.bodyHtml} onChange={(bodyHtml) => onChange({ bodyHtml })} />
      {error('bodyHtml')}

      <div className="field">
        <label htmlFor={id('video')}>Video</label>
        <select id={id('video')} value={lesson.videoKind} onChange={(event) => onChange({ videoKind: event.target.value as VideoKind })}>
          {VIDEO_KINDS.map((kind) => (
            <option key={kind.value} value={kind.value}>
              {kind.label}
            </option>
          ))}
        </select>
        {error('video')}
      </div>
      {lesson.videoKind === 'upload' && (
        <>
          {lesson.videoAssetId && (
            <p>
              Current video: <AssetName id={lesson.videoAssetId} />
            </p>
          )}
          <UploadField id={id('video-file')} label="Upload MP4 video" purpose="VIDEO" onUploaded={(asset) => onChange({ videoAssetId: asset.id })} />
          {error('videoAssetId')}
        </>
      )}
      {(lesson.videoKind === 'YOUTUBE' || lesson.videoKind === 'VIMEO') && (
        <div className="field">
          <label htmlFor={id('ref')}>{lesson.videoKind === 'YOUTUBE' ? 'YouTube video ID' : 'Vimeo video ID'}</label>
          <input id={id('ref')} value={lesson.videoRef} onChange={(event) => onChange({ videoRef: event.target.value })} />
          <small className="muted">Only the ID, e.g. dQw4w9WgXcQ — not the full link.</small>
          {error('videoRef')}
        </div>
      )}

      <div className="field">
        <span className="label">Documents</span>
        {lesson.resourceAssetIds.length > 0 && (
          <ul>
            {lesson.resourceAssetIds.map((assetId) => (
              <li key={assetId}>
                <AssetName id={assetId} />{' '}
                <button type="button" onClick={() => onChange({ resourceAssetIds: lesson.resourceAssetIds.filter((other) => other !== assetId) })}>
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
        <UploadField
          id={id('document')}
          label="Add PDF or text document"
          purpose="DOCUMENT"
          onUploaded={(asset) => onChange({ resourceAssetIds: [...lesson.resourceAssetIds, asset.id] })}
        />
        {error('resourceAssetIds')}
      </div>

      <div className="actions">
        <button type="button" onClick={() => onMove(-1)}>
          Move lesson up
        </button>
        <button type="button" onClick={() => onMove(1)}>
          Move lesson down
        </button>
        <button type="button" className="danger" onClick={onRemove}>
          Remove lesson
        </button>
      </div>
    </fieldset>
  )
}
