// ActuaGT orphan photo sweep: deletes report_photos/ objects no report
// references anymore. Unconfirmed drafts used to upload at verification
// time, so abandoned verifies left files behind; the client now uploads
// only at submit, and this schedule removes the leftovers.
// Convention: code and comments in English.

import { getStorage } from "firebase-admin/storage";
import { getFirestore, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";

const REPORT_PHOTOS_PREFIX = "report_photos/";
// Files newer than this are kept even when unreferenced: a submit uploads
// first and writes its document right after, so the sweep never races it.
const ORPHAN_GRACE_MILLIS = 24 * 60 * 60 * 1000;
const MAX_DELETES_PER_RUN = 500;

export interface StoredPhoto {
  name: string;
  updatedMillis: number;
}

// Extracts the Storage object path from a download URL
// (https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<path>...)
// or a gs:// URL. Null when the string carries no object path.
export function objectPathFromUrl(url: string): string | null {
  const marker = "/o/";
  const at = url.indexOf(marker);
  if (at >= 0) {
    const encoded = url.slice(at + marker.length).split("?")[0];
    try {
      const path = decodeURIComponent(encoded);
      return path.length > 0 ? path : null;
    } catch {
      return null;
    }
  }
  if (url.startsWith("gs://")) {
    const slash = url.indexOf("/", "gs://".length);
    return slash >= 0 && slash + 1 < url.length ? url.slice(slash + 1) : null;
  }
  return null;
}

function collectPathsFromValue(value: unknown, into: Set<string>): void {
  if (typeof value === "string") {
    if (value.includes(REPORT_PHOTOS_PREFIX)) {
      const path = objectPathFromUrl(value);
      if (path !== null && path.startsWith(REPORT_PHOTOS_PREFIX)) into.add(path);
    }
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) collectPathsFromValue(item, into);
    return;
  }
  if (typeof value === "object" && value !== null) {
    for (const item of Object.values(value)) collectPathsFromValue(item, into);
  }
}

// Every photo URL the database still points at: pending drafts, published
// incidents (evidence and resolution proof) and ratification evidence.
// Defensive JSON walk instead of field lists, so a renamed photo field
// can never turn a live photo into an orphan.
export async function collectReferencedPhotoPaths(
  db: Firestore,
): Promise<Set<string>> {
  const referenced = new Set<string>();
  const pending = await db.collection("pending_reports").get();
  for (const doc of pending.docs) collectPathsFromValue(doc.data(), referenced);
  const incidents = await db.collection("incidents").get();
  for (const doc of incidents.docs) collectPathsFromValue(doc.data(), referenced);
  const ratifications = await db.collectionGroup("ratifications").get();
  for (const doc of ratifications.docs) collectPathsFromValue(doc.data(), referenced);
  return referenced;
}

// Pure core of the sweep, unit-tested: old, unreferenced report photos go,
// everything else stays. Unknown age (NaN) never deletes.
export function findOrphanPhotoNames(
  files: StoredPhoto[],
  referenced: Set<string>,
  cutoffMillis: number,
): string[] {
  return files
    .filter(
      (file) =>
        file.name.startsWith(REPORT_PHOTOS_PREFIX) &&
        Number.isFinite(file.updatedMillis) &&
        file.updatedMillis < cutoffMillis &&
        !referenced.has(file.name),
    )
    .map((file) => file.name)
    .slice(0, MAX_DELETES_PER_RUN);
}

// Daily sweep: report photos without a referencing document and older
// than the grace period are deleted. Runs with Admin SDK privileges.
export const sweepOrphanPhotos = onSchedule(
  { schedule: "every 24 hours", timeZone: "America/Guatemala" },
  async () => {
    const db = getFirestore();
    const referenced = await collectReferencedPhotoPaths(db);
    const bucket = getStorage().bucket();
    const [files] = await bucket.getFiles({ prefix: REPORT_PHOTOS_PREFIX });
    const cutoff = Date.now() - ORPHAN_GRACE_MILLIS;
    const stored: StoredPhoto[] = files.map((file) => {
      const updated = file.metadata?.updated;
      return {
        name: file.name,
        updatedMillis: typeof updated === "string" ? Date.parse(updated) : NaN,
      };
    });
    const orphans = findOrphanPhotoNames(stored, referenced, cutoff);
    await Promise.all(
      orphans.map((name) => bucket.file(name).delete({ ignoreNotFound: true })),
    );
    logger.info(
      `Photo sweep: ${stored.length} stored, ${referenced.size} referenced, ${orphans.length} deleted.`,
    );
  },
);
