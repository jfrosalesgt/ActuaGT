// Pure neutral-transcription helpers: prompt building and output parsing.
// The Android SpeechRecognizer produces the raw transcript; the Meta model
// then neutralises it through this contract. Side-effect free for testing.
// v2: the model also orders the idea into a short title plus description.

export const SECURITY_ALERT_TOKEN = "ALERTA_DE_SEGURIDAD_NO_APTA_PARA_INFRAESTRUCTURA";

export type CleanedTranscription =
  | { kind: "clean"; title: string; text: string }
  | { kind: "security_alert" };

export interface TranscriptionMessage {
  role: string;
  content: string;
}

// Neutral transcription assistant v2. Receives a raw speech transcript,
// orders the idea and returns a short neutral title plus description.
export function buildTranscriptionPrompt(): string {
  return [
    "Eres un asistente de transcripción neutral para reportes cívicos.",
    "Recibirás la transcripción de un audio de un ciudadano quejándose de un problema urbano.",
    "",
    "REGLAS DE TRANSCRIPCIÓN:",
    "1. Identifica el problema principal y su ubicación si se menciona.",
    "2. Elimina cualquier palabra altisonante, insulto o acusación personal (ej. si el audio dice: \"Ese alcalde imbécil dejó este hoyo enorme en la 5ta calle\", el título debe ser: \"Hoyo enorme en la 5ta calle\").",
    `3. Si el audio trata sobre delincuencia común (robos, asaltos, drogas), la transcripción debe ser: "${SECURITY_ALERT_TOKEN}".`,
    "4. Ordena la idea: título corto y neutral de 4 a 8 palabras, y descripción clara de lo observado.",
    "",
    "FORMATO DE SALIDA (ESTRICTAMENTE JSON):",
    "Debes responder ÚNICAMENTE con un objeto JSON válido, sin formato de código markdown, sin texto antes ni después.",
    "{",
    '  "title": "Título corto y neutral de 4 a 8 palabras",',
    '  "description": "Descripción clara y neutral del problema observado",',
    "}",
  ].join("\n");
}

export function buildTranscriptionMessages(transcript: string): TranscriptionMessage[] {
  return [
    { role: "system", content: buildTranscriptionPrompt() },
    { role: "user", content: transcript },
  ];
}

// Parses the model answer: the exact security token is a dedicated
// outcome, a strict {title, description} object is the clean outcome.
// Null when unusable.
export function parseCleanedTranscription(
  raw: string | null | undefined
): CleanedTranscription | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (trimmed === SECURITY_ALERT_TOKEN) return { kind: "security_alert" };
  let text = trimmed;
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
  const title = typeof candidate.title === "string" ? candidate.title.trim().split(/\s+/).filter(Boolean).join(" ") : "";
  const description = typeof candidate.description === "string" ? candidate.description.trim().split(/\s+/).filter(Boolean).join(" ") : "";
  if (!title || !description) return null;
  if (title.length > 80 || description.length > 1000) return null;
  return { kind: "clean", title, text: description };
}
