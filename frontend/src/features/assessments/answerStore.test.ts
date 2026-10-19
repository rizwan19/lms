import { vi } from 'vitest'
import { AnswerStore, type SaveFailure } from './answerStore'

const classify = (error: unknown) => error as SaveFailure

test('serializes saves per question and carries the acknowledged revision', async () => {
  const calls: number[] = []
  let release: () => void = () => {}
  const request = vi.fn((_: string, body: { expectedRevision: number }) => {
    calls.push(body.expectedRevision)
    return new Promise<{ revision: number }>((resolve) => {
      release = () => resolve({ revision: body.expectedRevision + 1 })
    })
  })
  const store = new AnswerStore([], request, classify, vi.fn(), 0)
  store.change('q1', { selectedOptionIds: ['a'], writtenText: null }, true)
  store.change('q1', { selectedOptionIds: ['b'], writtenText: null }, true)
  expect(request).toHaveBeenCalledTimes(1)
  release()
  await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(2))
  release()
  await store.flush()
  expect(calls).toEqual([0, 1])
  expect(store.state('q1')).toBe('saved')
  expect(store.value('q1').selectedOptionIds).toEqual(['b'])
})

test('adopts the server answer on a revision conflict', async () => {
  const request = vi.fn().mockRejectedValue({ code: 'ANSWER_REVISION_CONFLICT', current: { questionId: 'q1', selectedOptionIds: [], writtenText: 'from other tab', revision: 3, savedAt: '' } })
  const store = new AnswerStore([], request, classify, vi.fn(), 0)
  store.change('q1', { selectedOptionIds: [], writtenText: 'mine' }, true)
  await store.flush()
  expect(store.state('q1')).toBe('conflict')
  expect(store.value('q1').writtenText).toBe('from other tab')
})

test('reports closed attempts and counts unsaved answers', async () => {
  const onClosed = vi.fn()
  const store = new AnswerStore([], vi.fn().mockRejectedValue({ code: 'ATTEMPT_EXPIRED' }), classify, onClosed, 10_000)
  store.change('q1', { selectedOptionIds: [], writtenText: 'draft' }, false)
  expect(store.unsavedCount()).toBe(1)
  await store.flush()
  expect(onClosed).toHaveBeenCalledWith('ATTEMPT_EXPIRED')
})
