import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { UploadField } from './UploadField'

class FakeXhr {
  static last: FakeXhr
  status = 0
  responseText = ''
  upload: { onprogress?: (event: { lengthComputable: boolean; loaded: number; total: number }) => void } = {}
  onload?: () => void
  onerror?: () => void
  onabort?: () => void
  url = ''
  headers: Record<string, string> = {}
  constructor() {
    FakeXhr.last = this
  }
  open(_method: string, url: string) {
    this.url = url
  }
  setRequestHeader(name: string, value: string) {
    this.headers[name] = value
  }
  send() {}
  abort() {
    this.onabort?.()
  }
  respond(status: number, body: unknown) {
    this.status = status
    this.responseText = JSON.stringify(body)
    this.onload?.()
  }
}

beforeEach(() => vi.stubGlobal('XMLHttpRequest', FakeXhr))

const pdf = () => new File(['%PDF-1.4'], 'নোট.pdf', { type: 'application/pdf' })

test('shows progress and the uploaded file', async () => {
  const onUploaded = vi.fn()
  render(<UploadField id="doc" label="Document" purpose="DOCUMENT" onUploaded={onUploaded} />)
  await userEvent.upload(screen.getByLabelText('Document'), pdf())
  expect(FakeXhr.last.url).toContain('purpose=DOCUMENT')
  expect(FakeXhr.last.headers['Content-Type']).toBe('application/pdf')

  act(() => FakeXhr.last.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: 4 }))
  expect(screen.getByRole('progressbar', { name: 'Uploading Document' })).toHaveAttribute('value', '25')

  const asset = { id: 'a1', purpose: 'DOCUMENT', filename: 'নোট.pdf', contentType: 'application/pdf', sizeBytes: 8, durationSeconds: null }
  act(() => FakeXhr.last.respond(201, asset))
  expect(screen.getByRole('status')).toHaveTextContent('Uploaded নোট.pdf')
  expect(onUploaded).toHaveBeenCalledWith(asset)
})

test('shows server rejection and allows cancelling', async () => {
  render(<UploadField id="doc" label="Document" purpose="DOCUMENT" onUploaded={vi.fn()} />)
  await userEvent.upload(screen.getByLabelText('Document'), pdf())
  await userEvent.click(screen.getByRole('button', { name: 'Cancel upload' }))
  expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()

  await userEvent.upload(screen.getByLabelText('Document'), pdf())
  act(() => FakeXhr.last.respond(413, { code: 'FILE_TOO_LARGE', detail: 'File exceeds the 25 MiB limit' }))
  expect(screen.getByRole('alert')).toHaveTextContent('File exceeds the 25 MiB limit')
})
