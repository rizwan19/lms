import { useQuery } from '@tanstack/react-query'
import { api } from '../../shared/api'
import type { Asset } from './UploadField'

/** Shows an asset's filename; the metadata request is authorized like the file itself. */
export function AssetName({ id }: { id: string }) {
  const asset = useQuery({ queryKey: ['media', id], queryFn: () => api.get<Asset>(`/media/${id}`), staleTime: Infinity })
  return <>{asset.data?.filename ?? (asset.isError ? 'Unavailable file' : '…')}</>
}
