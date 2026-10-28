// Maintained test for the neutral transcription contract (Iteration 1).
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  SECURITY_ALERT_TOKEN,
  buildTranscriptionMessages,
  buildTranscriptionPrompt,
  parseCleanedTranscription,
} = require("../lib/transcription.js");
const fixtures = require("./fixtures/transcription-responses.json");

for (const fixture of fixtures) {
  test(`transcription fixture: ${fixture.name}`, () => {
    assert.deepEqual(parseCleanedTranscription(fixture.raw), fixture.expected);
  });
}

test("security alert token matches the contract exactly", () => {
  assert.equal(SECURITY_ALERT_TOKEN, "ALERTA_DE_SEGURIDAD_NO_APTA_PARA_INFRAESTRUCTURA");
  assert.deepEqual(parseCleanedTranscription(SECURITY_ALERT_TOKEN), { kind: "security_alert" });
});

test("transcription prompt encodes the neutralisation rules", () => {
  const prompt = buildTranscriptionPrompt();
  assert.match(prompt, /problema principal/);
  assert.match(prompt, /palabra altisonante|insulto/);
  assert.match(prompt, /Hoyo enorme en la 5ta calle/);
  assert.match(prompt, /ALERTA_DE_SEGURIDAD_NO_APTA_PARA_INFRAESTRUCTURA/);
  assert.match(prompt, /ÚNICAMENTE el texto limpio/);
});

test("transcription messages carry the raw transcript as user content", () => {
  const messages = buildTranscriptionMessages("Ese alcalde imbécil dejó este hoyo enorme en la 5ta calle");
  assert.equal(messages[0].role, "system");
  assert.equal(messages[1].role, "user");
  assert.match(messages[1].content, /hoyo enorme/);
});
