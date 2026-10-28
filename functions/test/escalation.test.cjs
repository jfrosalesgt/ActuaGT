// Maintained test for the ratification escalation policy.
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  HIGH_PRIORITY,
  RATIFICATION_THRESHOLD,
  countValidRatifications,
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

test("counts ratifications with photo evidence and description", () => {
  assert.equal(
    countValidRatifications(
      [
        { userUid: "a", photoUrl: "https://example/a.jpg", comment: "Lo vi" },
        { userUid: "b", photoUrl: "https://example/b.jpg", comment: "Confirmado" },
        { userUid: "c", photoUrl: "https://example/c.jpg", comment: "Igual aquí" },
      ],
      "author"
    ),
    3
  );
});

test("ignores ratifications without photo, without comment, or from the author", () => {
  assert.equal(
    countValidRatifications(
      [
        { userUid: "a", photoUrl: "", comment: "Lo vi" },
        { userUid: "b", photoUrl: "https://example/b.jpg", comment: "   " },
        { userUid: "author", photoUrl: "https://example/c.jpg", comment: "Mío" },
        { userUid: "d", photoUrl: "https://example/d.jpg", comment: "Confirmado" },
      ],
      "author"
    ),
    1
  );
});

test("counts a repeated ratifier only once", () => {
  assert.equal(
    countValidRatifications(
      [
        { userUid: "a", photoUrl: "https://example/1.jpg", comment: "Lo vi" },
        { userUid: "a", photoUrl: "https://example/2.jpg", comment: "Otra vez" },
      ],
      "author"
    ),
    1
  );
});
