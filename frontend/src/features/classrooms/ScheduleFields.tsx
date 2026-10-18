import type { FieldErrors } from '../../shared/api'
import { instantToZonedInput } from '../../shared/time'

/** Optional start/end inputs entered in the institution timezone. */
export function ScheduleFields({ timeZone, startsAt, endsAt, errors }: { timeZone: string; startsAt?: string | null; endsAt?: string | null; errors: FieldErrors }) {
  return (
    <div className="inline-form">
      <div className="field">
        <label htmlFor="startsAt">Opens ({timeZone})</label>
        <input id="startsAt" name="startsAt" type="datetime-local" defaultValue={instantToZonedInput(startsAt ?? null, timeZone)} />
        <small className="muted">Leave empty to open on activation</small>
      </div>
      <div className="field">
        <label htmlFor="endsAt">Closes ({timeZone})</label>
        <input id="endsAt" name="endsAt" type="datetime-local" defaultValue={instantToZonedInput(endsAt ?? null, timeZone)} />
        <small className="muted">Leave empty for a self-paced class</small>
        {errors.endsAt && <small className="error">{errors.endsAt}</small>}
      </div>
    </div>
  )
}
