const ZONES = Intl.supportedValuesOf('timeZone')

export function TimezoneSelect({ id, name, defaultValue }: { id: string; name: string; defaultValue?: string }) {
  const initial = defaultValue ?? Intl.DateTimeFormat().resolvedOptions().timeZone
  return (
    <select id={id} name={name} defaultValue={initial} required>
      {ZONES.includes(initial) ? null : <option value={initial}>{initial}</option>}
      {ZONES.map((zone) => (
        <option key={zone} value={zone}>
          {zone}
        </option>
      ))}
    </select>
  )
}
