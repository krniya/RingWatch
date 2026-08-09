import { useEffect, useRef, useState } from 'react'
import { useReducedMotion } from 'framer-motion'

// Animates a number counting up to `target` on change. Short-circuits instantly under
// prefers-reduced-motion rather than skipping the animation silently-but-still-timed.
export function useCountUp(target, duration = 400) {
  const prefersReducedMotion = useReducedMotion()
  const [value, setValue] = useState(target)
  const frameRef = useRef(null)
  // Mirrors `value` on every render so an interrupted animation (a new target arriving before
  // the previous one finished) starts from wherever the display currently is, not from a ref
  // that's only updated on completion - otherwise the displayed number visibly jumps backward.
  const valueRef = useRef(value)
  valueRef.current = value

  useEffect(() => {
    if (prefersReducedMotion) {
      setValue(target)
      return
    }

    const start = performance.now()
    const from = valueRef.current
    const delta = target - from

    function tick(now) {
      const elapsed = now - start
      const progress = Math.min(elapsed / duration, 1)
      const eased = 1 - (1 - progress) * (1 - progress)
      setValue(Math.round(from + delta * eased))
      if (progress < 1) {
        frameRef.current = requestAnimationFrame(tick)
      }
    }

    frameRef.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frameRef.current)
  }, [target, duration, prefersReducedMotion])

  return value
}
