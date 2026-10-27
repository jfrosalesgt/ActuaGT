// Maintained test for the ratification escalation policy.
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  HIGH_PRIORITY,
  RATIFICATION_THRESHOLD,
  shouldEscalate,
} = require("../lib/escalation.js");

test("threshold is three ratifications", () => {
  assert.equal(RATIFICATION_THRESHOLD, 3);
});

test("escalates exactly at the threshold", () => {
  assert.equal(shouldEscalate(3, "normal"), true);
});

test("does not escalate below the threshold", () => {
  assert.equal(shouldEscalate(2, "normal"), false);
});

test("escalates above the threshold", () => {
  assert.equal(shouldEscalate(5), true);
});

test("does not re-escalate an already high priority report", () => {
  assert.equal(shouldEscalate(4, HIGH_PRIORITY), false);
});
