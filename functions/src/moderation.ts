// Pure moderation helpers: prompt building and verdict parsing.
// Kept side-effect free so they are unit-testable without network or Firestore.

export const MUSE_API_URL = "https://api.meta.ai/v1/chat/completions";
export const MUSE_MODEL = "muse-spark-1.3";

export interface ReportText {
  title: string;
  description: string;
  category: string;
}

export type Decision = "APROBADO" | "RECHAZADO";

export interface Verdict {
  decision: Decision;
  reason: string;
}

export interface ChatMessage {
  role: string;
  content: unknown;
}

// Strict system prompt: the model must answer with exactly one verdict line.
export function buildSystemPrompt(): string {
  return [
    "You are a content moderator for ActuaGT, a civic infrastructure-reporting platform.",
    "Evaluate the attached photo and the report text against these rules:",
    "1. The photo must look like a real photograph of a real-world scene. Reject memes, screenshots, drawings, AI-generated images, and unrelated content.",
    "2. The text must describe an infrastructure issue and must not contain insults, hate speech, threats, or profanity in any language.",
    "Respond with EXACTLY one line and nothing else:",
    "- `APROBADO` when both checks pass.",
    "- `RECHAZADO: <short reason in Spanish>` when any check fails.",
  ].join("\n");
}

// OpenAI-compatible chat payload: system rules plus report text and photo URLs.
export function buildChatMessages(report: ReportText, photoUrls: string[]): ChatMessage[] {
  const text =
    `Título: ${report.title}\nDescripción: ${report.description}\nCategoría: ${report.category}`;
  const content: unknown[] = [{ type: "text", text }];
  for (const url of photoUrls) {
    content.push({ type: "image_url", image_url: { url } });
  }
  return [
    { role: "system", content: buildSystemPrompt() },
    { role: "user", content },
  ];
}

// Parses the single-line verdict. Returns null when the answer is unusable,
// in which case the report stays pending for manual review.
export function parseVerdict(raw: string | null | undefined): Verdict | null {
  if (!raw) return null;
  const line = raw.trim();
  if (/^APROBADO\b/i.test(line)) return { decision: "APROBADO", reason: "" };
  const rejected = line.match(/^RECHAZADO\s*:?\s*([\s\S]*)$/i);
  if (rejected) {
    const reason = (rejected[1] ?? "").trim();
    return {
      decision: "RECHAZADO",
      reason: reason || "No cumple los criterios de publicación.",
    };
  }
  return null;
}
