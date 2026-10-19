import { fromContent, markCorrect, newQuestion, toExamPayload } from './examDraft'

test('single choice keeps one correct option, multiple select allows several', () => {
  const single = { ...newQuestion('SINGLE_CHOICE') }
  single.options = [...single.options, { key: 'x', id: null, label: 'C', correct: false }]
  expect(markCorrect(single, 2, true).map((option) => option.correct)).toEqual([false, false, true])
  const multiple = { ...newQuestion('MULTIPLE_SELECT') }
  expect(markCorrect(multiple, 1, true).map((option) => option.correct)).toEqual([true, true])
})

test('true/false and written questions get fixed option shapes', () => {
  expect(newQuestion('TRUE_FALSE').options.map((option) => option.label)).toEqual(['True', 'False'])
  expect(newQuestion('WRITTEN').options).toEqual([])
})

test('round-trips content into the save payload', () => {
  const state = fromContent({
    versionId: 'v',
    examId: 'e',
    edition: null,
    revision: 4,
    title: 'Quiz',
    instructions: '',
    passPercent: 60,
    questions: [{ id: 'q1', type: 'WRITTEN', prompt: 'Why?', points: 2.5, explanation: '', gradingNotes: 'Mentions gravity', options: [] }],
  })
  expect(toExamPayload(state, 4)).toMatchObject({ expectedRevision: 4, passPercent: '60', questions: [{ id: 'q1', points: '2.5', gradingNotes: 'Mentions gravity' }] })
})
