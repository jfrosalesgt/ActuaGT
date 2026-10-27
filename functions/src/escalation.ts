// Pure escalation policy: a report becomes HIGH priority once it gathers
// enough ratifications. Kept side-effect free for unit testing.
export const RATIFICATION_THRESHOLD = 3;
export const HIGH_PRIORITY = "high";

export function shouldEscalate(
  ratificationCount: number,
  currentPriority?: string
): boolean {
  return (
    ratificationCount >= RATIFICATION_THRESHOLD &&
    currentPriority !== HIGH_PRIORITY
  );
}
