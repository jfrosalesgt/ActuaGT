// Pure official-response policy. Kept side-effect free for unit testing.
export const RESPONDABLE_STATUSES = ["published", "in_progress", "resolved"];

export interface OfficialProfile {
  role?: string;
  zone?: string;
}

// Writes come from the callable, which resolves isAdmin from the verified
// Auth custom claims, never from client input.
export function isAdminClaim(token: unknown): boolean {
  return (
    typeof token === "object" &&
    token !== null &&
    (token as Record<string, unknown>).admin === true
  );
}

// Officials act within their own zone; admins act anywhere.
export function canActOnZone(
  profile: OfficialProfile | undefined,
  zone: string | undefined,
  isAdmin: boolean
): boolean {
  if (!profile || !zone) return false;
  if (isAdmin) return true;
  return profile.role === "OFFICIAL" && profile.zone === zone;
}

// The proof photo is accepted only when the triage engine approves it under
// the report's own official category.
export function isProofAccepted(
  verdict: { status: string; category: string } | null,
  expectedCategory: string | undefined
): boolean {
  if (!verdict || !expectedCategory) return false;
  return verdict.status === "APPROVED" && verdict.category === expectedCategory;
}

// A message-only response is allowed on published, in-progress or resolved
// reports. A status change must follow the case lifecycle.
export function canRespond(
  currentStatus: string | undefined,
  targetStatus?: string
): boolean {
  if (currentStatus == null || !RESPONDABLE_STATUSES.includes(currentStatus)) return false;
  if (targetStatus == null) return true;
  if (targetStatus === "in_progress") return currentStatus === "published";
  if (targetStatus === "resolved") {
    return currentStatus === "published" || currentStatus === "in_progress";
  }
  return false;
}
