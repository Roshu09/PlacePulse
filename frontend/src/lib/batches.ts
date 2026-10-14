/** Pass-out years a batch can have. One list, so the drive form and the profile always agree. */
export const BATCH_YEARS = [2025, 2026, 2027, 2028, 2029]

/** Minimum-CGPA choices for a drive. 0 means anyone can apply. */
export const CGPA_OPTIONS = [0, 5, 5.5, 6, 6.5, 7, 7.5, 8, 8.5, 9, 9.5, 10]

export function cgpaOptionLabel(value: number): string {
  return value === 0 ? 'No minimum' : value.toFixed(1)
}

/** A saved profile batch as a pass-out year: "2027" is 2027 and an older "2025-27" is also 2027. */
export function passOutYear(batch: string | null | undefined): number | null {
  if (!batch) return null
  const text = batch.trim()
  const single = /^(\d{4})$/.exec(text)
  if (single) return Number(single[1])
  const range = /^(\d{4})\s*[-\u2013/]\s*(\d{2}|\d{4})$/.exec(text)
  if (range) return range[2].length === 2 ? 2000 + Number(range[2]) : Number(range[2])
  return null
}

export function batchLabel(years: number[]): string {
  return years.length === 0 ? 'any batch' : `batch ${years.join(', ')}`
}
