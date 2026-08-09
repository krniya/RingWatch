// Shared row/list entry animation - every animated list in the app pulls from this one
// definition so reduced-motion handling and timing only need to be right in one place.
export const rowContainerVariants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.08 },
  },
}

export const rowVariants = {
  hidden: { opacity: 0, y: -6 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.18 } },
}

// Static (no motion) fallback for prefers-reduced-motion - callers pick between the two
// variant sets based on useReducedMotion() rather than every component re-deriving it.
export const staticRowVariants = {
  hidden: { opacity: 1, y: 0 },
  visible: { opacity: 1, y: 0 },
}
