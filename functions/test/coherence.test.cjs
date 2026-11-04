// Maintained test for the pre-submit photo/text coherence contract.
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildCoherenceMessages,
  parseCoherenceVerdict,
} = require("../lib/moderation.js");
const fixtures = require("./fixtures/coherence-responses.json");

for (const fixture of fixtures) {
  test(`coherence fixture: ${fixture.name}`, () => {
    assert.deepEqual(parseCoherenceVerdict(fixture.raw), fixture.expected);
  });
}

test("coherence messages carry text plus every photo URL", () => {
  const messages = buildCoherenceMessages(
    "Hoyo enorme en la 5ta calle",
    "Hoyo enorme que dificulta el paso",
    ["https://example.com/a.jpg", "https://example.com/b.jpg"]
  );
  assert.equal(messages[0].role, "system");
  assert.match(messages[0].content, /ÚNICAMENTE con un objeto JSON/);
  assert.equal(messages[1].role, "user");
  const content = messages[1].content;
  assert.match(content[0].text, /Hoyo enorme en la 5ta calle/);
  assert.equal(content.filter((part) => part.type === "image_url").length, 2);
  assert.deepEqual(content[1], {
    type: "image_url",
    image_url: { url: "https://example.com/a.jpg" },
  });
});
