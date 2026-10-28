// Pure neutral-transcription helpers: prompt building and output parsing.
// The Android SpeechRecognizer produces the raw transcript; the Meta model
// then neutralises it through this contract. Side-effect free for testing.

export const SECURITY_ALERT_TOKEN = "ALERTA_DE_SEGURIDAD_NO_APTA_PARA_INFRAESTRUCTURA";

export type CleanedTranscription =
  | { kind: "clean"; text: string }
  | { kind: "security_alert" };

export interface TranscriptionMessage {
  role: string;
  content: string;
}

// Neutral transcription assistant v1. Receives a raw speech transcript and
// must return only the cleaned, neutral text.
export function buildTranscriptionPrompt(): string {
  return [
    "Eres un asistente de transcripción neutral para reportes cívicos.",
    "Recibirás la transcripción de un audio de un ciudadano quejándose de un problema urbano.",
    "",
    "REGLAS DE TRANSCRIPCIÓN:",
    "1. Transcribe el audio identificando el problema principal y su ubicación si se menciona.",
    "2. Elimina cualquier palabra altisonante, insulto o acusación personal (ej. si el audio dice: \"Ese alcalde imbécil dejó este hoyo enorme en la 5ta calle\", la salida debe ser: \"Hoyo enorme en la 5ta calle\").",
    `3. Si el audio trata sobre delincuencia común (robos, asaltos, drogas), la transcripción debe ser: "${SECURITY_ALERT_TOKEN}".`,
    "",
    "Devuelve ÚNICAMENTE el texto limpio y neutralizado.",
  ].join("\n");
}

export function buildTranscriptionMessages(transcript: string): TranscriptionMessage[] {
  return [
    { role: "system", content: buildTranscriptionPrompt() },
    { role: "user", content: transcript },
  ];
}

// Parses the model answer: the exact security token is a dedicated outcome,
// anything else non-empty is the cleaned text. Null when unusable.
export function parseCleanedTranscription(
  raw: string | null | undefined
): CleanedTranscription | null {
  if (!raw) return null;
  const text = raw.trim().split(/\s+/).filter(Boolean).join(" ");
  if (!text) return null;
  if (text === SECURITY_ALERT_TOKEN) return { kind: "security_alert" };
  return { kind: "clean", text };
}
