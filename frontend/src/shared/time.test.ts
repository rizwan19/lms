import { instantToZonedInput, zonedInputToInstant } from './time'

test('converts Dhaka wall time to UTC and back', () => {
  expect(zonedInputToInstant('2026-10-19T09:00', 'Asia/Dhaka')).toBe('2026-10-19T03:00:00.000Z')
  expect(instantToZonedInput('2026-10-19T03:00:00Z', 'Asia/Dhaka')).toBe('2026-10-19T09:00')
})

test('handles a date boundary and daylight saving time', () => {
  expect(zonedInputToInstant('2026-10-19T02:30', 'Asia/Dhaka')).toBe('2026-10-18T20:30:00.000Z')
  expect(zonedInputToInstant('2026-07-01T12:00', 'America/New_York')).toBe('2026-07-01T16:00:00.000Z')
  expect(zonedInputToInstant('2026-01-01T12:00', 'America/New_York')).toBe('2026-01-01T17:00:00.000Z')
})

test('empty input means no date', () => {
  expect(zonedInputToInstant('', 'UTC')).toBeNull()
  expect(instantToZonedInput(null, 'UTC')).toBe('')
})
