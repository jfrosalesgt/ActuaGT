// Pure publication policy: a report becomes public (HIGH priority) once it
// gathers enough VALID ratifications. Kept side-effect free for unit testing.
export const RATIFICATION_THRESHOLD = 3;
export const HIGH_PRIORITY = "high";

export interface RatificationEvidence {
  userUid?: string;
  photoUrl?: string;
  comment?: string;
}

// A ratification counts only with photo evidence, a written description,
// and a ratifier different from the report author.
export function countValidRatifications(
  ratifications: RatificationEvidence[],
  reportedByUid?: string
): number {
  const authors = new Set<string>();
  let valid = 0;
  for (const ratification of ratifications) {
    const userUid = ratification.userUid ?? "";
    if (
      userUid.length === 0 ||
      userUid === reportedByUid ||
      authors.has(userUid) ||
      typeof ratification.photoUrl !== "string" ||
      ratification.photoUrl.length === 0 ||
      typeof ratification.comment !== "string" ||
      ratification.comment.trim().length === 0
    ) {
      continue;
    }
    authors.add(userUid);
    valid += 1;
  }
  return valid;
}

export function shouldEscalate(
  ratificationCount: number,
  currentPriority?: string
): boolean {
  return (
    ratificationCount >= RATIFICATION_THRESHOLD &&
    currentPriority !== HIGH_PRIORITY
  );
}
