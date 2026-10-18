import { useRef, useState } from 'react'
import { ApiError } from '../../shared/api'

export type AssetPurpose = 'VIDEO' | 'DOCUMENT' | 'BRANDING_IMAGE'
export type Asset = { id: string; purpose: AssetPurpose; filename: string; contentType: string; sizeBytes: number; durationSeconds: number | null }

const ACCEPT: Record<AssetPurpose, string> = {
  VIDEO: 'video/mp4,.mp4',
  DOCUMENT: 'application/pdf,.pdf,text/plain,.txt',
  BRANDING_IMAGE: 'image/png,.png,image/jpeg,.jpg,.jpeg',
}

type State = { kind: 'idle' } | { kind: 'uploading'; percent: number } | { kind: 'error'; message: string } | { kind: 'done'; asset: Asset }

function csrfToken(): string {
  const cookie = document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : ''
}

/** Streams one file to POST /media with progress and cancellation; XHR is used for upload progress events. */
export function UploadField({ id, label, purpose, onUploaded }: { id: string; label: string; purpose: AssetPurpose; onUploaded: (asset: Asset) => void }) {
  const [state, setState] = useState<State>({ kind: 'idle' })
  const request = useRef<XMLHttpRequest | null>(null)

  function upload(file: File) {
    const xhr = new XMLHttpRequest()
    request.current = xhr
    xhr.open('POST', `/api/v1/media?purpose=${purpose}&filename=${encodeURIComponent(file.name)}`)
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
    xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken())
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) setState({ kind: 'uploading', percent: Math.round((event.loaded / event.total) * 100) })
    }
    xhr.onload = () => {
      const body = xhr.responseText ? JSON.parse(xhr.responseText) : {}
      if (xhr.status === 201) {
        setState({ kind: 'done', asset: body as Asset })
        onUploaded(body as Asset)
      } else {
        setState({ kind: 'error', message: new ApiError(xhr.status, body.code ?? '', body.detail ?? 'Upload failed').message })
      }
    }
    xhr.onerror = () => setState({ kind: 'error', message: 'The upload was interrupted. Check your connection and try again.' })
    xhr.onabort = () => setState({ kind: 'idle' })
    setState({ kind: 'uploading', percent: 0 })
    xhr.send(file)
  }

  return (
    <div className="field upload-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="file"
        accept={ACCEPT[purpose]}
        disabled={state.kind === 'uploading'}
        onChange={(event) => {
          const file = event.target.files?.[0]
          if (file) upload(file)
          event.target.value = ''
        }}
      />
      {state.kind === 'uploading' && (
        <div className="actions">
          <progress max={100} value={state.percent} aria-label={`Uploading ${label}`}>
            {state.percent}%
          </progress>
          <span>{state.percent}%</span>
          <button type="button" onClick={() => request.current?.abort()}>
            Cancel upload
          </button>
        </div>
      )}
      {state.kind === 'error' && (
        <small className="error" role="alert">
          {state.message}
        </small>
      )}
      {state.kind === 'done' && <small role="status">Uploaded {state.asset.filename}</small>}
    </div>
  )
}
