import type { AuthorQuestion, ExamContent, QuestionType } from './types'

/** Editable exam draft; option and question keys give React stable identities for unsaved rows. */
export type OptionDraft = { key: string; id: string | null; label: string; correct: boolean }
export type QuestionDraft = { key: string; id: string | null; type: QuestionType; prompt: string; points: string; explanation: string; gradingNotes: string; options: OptionDraft[] }
export type ExamDraftState = { title: string; instructions: string; passPercent: string; questions: QuestionDraft[] }

let nextKey = 0
const newKey = () => `new-${nextKey++}`

export function optionsFor(type: QuestionType): OptionDraft[] {
  if (type === 'WRITTEN') return []
  if (type === 'TRUE_FALSE')
    return [
      { key: newKey(), id: null, label: 'True', correct: true },
      { key: newKey(), id: null, label: 'False', correct: false },
    ]
  return [
    { key: newKey(), id: null, label: '', correct: true },
    { key: newKey(), id: null, label: '', correct: false },
  ]
}

export function newQuestion(type: QuestionType = 'SINGLE_CHOICE'): QuestionDraft {
  return { key: newKey(), id: null, type, prompt: '', points: '1', explanation: '', gradingNotes: '', options: optionsFor(type) }
}

export function newOption(): OptionDraft {
  return { key: newKey(), id: null, label: '', correct: false }
}

export function fromContent(content: ExamContent): ExamDraftState {
  return {
    title: content.title,
    instructions: content.instructions,
    passPercent: String(content.passPercent),
    questions: content.questions.map((question: AuthorQuestion) => ({
      key: question.id,
      id: question.id,
      type: question.type,
      prompt: question.prompt,
      points: String(question.points),
      explanation: question.explanation,
      gradingNotes: question.gradingNotes,
      options: question.options.map((option) => ({ key: option.id, id: option.id, label: option.label, correct: option.correct })),
    })),
  }
}

export function toExamPayload(state: ExamDraftState, expectedRevision: number) {
  return {
    expectedRevision,
    title: state.title,
    instructions: state.instructions,
    passPercent: state.passPercent,
    questions: state.questions.map((question) => ({
      id: question.id,
      type: question.type,
      prompt: question.prompt,
      points: question.points,
      explanation: question.explanation,
      gradingNotes: question.gradingNotes,
      options: question.options.map((option) => ({ id: option.id, label: option.label, correct: option.correct })),
    })),
  }
}

/** Single choice and true/false keep exactly one correct option. */
export function markCorrect(question: QuestionDraft, index: number, correct: boolean): OptionDraft[] {
  const exclusive = question.type === 'SINGLE_CHOICE' || question.type === 'TRUE_FALSE'
  return question.options.map((option, i) => (i === index ? { ...option, correct } : exclusive && correct ? { ...option, correct: false } : option))
}
