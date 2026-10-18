import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { Asset } from '../materials/UploadField'
import { api } from '../../shared/api'
import { ErrorMessage, Spinner } from '../../shared/ui'

/**
 * Lesson documents. PDFs open in an embedded viewer (served with a restrictive CSP); text files are fetched and
 * shown as escaped text, never as HTML.
 */
export function DocumentViewer({ assetId }: { assetId: string }) {
  const [open, setOpen] = useState(false)
  const asset = useQuery({ queryKey: ['media', assetId], queryFn: () => api.get<Asset>(`/media/${assetId}`), staleTime: Infinity })
  if (!asset.data) return <span>{asset.isError ? 'Unavailable file' : '…'}</span>
  const url = `/api/v1/media/${assetId}/content`
  return (
    <div className="document">
      <button type="button" aria-expanded={open} onClick={() => setOpen(!open)}>
        {open ? 'Hide' : 'Open'} {asset.data.filename}
      </button>{' '}
      <a href={`${url}?download=true`}>Download</a>
      {open && (asset.data.contentType === 'text/plain' ? <TextDocument url={url} /> : <iframe className="pdf" src={url} title={asset.data.filename} />)}
    </div>
  )
}

function TextDocument({ url }: { url: string }) {
  const text = useQuery({
    queryKey: ['document-text', url],
    queryFn: async () => {
      const response = await fetch(url, { credentials: 'same-origin' })
      if (!response.ok) throw new Error('Could not load the document')
      return response.text()
    },
  })
  if (text.isPending) return <Spinner />
  if (text.error) return <ErrorMessage error={text.error} />
  return <pre className="text-document">{text.data}</pre>
}
