import { STAGES } from './StageTrack'

// A heartbeat that runs along a line and passes through the five stages of a placement.
// It draws itself once on load; the stage nodes pop in as the line reaches them.
const BASELINE = 120
const NODE_X = [200, 300, 400, 500, 600]
const PATH =
  `M0 ${BASELINE} H40 L56 ${BASELINE} L70 90 L86 164 L104 36 L122 146 L136 ${BASELINE} H640`

export function PulseHero() {
  return (
    <svg
      viewBox="0 0 640 200"
      role="img"
      aria-label="A pulse line passing through five stages: Applied, Shortlisted, Test, Interview, Offer"
      className="w-full max-w-xl overflow-visible"
    >
      <path
        d={PATH}
        pathLength={1}
        fill="none"
        stroke="var(--signal)"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="pp-draw"
      />
      {NODE_X.map((x, i) => {
        const last = i === NODE_X.length - 1
        // the line takes ~2.4s starting at 0.3s; stagger nodes along its length
        const delay = `${0.3 + 1.2 + i * 0.28}s`
        return (
          <g key={STAGES[i]} className="pp-node" style={{ animationDelay: delay }}>
            <circle
              cx={x}
              cy={BASELINE}
              r={8}
              fill={last ? 'var(--signal)' : 'var(--rail)'}
              stroke={last ? 'var(--signal)' : 'var(--rail-ink)'}
              strokeWidth="2.5"
            />
            <text
              x={x}
              y={BASELINE + 40}
              textAnchor="middle"
              fontSize="14"
              fontWeight={last ? 700 : 500}
              fill="var(--rail-ink)"
              opacity={last ? 1 : 0.8}
            >
              {STAGES[i]}
            </text>
          </g>
        )
      })}
    </svg>
  )
}
