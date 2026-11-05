// SAM damage-segmentation helpers (Meta Segment Anything 3.1, backend auto).
// Pure and side-effect free: prompt building, request shaping and strict
// response parsing live here so they are unit-testable without network,
// GPU, or Firestore. The hosted model itself runs elsewhere (self-hosted
// FastAPI, HF Inference Endpoint, or Replicate) behind SAM_API_URL; this
// module only speaks its thin HTTP contract.
//
// Hosted contract (POST JSON):
//   { images: string[], text_prompts: string[], category: string }
// Response JSON (any of these shapes):
//   { detections: [{ box: [x1,y1,x2,y2], score: number, label?: string }] }
//   { boxes: [{ x1,y1,x2,y2, score, label? }] }
//   { masks: [...] } is NOT parsed here: masks stay on the provider and only
//   boxes/scores travel back to Firestore, so old app versions keep working.

export const SAM_TEXT_PROMPTS: Record<string, string[]> = {
  VIALIDAD: ["pothole", "cracked road surface", "damaged asphalt"],
  AGUA_Y_DRENAJES: ["water leak", "flooded street", "broken drain pipe"],
  ALUMBRADO: ["broken street light", "damaged lamp post"],
  DESECHOS_Y_AMBIENTE: ["trash pile", "accumulated garbage", "illegal dumping"],
  ESPACIOS_PUBLICOS: ["damaged park bench", "broken sidewalk", "vandalized public furniture"],
  OTROS: ["damaged urban infrastructure"],
};

export const SAM_MAX_BOXES = 5;
export const SAM_MIN_SCORE = 0.35;
export const SAM_NO_DAMAGE_SCORE = 0.2;

// Normalized box in 0..1000 space (provider-independent, UI-ready).
export interface DamageBox {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  score: number;
  label: string;
}

export interface DamageSegmentation {
  boxes: DamageBox[];
  score: number;
  labels: string[];
  segmentedAt: number;
}

export interface SamRequestBody {
  images: string[];
  text_prompts: string[];
  category: string;
}

// Text prompts for a civic category. Falls back to OTROS prompts.
export function promptsForCategory(category: string | undefined): string[] {
  if (category && SAM_TEXT_PROMPTS[category]) return SAM_TEXT_PROMPTS[category];
  return SAM_TEXT_PROMPTS.OTROS;
}

// Request body sent to the hosted SAM endpoint. Pure: no fetch here.
export function buildSamRequest(
  photoUrls: string[],
  category: string | undefined
): SamRequestBody {
  return {
    images: [...photoUrls],
    text_prompts: promptsForCategory(category),
    category: category ?? "OTROS",
  };
}

function toFinite(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function cleanLabel(value: unknown, fallback: string): string {
  if (typeof value === "string" && value.trim().length > 0) {
    return value.trim().slice(0, 60);
  }
  return fallback;
}

// Accepts one raw box candidate in any of the supported shapes.
// Coordinates must already be normalized to 0..1000.
function parseBoxCandidate(raw: unknown, fallbackLabel: string): DamageBox | null {
  if (typeof raw !== "object" || raw === null) return null;
  const obj = raw as Record<string, unknown>;

  let coords: unknown = obj.box;
  if (!Array.isArray(coords)) {
    const x1 = toFinite(obj.x1);
    const y1 = toFinite(obj.y1);
    const x2 = toFinite(obj.x2);
    const y2 = toFinite(obj.y2);
    if (x1 === null || y1 === null || x2 === null || y2 === null) return null;
    coords = [x1, y1, x2, y2];
  }
  const list = coords as unknown[];
  if (list.length !== 4) return null;
  const nums = list.map(toFinite);
  if (nums.some((n) => n === null)) return null;
  const [x1, y1, x2, y2] = nums as number[];
  if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) return null;
  if (x1 > 1000 || y1 > 1000 || x2 > 1000 || y2 > 1000) return null;
  if (x2 <= x1 || y2 <= y1) return null;

  const score = toFinite(obj.score) ?? toFinite(obj.confidence);
  if (score === null || score < 0 || score > 1) return null;
  if (score < SAM_MIN_SCORE) return null;

  return {
    x1,
    y1,
    x2,
    y2,
    score,
    label: cleanLabel(obj.label ?? obj.class, fallbackLabel),
  };
}

// Parses the hosted SAM answer. Null when unusable: the caller treats it
// as "no segmentation available", never as "no damage".
export function parseSamResponse(
  raw: string | null | undefined,
  category: string | undefined,
  nowMillis = Date.now()
): DamageSegmentation | null {
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
  const obj = parsed as Record<string, unknown>;
  const candidates: unknown[] = Array.isArray(obj.detections)
    ? obj.detections
    : Array.isArray(obj.boxes)
      ? obj.boxes
      : [];
  if (candidates.length === 0) return null;

  const fallbackLabel = (category ?? "OTROS").toLowerCase();
  const boxes: DamageBox[] = [];
  for (const candidate of candidates) {
    const box = parseBoxCandidate(candidate, fallbackLabel);
    if (box) boxes.push(box);
    if (boxes.length >= SAM_MAX_BOXES) break;
  }
  if (boxes.length === 0) return null;
  boxes.sort((a, b) => b.score - a.score);

  return {
    boxes,
    score: boxes[0].score,
    labels: [...new Set(boxes.map((box) => box.label))],
    segmentedAt: nowMillis,
  };
}

// True when the segmentation shows visible damage worth displaying.
export function hasVisibleDamage(segmentation: DamageSegmentation | null): boolean {
  if (!segmentation) return false;
  return segmentation.score >= SAM_MIN_SCORE;
}

// Document fragment merged into the approved incident. Null-safe: unknown
// fields are never written, so old app versions ignore the whole fragment.
export function damageDocumentFragment(segmentation: DamageSegmentation | null): Record<string, unknown> | null {
  if (!hasVisibleDamage(segmentation)) return null;
  const seg = segmentation as DamageSegmentation;
  return {
    damageScore: seg.score,
    damageBoxes: seg.boxes,
    damageLabels: seg.labels,
    damageSegmentedAt: seg.segmentedAt,
  };
}
