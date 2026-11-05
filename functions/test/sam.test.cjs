// Maintained test for the SAM 3.1 damage-segmentation contract.
// Run with: npm test (builds first, then exercises the compiled output).
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  SAM_MAX_BOXES,
  SAM_MIN_SCORE,
  buildSamRequest,
  damageDocumentFragment,
  hasVisibleDamage,
  parseSamResponse,
  promptsForCategory,
} = require("../lib/sam.js");

test("prompts fall back to OTROS on unknown category", () => {
  assert.deepEqual(promptsForCategory("VIALIDAD")[0], "pothole");
  assert.ok(promptsForCategory("UNKNOWN").length > 0);
  assert.ok(promptsForCategory(undefined).length > 0);
});

test("request carries images plus text prompts", () => {
  const body = buildSamRequest(["https://example/1.jpg"], "VIALIDAD");
  assert.deepEqual(body.images, ["https://example/1.jpg"]);
  assert.ok(body.text_prompts.includes("pothole"));
  assert.equal(body.category, "VIALIDAD");
});

test("parses detections shape and sorts by score", () => {
  const raw = JSON.stringify({
    detections: [
      { box: [10, 10, 100, 100], score: 0.5, label: "pothole" },
      { box: [200, 200, 400, 400], score: 0.9, label: "crack" },
    ],
  });
  const parsed = parseSamResponse(raw, "VIALIDAD", 123);
  assert.equal(parsed.score, 0.9);
  assert.equal(parsed.boxes.length, 2);
  assert.equal(parsed.boxes[0].label, "crack");
  assert.deepEqual(parsed.labels.sort(), ["crack", "pothole"]);
  assert.equal(parsed.segmentedAt, 123);
});

test("parses flat box shape too", () => {
  const raw = JSON.stringify({
    boxes: [{ x1: 0, y1: 0, x2: 50, y2: 50, score: 0.8 }],
  });
  const parsed = parseSamResponse(raw, "OTROS", 7);
  assert.equal(parsed.boxes.length, 1);
  assert.equal(parsed.score, 0.8);
});

test("drops low-score and invalid boxes", () => {
  const raw = JSON.stringify({
    detections: [
      { box: [0, 0, 10, 10], score: 0.1, label: "noise" },
      { box: [50, 50, 20, 20], score: 0.9, label: "inverted" },
      { box: [0, 0, 2000, 2000], score: 0.9, label: "oversized" },
    ],
  });
  assert.equal(parseSamResponse(raw, "VIALIDAD"), null);
});

test("rejects empty and malformed answers", () => {
  assert.equal(parseSamResponse(null, "VIALIDAD"), null);
  assert.equal(parseSamResponse("", "VIALIDAD"), null);
  assert.equal(parseSamResponse("not json", "VIALIDAD"), null);
  assert.equal(parseSamResponse(JSON.stringify({}), "VIALIDAD"), null);
});

test("caps boxes at the maximum", () => {
  const detections = Array.from({ length: SAM_MAX_BOXES + 3 }, (_, i) => ({
    box: [i, i, i + 50, i + 50],
    score: 0.9,
    label: "pothole",
  }));
  const parsed = parseSamResponse(JSON.stringify({ detections }), "VIALIDAD");
  assert.equal(parsed.boxes.length, SAM_MAX_BOXES);
});

test("damage fragment only for visible damage", () => {
  const visible = parseSamResponse(
    JSON.stringify({ detections: [{ box: [0, 0, 10, 10], score: 0.7 }] }),
    "VIALIDAD",
    9
  );
  assert.equal(hasVisibleDamage(visible), true);
  assert.equal(hasVisibleDamage(null), false);
  const fragment = damageDocumentFragment(visible);
  assert.equal(fragment.damageScore, 0.7);
  assert.equal(fragment.damageSegmentedAt, 9);
  assert.equal(damageDocumentFragment(null), null);
});

test("visible threshold matches the minimum score", () => {
  const edge = { boxes: [], score: SAM_MIN_SCORE, labels: [], segmentedAt: 1 };
  assert.equal(hasVisibleDamage(edge), true);
});
