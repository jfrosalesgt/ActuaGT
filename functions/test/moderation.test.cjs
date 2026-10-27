// Maintained test for the moderation verdict contract (node:test, no frameworks).
// Run with: npm test (builds first, then exercises the compiled output).
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildChatMessages,
  buildSystemPrompt,
  parseVerdict,
} = require("../lib/moderation.js");

test("parseVerdict accepts a clean APROBADO", () => {
  assert.deepEqual(parseVerdict("APROBADO"), { decision: "APROBADO", reason: "" });
});

test("parseVerdict tolerates casing and trailing text", () => {
  assert.deepEqual(parseVerdict("aprobado.").decision, "APROBADO");
});

test("parseVerdict extracts the Spanish rejection motive", () => {
  assert.deepEqual(parseVerdict("RECHAZADO: la imagen es un meme"), {
    decision: "RECHAZADO",
    reason: "la imagen es un meme",
  });
});

test("parseVerdict defaults an empty rejection motive", () => {
  const verdict = parseVerdict("RECHAZADO");
  assert.equal(verdict.decision, "RECHAZADO");
  assert.match(verdict.reason, /\S/);
});

test("parseVerdict returns null for unusable answers", () => {
  assert.equal(parseVerdict(""), null);
  assert.equal(parseVerdict(null), null);
  assert.equal(parseVerdict("Quizás, hay que revisarlo"), null);
});

test("system prompt demands exactly one verdict line", () => {
  const prompt = buildSystemPrompt();
  assert.match(prompt, /APROBADO/);
  assert.match(prompt, /RECHAZADO/);
  assert.match(prompt, /EXACTLY one line/);
});

test("chat messages carry text plus every photo URL", () => {
  const messages = buildChatMessages(
    { title: "Bache", description: "En la esquina", category: "vialidad" },
    ["https://example/1.jpg", "https://example/2.jpg"]
  );
  assert.equal(messages[0].role, "system");
  assert.equal(messages[1].role, "user");
  const images = messages[1].content.filter((part) => part.type === "image_url");
  assert.equal(images.length, 2);
});
