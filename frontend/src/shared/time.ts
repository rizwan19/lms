/** Conversions between UTC instants and wall-clock "YYYY-MM-DDTHH:mm" values in a named timezone. */

function zoneOffsetMillis(instant: number, timeZone: string): number {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', { timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
      .formatToParts(new Date(instant))
      .map((part) => [part.type, part.value]),
  )
  const asUtc = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), Number(parts.hour), Number(parts.minute), Number(parts.second))
  return asUtc - Math.floor(instant / 1000) * 1000
}

/** Interprets a datetime-local value as wall time in {@code timeZone}; empty input means no value. */
export function zonedInputToInstant(value: string, timeZone: string): string | null {
  if (!value) return null
  const [date, time] = value.split('T')
  const [year, month, day] = date.split('-').map(Number)
  const [hour, minute] = time.split(':').map(Number)
  const wall = Date.UTC(year, month - 1, day, hour, minute)
  let instant = wall - zoneOffsetMillis(wall, timeZone)
  instant = wall - zoneOffsetMillis(instant, timeZone)
  return new Date(instant).toISOString()
}

export function instantToZonedInput(instant: string | null, timeZone: string): string {
  if (!instant) return ''
  const local = new Date(Date.parse(instant) + zoneOffsetMillis(Date.parse(instant), timeZone))
  return local.toISOString().slice(0, 16)
}
