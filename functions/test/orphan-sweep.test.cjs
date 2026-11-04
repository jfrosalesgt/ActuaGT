// Maintained test for the orphan photo sweep contract.
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  findOrphanPhotoNames,
  objectPathFromUrl,
} = require("../lib/orphan-sweep.js");

const DAY = 24 * 60 * 60 * 1000;
const NOW = 1_700_000_000_000;

test("objectPathFromUrl extracts the path from a download URL", () => {
  assert.equal(
    objectPathFromUrl(
      "https://firebasestorage.googleapis.com/v0/b/actuagt-22679.appspot.com/o/report_photos%2Fpending_1%2Fphoto-0.jpg?alt=media&token=abc",
    ),
    "report_photos/pending_1/photo-0.jpg",
  );
});

test("objectPathFromUrl extracts the path from a gs URL", () => {
  assert.equal(
    objectPathFromUrl("gs://actuagt-22679.appspot.com/report_photos/pending_1/photo-0.jpg"),
    "report_photos/pending_1/photo-0.jpg",
  );
});

test("objectPathFromUrl returns null for plain strings", () => {
  assert.equal(objectPathFromUrl("not a url"), null);
});

test("findOrphanPhotoNames deletes only old unreferenced report photos", () => {
  const files = [
    { name: "report_photos/pending_old/photo-0.jpg", updatedMillis: NOW - 2 * DAY },
    { name: "report_photos/pending_new/photo-0.jpg", updatedMillis: NOW - DAY + 1000 },
    { name: "report_photos/pending_live/photo-0.jpg", updatedMillis: NOW - 2 * DAY },
    { name: "official_requests/u1/credencial.jpg", updatedMillis: NOW - 30 * DAY },
  ];
  const referenced = new Set(["report_photos/pending_live/photo-0.jpg"]);
  assert.deepEqual(findOrphanPhotoNames(files, referenced, NOW - DAY), [
    "report_photos/pending_old/photo-0.jpg",
  ]);
});

test("findOrphanPhotoNames never deletes when the age is unknown", () => {
  const files = [{ name: "report_photos/pending_x/photo-0.jpg", updatedMillis: NaN }];
  assert.deepEqual(findOrphanPhotoNames(files, new Set(), NOW - DAY), []);
});
