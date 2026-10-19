import type { FieldErrors } from '../../shared/api'
import { markCorrect, newOption, optionsFor, type QuestionDraft } from './examDraft'
import { QUESTION_TYPE_LABELS, type QuestionType } from './types'

type Props = {
  name: string
  question: QuestionDraft
  errors: FieldErrors
  onChange: (patch: Partial<QuestionDraft>) => void
  onMove: (offset: -1 | 1) => void
  onRemove: () => void
}

/** One question with its answer key; prompts are plain text. */
export function QuestionEditor({ name, question, errors, onChange, onMove, onRemove }: Props) {
  const id = (field: string) => `${question.key}-${field}`
  const error = (field: string) => errors[field] && <small className="error">{errors[field]}</small>
  const exclusive = question.type === 'SINGLE_CHOICE' || question.type === 'TRUE_FALSE'

  return (
    <fieldset className="lesson-editor">
      <legend>{name}</legend>
      <div className="inline-form">
        <div className="field">
          <label htmlFor={id('type')}>Question type</label>
          <select id={id('type')} value={question.type} onChange={(event) => onChange({ type: event.target.value as QuestionType, options: optionsFor(event.target.value as QuestionType) })}>
            {Object.entries(QUESTION_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={id('points')}>Points</label>
          <input id={id('points')} inputMode="decimal" value={question.points} onChange={(event) => onChange({ points: event.target.value })} />
          {error('points')}
        </div>
      </div>
      <div className="field">
        <label htmlFor={id('prompt')}>Question</label>
        <textarea id={id('prompt')} value={question.prompt} maxLength={10000} onChange={(event) => onChange({ prompt: event.target.value })} />
        {error('prompt')}
      </div>
      {question.type !== 'WRITTEN' && (
        <fieldset>
          <legend>Answers ({exclusive ? 'choose the correct one' : 'tick every correct one'})</legend>
          {question.options.map((option, index) => (
            <div key={option.key} className="actions">
              <input
                type={exclusive ? 'radio' : 'checkbox'}
                name={id('correct')}
                checked={option.correct}
                aria-label={`Answer ${index + 1} is correct`}
                onChange={(event) => onChange({ options: markCorrect(question, index, event.target.checked) })}
              />
              <input
                aria-label={`Answer ${index + 1}`}
                value={option.label}
                readOnly={question.type === 'TRUE_FALSE'}
                maxLength={1000}
                onChange={(event) => onChange({ options: question.options.map((other, i) => (i === index ? { ...other, label: event.target.value } : other)) })}
              />
              {question.type !== 'TRUE_FALSE' && question.options.length > 2 && (
                <button type="button" onClick={() => onChange({ options: question.options.filter((_, i) => i !== index) })}>
                  Remove answer {index + 1}
                </button>
              )}
            </div>
          ))}
          {question.type !== 'TRUE_FALSE' && question.options.length < 10 && (
            <button type="button" onClick={() => onChange({ options: [...question.options, newOption()] })}>
              Add answer
            </button>
          )}
          {error('options')}
        </fieldset>
      )}
      {question.type === 'WRITTEN' && (
        <div className="field">
          <label htmlFor={id('notes')}>Grading notes (teachers only)</label>
          <textarea id={id('notes')} value={question.gradingNotes} maxLength={10000} onChange={(event) => onChange({ gradingNotes: event.target.value })} />
        </div>
      )}
      <div className="field">
        <label htmlFor={id('explanation')}>Explanation shown with released answers</label>
        <textarea id={id('explanation')} value={question.explanation} maxLength={10000} onChange={(event) => onChange({ explanation: event.target.value })} />
      </div>
      <div className="actions">
        <button type="button" onClick={() => onMove(-1)}>
          Move question up
        </button>
        <button type="button" onClick={() => onMove(1)}>
          Move question down
        </button>
        <button type="button" className="danger" onClick={onRemove}>
          Remove question
        </button>
      </div>
    </fieldset>
  )
}
