// Pure triage helpers: prompt building and strict JSON verdict parsing.
// Kept side-effect free so they are unit-testable without network or Firestore.

export const MUSE_API_URL = "https://api.meta.ai/v1/chat/completions";
export const MUSE_MODEL = "muse-spark-1.3";

export interface ReportText {
  title: string;
  description: string;
  category: string;
}

export const TRIAGE_CATEGORIES = [
  "VIALIDAD",
  "AGUA_Y_DRENAJES",
  "ALUMBRADO",
  "DESECHOS_Y_AMBIENTE",
  "ESPACIOS_PUBLICOS",
  "OTROS",
] as const;

export type TriageCategory = (typeof TRIAGE_CATEGORIES)[number];
export type TriageStatus = "APPROVED" | "REJECTED";

export interface TriageResult {
  status: TriageStatus;
  category: TriageCategory | "REJECTED";
  reason: string;
  suggestedTitle?: string;
}

export interface ChatMessage {
  role: string;
  content: unknown;
}

function wordCount(value: string): number {
  return value.trim().split(/\s+/).filter(Boolean).length;
}

// Civic triage engine v1: every acceptance rule must hold, and the model
// must answer with exactly one strict JSON object (see parseTriageResponse).
export function buildSystemPrompt(): string {
  return [
    'Eres el motor de triaje cívico automatizado para "ActuaGT", una plataforma de infraestructura urbana.',
    "Recibirás una imagen capturada por un ciudadano y su descripción (texto). Tu objetivo es evaluar la autenticidad, adecuación y clasificación del reporte.",
    "",
    "REGLAS DE ACEPTACIÓN (DEBE CUMPLIR TODAS):",
    "1. La imagen debe mostrar un problema de infraestructura física, mobiliario urbano o servicios públicos en el exterior (ej. baches, fugas de agua, postes dañados, basura acumulada).",
    "2. La imagen NO debe ser una captura de pantalla, una foto de otra pantalla, ni arte digital.",
    "3. El texto debe describir un problema material.",
    "4. El texto NO debe contener nombres propios, insultos, difamación, ni acusaciones de delitos o corrupción dirigidos a personas físicas o políticos (ej. \"el alcalde Juan roba\", \"los vecinos ladrones\").",
    "",
    "INSTRUCCIONES DE ANÁLISIS:",
    "1. Analiza la imagen para confirmar que cumple las reglas 1 y 2.",
    "2. Analiza el texto para confirmar que cumple las reglas 3 y 4.",
    "3. Comprueba que el texto y la imagen tienen coherencia semántica (ej. si el texto habla de una fuga de agua, la imagen debe mostrar agua o tuberías).",
    `4. Clasifica el problema en UNA de las siguientes categorías: [${TRIAGE_CATEGORIES.join(", ")}]. Si es inválido, usa la categoría "REJECTED".`,
    "",
    "FORMATO DE SALIDA (ESTRICTAMENTE JSON):",
    "Debes responder ÚNICAMENTE con un objeto JSON válido, sin formato de código markdown, sin texto antes ni después.",
    "{",
    '  "status": "APPROVED" o "REJECTED",',
    '  "category": "Una de las categorías de la lista",',
    '  "reason": "Explicación muy breve de por qué se aprobó o rechazó (máximo 20 palabras).",',
    '  "suggested_title": "Si es APPROVED, genera un título corto y neutral de 4 a 6 palabras resumiendo el problema."',
    "}",
  ].join("\n");
}

// OpenAI-compatible chat payload: system rules plus report text and photo URLs.
export function buildChatMessages(report: ReportText, photoUrls: string[]): ChatMessage[] {
  const text =
    `Título: ${report.title}\nDescripción: ${report.description}\nCategoría sugerida por el usuario: ${report.category}`;
  const content: unknown[] = [{ type: "text", text }];
  for (const url of photoUrls) {
    content.push({ type: "image_url", image_url: { url } });
  }
  return [
    { role: "system", content: buildSystemPrompt() },
    { role: "user", content },
  ];
}

// Parses and validates the strict JSON triage answer. Returns null when the
// answer is unusable, in which case the caller retries once and then leaves
// the report pending for manual review.
export function parseTriageResponse(raw: string | null | undefined): TriageResult | null {
  if (!raw) return null;
  let text = raw.trim();
  const fenced = text.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  if (fenced) text = fenced[1].trim();
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }
  if (typeof parsed !== "object" || parsed === null) return null;
  const candidate = parsed as Record<string, unknown>;
  const status = candidate.status;
  const category = candidate.category;
  const reason = candidate.reason;
  const suggestedTitle = candidate.suggested_title;

  if (status !== "APPROVED" && status !== "REJECTED") return null;
  if (typeof category !== "string" || typeof reason !== "string") return null;
  const cleanReason = reason.trim();
  if (wordCount(cleanReason) < 1 || wordCount(cleanReason) > 20) return null;

  if (status === "REJECTED") {
    if (category !== "REJECTED") return null;
    return { status, category: "REJECTED", reason: cleanReason };
  }

  if (!(TRIAGE_CATEGORIES as readonly string[]).includes(category)) return null;
  if (typeof suggestedTitle !== "string") return null;
  const cleanTitle = suggestedTitle.trim();
  const titleWords = wordCount(cleanTitle);
  if (titleWords < 4 || titleWords > 6) return null;
  return {
    status,
    category: category as TriageCategory,
    reason: cleanReason,
    suggestedTitle: cleanTitle,
  };
}
