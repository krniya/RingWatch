// recharts' SVG fill/stroke props can't resolve Tailwind utility classes or CSS custom properties
// the way JSX className can, so these mirror the literal hex values from index.css's @theme block.
// Keep in sync with that file - the app has no dark/light toggle, so there's only one palette to
// track.
export const CHART_COLORS = {
  approve: '#22c55e',
  flag: '#f59e0b',
  block: '#ef4444',
  accent: '#22d3ee',
  border: '#23262e',
  textFaint: '#565c6b',
  panel: '#111318',
}

export const CHART_FONT_FAMILY = '"IBM Plex Mono", ui-monospace, "SFMono-Regular", monospace'

export const CHART_TICK_STYLE = {
  fontFamily: CHART_FONT_FAMILY,
  fontSize: 11,
  fill: CHART_COLORS.textFaint,
}
