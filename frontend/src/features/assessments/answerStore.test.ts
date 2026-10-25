import { vi } from 'vitest'
import { AnswerStore, type AnswerValue, type SaveFailure } from './answerStore'

const classify = (error: unknown) => error as SaveFailure

test('serializes saves per question and sends the latest value last', async () => {
  const calls: string[][] = []
  let release: () => void = () => {}
  const request = vi.fn((_: string, body: AnswerValue) => {
    calls.push(body.selectedOptionIds)
    return new Promise<void>((resolve) => {
      release = () => resolve()
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
  expect(calls).toEqual([['a'], ['b']])
  expect(store.state('q1')).toBe('saved')
  expect(store.value('q1').selectedOptionIds).toEqual(['b'])
})

test('reports closed attempts and counts unsaved answers', async () => {
  const onClosed = vi.fn()
  const store = new AnswerStore([], vi.fn().mockRejectedValue({ code: 'ATTEMPT_EXPIRED' }), classify, onClosed, 10_000)
  store.change('q1', { selectedOptionIds: [], writtenText: 'draft' }, false)
  expect(store.unsavedCount()).toBe(1)
  await store.flush()
  expect(onClosed).toHaveBeenCalledWith('ATTEMPT_EXPIRED')
})
