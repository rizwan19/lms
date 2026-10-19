import type { SavedAnswer } from './types'

export type AnswerValue = { selectedOptionIds: string[]; writtenText: string | null }
export type SaveState = 'saved' | 'unsaved' | 'saving' | 'error' | 'conflict'

type Entry = { value: AnswerValue; revision: number; state: SaveState; sending: boolean; resend: boolean; timer?: ReturnType<typeof setTimeout> }

export type SaveRequest = (questionId: string, body: { expectedRevision: number } & AnswerValue) => Promise<{ revision: number }>
export type SaveFailure = { code?: string; current?: SavedAnswer | null }

const EMPTY: AnswerValue = { selectedOptionIds: [], writtenText: null }

/**
 * In-memory answers of one attempt (never browser storage). Saves per question are serialized and always carry the
 * last acknowledged revision; a revision conflict adopts the server's newer answer instead of overwriting it.
 */
export class AnswerStore {
  private readonly entries = new Map<string, Entry>()
  private readonly listeners = new Set<() => void>()
  private version = 0

  constructor(
    saved: SavedAnswer[],
    private readonly request: SaveRequest,
    private readonly classify: (error: unknown) => SaveFailure,
    private readonly onClosed: (code: string) => void,
    private readonly textDelayMs = 800,
  ) {
    saved.forEach((answer) =>
      this.entries.set(answer.questionId, {
        value: { selectedOptionIds: answer.selectedOptionIds, writtenText: answer.writtenText },
        revision: answer.revision,
        state: 'saved',
        sending: false,
        resend: false,
      }),
    )
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  snapshot = () => this.version

  value(questionId: string): AnswerValue {
    return this.entries.get(questionId)?.value ?? EMPTY
  }

  state(questionId: string): SaveState {
    return this.entries.get(questionId)?.state ?? 'saved'
  }

  unsavedCount(): number {
    return [...this.entries.values()].filter((entry) => entry.state === 'unsaved' || entry.state === 'saving' || entry.state === 'error').length
  }

  change(questionId: string, value: AnswerValue, immediate: boolean) {
    const entry = this.entry(questionId)
    entry.value = value
    entry.state = 'unsaved'
    clearTimeout(entry.timer)
    this.emit()
    if (immediate) void this.send(questionId)
    else entry.timer = setTimeout(() => void this.send(questionId), this.textDelayMs)
  }

  retry(questionId: string) {
    void this.send(questionId)
  }

  /** Sends everything not yet acknowledged and resolves when nothing is in flight. */
  async flush() {
    await Promise.all(
      [...this.entries.entries()]
        .filter(([, entry]) => entry.state === 'unsaved' || entry.state === 'error')
        .map(([questionId, entry]) => {
          clearTimeout(entry.timer)
          return this.send(questionId)
        }),
    )
    while ([...this.entries.values()].some((entry) => entry.sending)) {
      await new Promise((resolve) => setTimeout(resolve, 25))
    }
  }

  private entry(questionId: string): Entry {
    let entry = this.entries.get(questionId)
    if (!entry) {
      entry = { value: EMPTY, revision: 0, state: 'saved', sending: false, resend: false }
      this.entries.set(questionId, entry)
    }
    return entry
  }

  private async send(questionId: string): Promise<void> {
    const entry = this.entry(questionId)
    if (entry.sending) {
      entry.resend = true
      return
    }
    entry.sending = true
    entry.state = 'saving'
    const value = entry.value
    this.emit()
    try {
      const result = await this.request(questionId, { expectedRevision: entry.revision, ...value })
      entry.revision = result.revision
      entry.resend = entry.resend || entry.value !== value
      entry.state = entry.resend ? 'unsaved' : 'saved'
    } catch (error) {
      const failure = this.classify(error)
      if (failure.code === 'ANSWER_REVISION_CONFLICT') {
        entry.revision = failure.current?.revision ?? 0
        entry.value = { selectedOptionIds: failure.current?.selectedOptionIds ?? [], writtenText: failure.current?.writtenText ?? null }
        entry.state = 'conflict'
        entry.resend = false
      } else if (failure.code === 'ATTEMPT_EXPIRED' || failure.code === 'ATTEMPT_CLOSED') {
        entry.state = 'error'
        entry.resend = false
        this.onClosed(failure.code)
      } else {
        entry.state = 'error'
      }
    } finally {
      entry.sending = false
      this.emit()
    }
    if (entry.resend) {
      entry.resend = false
      await this.send(questionId)
    }
  }

  private emit() {
    this.version++
    this.listeners.forEach((listener) => listener())
  }
}
