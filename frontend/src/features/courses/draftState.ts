import type { Draft } from './types'

/** Editable draft held in memory until saved; keys give React stable identities for new rows. */
export type VideoKind = 'none' | 'upload' | 'YOUTUBE' | 'VIMEO'

export type LessonDraft = {
  key: string
  id: string | null
  title: string
  bodyHtml: string
  videoKind: VideoKind
  videoAssetId: string | null
  videoRef: string
  resourceAssetIds: string[]
  required: boolean
}

export type SectionDraft = { key: string; id: string | null; title: string; lessons: LessonDraft[] }

export type DraftState = { title: string; description: string; sections: SectionDraft[] }

let nextKey = 0
const newKey = () => `new-${nextKey++}`

export function newLesson(): LessonDraft {
  return { key: newKey(), id: null, title: '', bodyHtml: '', videoKind: 'none', videoAssetId: null, videoRef: '', resourceAssetIds: [], required: true }
}

export function newSection(): SectionDraft {
  return { key: newKey(), id: null, title: '', lessons: [newLesson()] }
}

export function fromServer(draft: Draft): DraftState {
  return {
    title: draft.title,
    description: draft.description,
    sections: draft.sections.map((section) => ({
      key: section.id,
      id: section.id,
      title: section.title,
      lessons: section.lessons.map((lesson) => ({
        key: lesson.id,
        id: lesson.id,
        title: lesson.title,
        bodyHtml: lesson.bodyHtml,
        videoKind: lesson.video?.assetId ? 'upload' : (lesson.video?.provider ?? 'none'),
        videoAssetId: lesson.video?.assetId ?? null,
        videoRef: lesson.video?.providerRef ?? '',
        resourceAssetIds: lesson.resourceAssetIds,
        required: lesson.required,
      })),
    })),
  }
}

export function toPayload(state: DraftState, expectedRevision: number) {
  return {
    expectedRevision,
    title: state.title,
    description: state.description,
    sections: state.sections.map((section) => ({
      id: section.id,
      title: section.title,
      lessons: section.lessons.map((lesson) => ({
        id: lesson.id,
        title: lesson.title,
        bodyHtml: lesson.bodyHtml,
        videoAssetId: lesson.videoKind === 'upload' ? lesson.videoAssetId : null,
        videoProvider: lesson.videoKind === 'YOUTUBE' || lesson.videoKind === 'VIMEO' ? lesson.videoKind : null,
        videoRef: lesson.videoKind === 'YOUTUBE' || lesson.videoKind === 'VIMEO' ? lesson.videoRef.trim() : null,
        resourceAssetIds: lesson.resourceAssetIds,
        required: lesson.required,
      })),
    })),
  }
}

/** Returns a copy of {@code items} with the element at {@code index} moved by {@code offset}, if possible. */
export function move<T>(items: T[], index: number, offset: -1 | 1): T[] {
  const target = index + offset
  if (target < 0 || target >= items.length) return items
  const copy = [...items]
  ;[copy[index], copy[target]] = [copy[target], copy[index]]
  return copy
}
