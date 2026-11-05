// Maintained test for the Iteration 1 triage contract (node:test, no frameworks).
// Run with: npm test (builds first, then exercises the compiled output).
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  TRIAGE_CATEGORIES,
  buildChatMessages,
  buildSystemPrompt,
  parseTriageResponse,
} = require("../lib/moderation.js");
const fixtures = require("./fixtures/triage-responses.json");

for (const fixture of fixtures) {
  test(`triage fixture: ${fixture.name}`, () => {
    const parsed = parseTriageResponse(fixture.raw);
    if (fixture.expected === null) {
      assert.equal(parsed, null);
      return;
    }
    assert.equal(parsed.status, fixture.expected.status);
    assert.equal(parsed.category, fixture.expected.category);
    assert.ok(parsed.reason.split(/\s+/).filter(Boolean).length <= 20);
    if (fixture.expected.suggestedTitle) {
      assert.equal(parsed.suggestedTitle, fixture.expected.suggestedTitle);
      const words = parsed.suggestedTitle.split(/\s+/).filter(Boolean);
      assert.ok(words.length >= 4 && words.length <= 6);
    }
  });
}

test("triage categories match the civic contract", () => {
  assert.deepEqual(TRIAGE_CATEGORIES, [
    "VIALIDAD",
    "AGUA_Y_DRENAJES",
    "ALUMBRADO",
    "DESECHOS_Y_AMBIENTE",
    "ESPACIOS_PUBLICOS",
    "OTROS",
  ]);
});

test("system prompt encodes every acceptance rule", () => {
  const prompt = buildSystemPrompt();
  assert.match(prompt, /infraestructura/);
  assert.match(prompt, /captura de pantalla/);
  assert.match(prompt, /nombres propios/);
  assert.match(prompt, /coherencia semántica/);
  assert.match(prompt, /ÚNICAMENTE con un objeto JSON/);
  for (const category of TRIAGE_CATEGORIES) assert.match(prompt, new RegExp(category));
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
