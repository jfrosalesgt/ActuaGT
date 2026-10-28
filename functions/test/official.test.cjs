// Maintained test for the official-response policy.
const test = require("node:test");
const assert = require("node:assert/strict");
const { canRespond, isProofAccepted } = require("../lib/official.js");

test("allows taking a published case", () => {
  assert.equal(canRespond("published", "in_progress"), true);
});

test("allows resolving a published or in-progress case", () => {
  assert.equal(canRespond("published", "resolved"), true);
  assert.equal(canRespond("in_progress", "resolved"), true);
});

test("allows message-only responses on public states", () => {
  assert.equal(canRespond("published"), true);
  assert.equal(canRespond("in_progress"), true);
  assert.equal(canRespond("resolved"), true);
});

test("rejects actions on non-public states", () => {
  assert.equal(canRespond("in_ratification", "in_progress"), false);
  assert.equal(canRespond("in_ratification"), false);
  assert.equal(canRespond("rejected", "resolved"), false);
  assert.equal(canRespond(undefined, "in_progress"), false);
});

test("rejects unknown target states", () => {
  assert.equal(canRespond("published", "archived"), false);
});

test("accepts proof approved under the report category", () => {
  assert.equal(
    isProofAccepted({ status: "APPROVED", category: "VIALIDAD" }, "VIALIDAD"),
    true
  );
});

test("rejects proof with another category, rejection, or missing data", () => {
  assert.equal(
    isProofAccepted({ status: "APPROVED", category: "ALUMBRADO" }, "VIALIDAD"),
    false
  );
  assert.equal(
    isProofAccepted({ status: "REJECTED", category: "REJECTED" }, "VIALIDAD"),
    false
  );
  assert.equal(isProofAccepted(null, "VIALIDAD"), false);
  assert.equal(
    isProofAccepted({ status: "APPROVED", category: "VIALIDAD" }, undefined),
    false
  );
});

test("needs_review is distinct from approved and from missing state", () => {
  const { isProofAccepted } = require("../lib/official.js");
  const approved = { status: "APPROVED", category: "VIALIDAD" };
  assert.equal(isProofAccepted(approved, "VIALIDAD"), true);
  // Awaiting human review is not an approval: the gate must stay closed.
  assert.equal(isProofAccepted(null, "VIALIDAD"), false);
});

test("zone topics match the client naming", () => {
  const { topicForZone } = require("../lib/notifications.js");
  assert.equal(topicForZone("Guatemala", "ratify"), "zone_guatemala_ratify");
  assert.equal(topicForZone("Villa Nueva", "officials"), "zone_villa_nueva_officials");
});
