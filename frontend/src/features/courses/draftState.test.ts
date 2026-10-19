import { fromServer, move, newSection, toPayload } from './draftState'
import type { Draft } from './types'

const draft: Draft = {
  versionId: 'v1',
  courseId: 'c1',
  revision: 3,
  title: 'পদার্থবিজ্ঞান',
  description: '',
  requiredExamVersionIds: ['x1'],
  sections: [
    {
      id: 's1',
      title: 'Motion',
      lessons: [
        { id: 'l1', title: 'Video', bodyHtml: '<p>x</p>', video: { assetId: 'a1', provider: null, providerRef: null }, resourceAssetIds: ['d1'], required: true },
        { id: 'l2', title: 'Embed', bodyHtml: '', video: { assetId: null, provider: 'YOUTUBE', providerRef: 'dQw4w9WgXcQ' }, resourceAssetIds: [], required: false },
      ],
    },
  ],
}

test('round-trips server drafts into the save payload', () => {
  const payload = toPayload(fromServer(draft), draft.revision)
  expect(payload.expectedRevision).toBe(3)
  expect(payload.sections[0].lessons[0]).toMatchObject({ id: 'l1', videoAssetId: 'a1', videoProvider: null, videoRef: null, resourceAssetIds: ['d1'] })
  expect(payload.sections[0].lessons[1]).toMatchObject({ id: 'l2', videoAssetId: null, videoProvider: 'YOUTUBE', videoRef: 'dQw4w9WgXcQ', required: false })
})

test('new rows have no server id and switching video kind drops the other video', () => {
  const state = fromServer(draft)
  state.sections.push(newSection())
  state.sections[0].lessons[0].videoKind = 'none'
  const payload = toPayload(state, 3)
  expect(payload.sections[1].id).toBeNull()
  expect(payload.sections[1].lessons).toHaveLength(1)
  expect(payload.sections[0].lessons[0].videoAssetId).toBeNull()
})

test('moves items within bounds only', () => {
  expect(move(['a', 'b', 'c'], 0, 1)).toEqual(['b', 'a', 'c'])
  expect(move(['a', 'b'], 0, -1)).toEqual(['a', 'b'])
})
