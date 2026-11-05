// ActuaGT Cloud Functions: backend triggers for the citizen-report pipeline.
// Convention: code and comments in English.

import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { defineString } from "firebase-functions/params";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  buildChatMessages,
  buildCoherenceMessages,
  buildSystemPrompt,
  ChatMessage,
  MUSE_API_URL,
  MUSE_MODEL,
  parseCoherenceVerdict,
  parseTriageResponse,
  TriageResult,
} from "./moderation";
import {
  buildTranscriptionMessages,
  parseCleanedTranscription,
} from "./transcription";
import {
  canActOnZone,
  canRespond,
  isAdminClaim,
  isProofAccepted,
  OfficialProfile,
} from "./official";
import {
  notifyTopic,
  notifyUser,
  notifyZoneOfficials,
  topicForZone,
} from "./notifications";
import {
  countValidRatifications,
  HIGH_PRIORITY,
  RatificationEvidence,
  shouldEscalate,
} from "./escalation";
import {
  buildSamRequest,
  damageDocumentFragment,
  DamageSegmentation,
  parseSamResponse,
} from "./sam";
export { sweepOrphanPhotos } from "./orphan-sweep";

initializeApp();

// Set with: firebase functions:secrets:set MUSE_API_KEY
const museApiKey = defineString("MUSE_API_KEY");
// Set with: firebase functions:secrets:set SAM_API_URL  (hosted SAM 3.1 endpoint)
// Optional: firebase functions:secrets:set SAM_API_TOKEN (bearer for that endpoint)
const samApiUrl = defineString("SAM_API_URL", { default: "" });
const samApiToken = defineString("SAM_API_TOKEN", { default: "" });

// Shape of the documents written by the KMP client to `pending_reports`.
interface PendingReport {
  title?: string;
  description?: string;
  category?: string;
  photoUrls?: string[];
  reportedByUid?: string;
  zone?: string;
  status?: string;
  createdAtEpochMillis?: number;
}

// Calls the Meta triage engine once; on an unusable (non-JSON) answer it
// retries once with a format reminder. Null means manual review.
async function requestTriage(
  report: PendingReport,
  photoUrls: string[]
): Promise<TriageResult | null> {
  const messages: ChatMessage[] = buildChatMessages(
    {
      title: report.title ?? "",
      description: report.description ?? "",
      category: report.category ?? "",
    },
    photoUrls
  );
  for (let attempt = 0; attempt < 2; attempt++) {
    const attemptMessages =
      attempt === 0
        ? messages
        : [
            ...messages,
            {
              role: "user",
              content:
                "Tu respuesta anterior no fue un objeto JSON válido. Responde ÚNICAMENTE con el objeto JSON indicado, sin texto adicional.",
            },
          ];
    const response = await fetch(MUSE_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${museApiKey.value()}`,
      },
      body: JSON.stringify({
        model: MUSE_MODEL,
        temperature: 0,
        max_tokens: 2000,
        messages: attemptMessages,
      }),
    });
    if (!response.ok) {
      logger.error(`Muse API error ${response.status}.`);
      return null;
    }
    const payload = (await response.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    const parsed = parseTriageResponse(payload.choices?.[0]?.message?.content);
    if (parsed) return parsed;
    logger.warn("Unparseable triage answer; retrying with format reminder.");
  }
  return null;
}

// Verifies the finished-work proof: the original photos come first, the
// proof photo last. The same triage engine must approve it under the
// report's own official category.
async function verifyResolutionProof(
  report: { title?: string; description?: string; category?: string; photoUrls?: string[] },
  proofPhotoUrl: string
): Promise<TriageResult | null> {
  const originals = report.photoUrls ?? [];
  const content: unknown[] = [
    {
      type: "text",
      text:
        `Reporte original — Título: ${report.title ?? ""}. ` +
        `Descripción: ${report.description ?? ""}. ` +
        `Las primeras imágenes son la evidencia original del problema; la última imagen es la prueba de obra terminada. ` +
        `Confirma que muestran el MISMO lugar ya reparado: sin el problema visible, sin capturas ni arte digital.`,
    },
  ];
  for (const url of [...originals, proofPhotoUrl]) {
    content.push({ type: "image_url", image_url: { url } });
  }
  const response = await fetch(MUSE_API_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${museApiKey.value()}`,
    },
    body: JSON.stringify({
      model: MUSE_MODEL,
      temperature: 0,
      max_tokens: 2000,
      messages: [
        { role: "system", content: buildSystemPrompt() },
        { role: "user", content },
      ],
    }),
  });
  if (!response.ok) {
    logger.error(`Muse API error ${response.status} during proof verification.`);
    return null;
  }
  const payload = (await response.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  return parseTriageResponse(payload.choices?.[0]?.message?.content ?? "");
}

// Calls the hosted SAM 3.1 endpoint once. Null when unconfigured,
// unreachable, or unusable: segmentation enriches the report but never
// blocks moderation, so failures degrade to "no damage overlay".
async function requestSamSegmentation(
  photoUrls: string[],
  category: string | undefined
): Promise<DamageSegmentation | null> {
  const endpoint = samApiUrl.value().trim();
  if (!endpoint) return null;
  try {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    const token = samApiToken.value().trim();
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(endpoint, {
      method: "POST",
      headers,
      body: JSON.stringify(buildSamRequest(photoUrls, category)),
    });
    if (!response.ok) {
      logger.warn(`SAM API error ${response.status}; continuing without segmentation.`);
      return null;
    }
    return parseSamResponse(await response.text(), category);
  } catch (error) {
    logger.warn("SAM API call failed; continuing without segmentation.", error);
    return null;
  }
}

// Moderates every new pending report with Meta Muse AI. APPROVED moves the
// report to `incidents` in `in_ratification` (never public yet); REJECTED
// marks it rejected with the motive; anything else leaves it pending for
// manual review.
export const onpendingreportcreated = onDocumentCreated(
  "pending_reports/{reportId}",
  async (event) => {
    const reportId = event.params.reportId;
    const snapshot = event.data;
    if (!snapshot) {
      logger.warn(`Empty event received for pending report ${reportId}.`);
      return;
    }
    const report = snapshot.data() as PendingReport;
    logger.info(`New pending report ${reportId} received.`, {
      title: report.title ?? "(untitled)",
      description: report.description ?? "(no description)",
      category: report.category ?? "(uncategorized)",
      reportedByUid: report.reportedByUid ?? "(anonymous)",
      photoCount: report.photoUrls?.length ?? 0,
      photoUrls: report.photoUrls ?? [],
    });

    const photoUrls = report.photoUrls ?? [];
    if (photoUrls.length === 0) {
      await snapshot.ref.update({
        status: "rejected",
        rejectionReason: "El reporte no incluye fotografía.",
        moderatedAt: Date.now(),
      });
      logger.info(`Report ${reportId} rejected: no photo attached.`);
      return;
    }

    let verdict: TriageResult | null;
    try {
      verdict = await requestTriage(report, photoUrls);
    } catch (error) {
      logger.error(`Muse API call failed for report ${reportId}.`, error);
      return;
    }
    if (!verdict) {
      logger.error(`Unparseable triage verdict for report ${reportId}; left pending for manual review.`);
      return;
    }

    const db = getFirestore();
    if (verdict.status === "APPROVED") {
      const batch = db.batch();
      batch.set(db.collection("incidents").doc(reportId), {
        ...report,
        title: verdict.suggestedTitle ?? report.title ?? "",
        category: verdict.category,
        status: "in_ratification",
        moderationVerdict: "APPROVED",
        validRatificationsCount: 0,
        moderatedAt: Date.now(),
      });
      batch.delete(snapshot.ref);
      await batch.commit();
      logger.info(`Report ${reportId} approved and moved to incidents for ratification.`);

      // Backend-auto SAM: enrich the approved incident with damage boxes.
      // Best-effort only: a missing endpoint or a failed call leaves the
      // incident exactly as moderation approved it.
      try {
        const segmentation = await requestSamSegmentation(photoUrls, verdict.category);
        const fragment = damageDocumentFragment(segmentation);
        if (fragment) {
          await db.collection("incidents").doc(reportId).update(fragment);
          logger.info(`Report ${reportId} enriched with SAM damage overlay.`);
        }
      } catch (error) {
        logger.warn(`SAM enrichment failed for report ${reportId}.`, error);
      }
      const data = { reportId };
      if (report.reportedByUid) {
        await notifyUser(
          report.reportedByUid,
          "Reporte en validación",
          `Tu reporte "${verdict.suggestedTitle ?? report.title ?? ""}" necesita 3 ratificaciones para publicarse.`,
          data
        );
      }
      if (report.zone) {
        await notifyTopic(
          topicForZone(report.zone, "ratify"),
          "Nuevo caso por validar",
          `Un reporte en tu zona necesita 3 ratificaciones con foto.`,
          data
        );
      }
    } else {
      await snapshot.ref.update({
        status: "rejected",
        category: verdict.category,
        rejectionReason: verdict.reason,
        moderatedAt: Date.now(),
      });
      logger.info(`Report ${reportId} rejected: ${verdict.reason}`);
      if (report.reportedByUid) {
        await notifyUser(
          report.reportedByUid,
          "Reporte no publicado",
          `Tu reporte fue rechazado: ${verdict.reason}`,
          { reportId }
        );
      }
    }
  }
);

// Neutralises a raw voice transcript with the Meta transcription assistant.
// The client shows the clean text for confirmation and never persists the
// raw transcript. A security topic returns `security_alert` and must not be
// submitted as an infrastructure report.
export const enhanceReportText = onCall(
  { maxInstances: 10 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in is required.");
    }
    const transcript = (request.data as { transcript?: unknown } | null)?.transcript;
    if (typeof transcript !== "string" || transcript.trim().length === 0) {
      throw new HttpsError("invalid-argument", "A non-empty transcript is required.");
    }
    if (transcript.length > 2000) {
      throw new HttpsError("invalid-argument", "The transcript is too long.");
    }

    // The model reasons at length before answering: allow room and retry
    // once with a format reminder when the answer is unusable.
    const baseMessages = buildTranscriptionMessages(transcript);
    for (let attempt = 0; attempt < 2; attempt++) {
      const messages =
        attempt === 0
          ? baseMessages
          : [
              ...baseMessages,
              {
                role: "user",
                content:
                  "Tu respuesta anterior no fue un objeto JSON válido. Responde ÚNICAMENTE con el objeto JSON indicado, sin texto adicional.",
              },
            ];
      const response = await fetch(MUSE_API_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${museApiKey.value()}`,
        },
        body: JSON.stringify({
          model: MUSE_MODEL,
          temperature: 0,
          max_tokens: 4000,
          messages,
        }),
      });
      if (!response.ok) {
        logger.error(`Muse API error ${response.status} during transcription cleanup.`);
        throw new HttpsError("unavailable", "The cleanup service is unavailable.");
      }
      const payload = (await response.json()) as {
        choices?: Array<{
          message?: { content?: string };
          finish_reason?: string;
        }>;
      };
      const raw = payload.choices?.[0]?.message?.content ?? "";
      logger.info(
        `Transcription attempt ${attempt + 1}: finish=${payload.choices?.[0]?.finish_reason ?? "unknown"}, chars=${raw.length}.`
      );
      const cleaned = parseCleanedTranscription(raw);
      if (cleaned) {
        if (cleaned.kind === "security_alert") return { kind: "security_alert" };
        return { kind: "clean", title: cleaned.title, text: cleaned.text };
      }
      logger.warn("Unparseable transcription answer; retrying with format reminder.");
    }
    throw new HttpsError("unavailable", "The cleanup service returned an unusable answer.");
  }
);

// Pre-submit coherence check: the client uploads the photos first, then
// asks whether they show the reported problem. A mismatch blocks the
// submit so the citizen can fix photos or text before moderation.
export const verifyReportCoherence = onCall(
  { maxInstances: 10 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in is required.");
    }
    const data = (request.data as {
      title?: unknown;
      description?: unknown;
      photoUrls?: unknown;
    } | null) ?? {};
    const title = typeof data.title === "string" ? data.title.trim() : "";
    const description = typeof data.description === "string" ? data.description.trim() : "";
    const photoUrls = Array.isArray(data.photoUrls)
      ? data.photoUrls.filter((url): url is string => typeof url === "string" && url.length > 0)
      : [];
    if (!title || !description) {
      throw new HttpsError("invalid-argument", "Title and description are required.");
    }
    if (photoUrls.length < 1 || photoUrls.length > 4) {
      throw new HttpsError("invalid-argument", "Between 1 and 4 photo URLs are required.");
    }

    const response = await fetch(MUSE_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${museApiKey.value()}`,
      },
      body: JSON.stringify({
        model: MUSE_MODEL,
        temperature: 0,
        max_tokens: 2000,
        messages: buildCoherenceMessages(title, description, photoUrls),
      }),
    });
    if (!response.ok) {
      logger.error(`Muse API error ${response.status} during coherence check.`);
      throw new HttpsError("unavailable", "The verification service is unavailable.");
    }
    const payload = (await response.json()) as {
      choices?: Array<{
        message?: { content?: string };
        finish_reason?: string;
      }>;
    };
    const raw = payload.choices?.[0]?.message?.content ?? "";
    logger.info(
      `Coherence check finish=${payload.choices?.[0]?.finish_reason ?? "unknown"}, chars=${raw.length}, photos=${photoUrls.length}.`
    );
    const verdict = parseCoherenceVerdict(raw);
    if (!verdict) {
      logger.warn(`Unparseable coherence answer (chars=${raw.length}).`);
      throw new HttpsError("unavailable", "The verification service returned an unusable answer.");
    }
    logger.info(`Coherence verdict match=${verdict.match}.`);
    return { match: verdict.match, reason: verdict.reason };
  }
);

// On-demand SAM segmentation for the client: returns damage boxes plus a
// score without writing anything. Lets the app show the overlay (and warn
// on "no visible damage") before the user submits. Unconfigured endpoint
// fails with `unavailable` so the client falls back to the SAM-free flow.
export const segmentDamage = onCall(
  { maxInstances: 10 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in is required.");
    }
    const data = (request.data as {
      photoUrls?: unknown;
      category?: unknown;
    } | null) ?? {};
    const photoUrls = Array.isArray(data.photoUrls)
      ? data.photoUrls.filter((url): url is string => typeof url === "string" && url.length > 0)
      : [];
    const category = typeof data.category === "string" ? data.category : "OTROS";
    if (photoUrls.length < 1 || photoUrls.length > 4) {
      throw new HttpsError("invalid-argument", "Between 1 and 4 photo URLs are required.");
    }
    if (!samApiUrl.value().trim()) {
      throw new HttpsError("unavailable", "The segmentation service is not configured.");
    }
    const segmentation = await requestSamSegmentation(photoUrls, category);
    if (!segmentation) {
      throw new HttpsError("unavailable", "The segmentation service returned an unusable answer.");
    }
    return {
      boxes: segmentation.boxes,
      score: segmentation.score,
      labels: segmentation.labels,
    };
  }
);

// Role administration, admin-only: approving an official request writes the
// OFFICIAL profile and fixes the matching custom claims; rejecting only
// marks the request. Client roles stay immutable in the security rules.
export const setOfficialRole = onCall(
  { maxInstances: 10 },
  async (request) => {
    if (!isAdminClaim(request.auth?.token)) {
      throw new HttpsError("permission-denied", "Only admins can manage roles.");
    }
    const input = (request.data ?? {}) as {
      uid?: unknown;
      zone?: unknown;
      institution?: unknown;
      approved?: unknown;
      reason?: unknown;
    };
    if (typeof input.uid !== "string" || input.uid.length === 0) {
      throw new HttpsError("invalid-argument", "A user id is required.");
    }
    if (typeof input.approved !== "boolean") {
      throw new HttpsError("invalid-argument", "An approval decision is required.");
    }

    const db = getFirestore();
    const requestRef = db.collection("official_requests").doc(input.uid);
    const stored = (await requestRef.get()).data() as
      | { status?: string }
      | undefined;
    if (!stored || stored.status !== "pending") {
      throw new HttpsError("failed-precondition", "There is no pending request for this user.");
    }

    if (!input.approved) {
      await requestRef.update({
        status: "rejected",
        reviewReason: typeof input.reason === "string" ? input.reason.trim() : "",
        reviewedAt: Date.now(),
        reviewedBy: request.auth?.uid ?? null,
      });
      logger.info(`Official request for ${input.uid} rejected.`);
      return { ok: true };
    }

    const zone = typeof input.zone === "string" ? input.zone.trim() : "";
    const institution = typeof input.institution === "string" ? input.institution.trim() : "";
    if (!zone || !institution) {
      throw new HttpsError("invalid-argument", "Approving requires zone and institution.");
    }
    const batch = db.batch();
    batch.update(db.collection("users").doc(input.uid), {
      role: "OFFICIAL",
      zone,
      institution,
    });
    batch.update(requestRef, {
      status: "approved",
      reviewedAt: Date.now(),
      reviewedBy: request.auth?.uid ?? null,
    });
    await getAuth().setCustomUserClaims(input.uid, { official: true, zone });
    await batch.commit();
    logger.info(`User ${input.uid} approved as official for zone ${zone}.`);
    return { ok: true };
  }
);

// Official action verified server-side: only an OFFICIAL profile whose zone
// matches the report can write to its public timeline or move its status.
// Resolution requires the finished-work proof photo URL (automated visual
// verification of the proof is the planned next iteration).
export const officialRespond = onCall(
  { maxInstances: 10 },
  async (request) => {
    const uid = request.auth?.uid;
    const provider = (request.auth?.token as { firebase?: { sign_in_provider?: string } } | undefined)
      ?.firebase?.sign_in_provider;
    if (!uid || provider === "anonymous") {
      throw new HttpsError("unauthenticated", "An identified official session is required.");
    }
    const input = (request.data ?? {}) as {
      reportId?: unknown;
      message?: unknown;
      status?: unknown;
      proofPhotoUrl?: unknown;
      manualReview?: unknown;
      reviewReason?: unknown;
    };
    if (typeof input.reportId !== "string" || input.reportId.length === 0) {
      throw new HttpsError("invalid-argument", "A report id is required.");
    }
    const message = typeof input.message === "string" ? input.message.trim() : "";
    const manualReview =
      input.manualReview === "approved" || input.manualReview === "rejected"
        ? input.manualReview
        : undefined;
    const reviewReason =
      typeof input.reviewReason === "string" ? input.reviewReason.trim() : "";
    const targetStatus =
      input.status === "in_progress" || input.status === "resolved"
        ? input.status
        : undefined;
    const proofPhotoUrl =
      typeof input.proofPhotoUrl === "string" && input.proofPhotoUrl.length > 0
        ? input.proofPhotoUrl
        : undefined;
    if (!message && !targetStatus && !manualReview) {
      throw new HttpsError("invalid-argument", "A message, a status change, or a manual review is required.");
    }
    if (targetStatus === "resolved" && !proofPhotoUrl) {
      throw new HttpsError("invalid-argument", "Resolving requires the finished-work photo.");
    }
    if (manualReview === "rejected" && !reviewReason) {
      throw new HttpsError("invalid-argument", "Rejecting a proof requires a reason.");
    }

    const db = getFirestore();
    const isAdmin = isAdminClaim(request.auth?.token);
    const profile = (await db.collection("users").doc(uid).get()).data() as
      | OfficialProfile
      | undefined;
    const parentRef = db.collection("incidents").doc(input.reportId);
    const parent = await parentRef.get();
    if (!parent.exists) {
      throw new HttpsError("not-found", "The report does not exist.");
    }
    const report = parent.data() as
      | {
          title?: string;
          description?: string;
          category?: string;
          photoUrls?: string[];
          reportedByUid?: string;
          resolvedPhotoUrl?: string;
          proofReview?: string;
          zone?: string;
          status?: string;
        }
      | undefined;
    if (!canActOnZone(profile, report?.zone, isAdmin)) {
      throw new HttpsError("permission-denied", "The report is outside your zone.");
    }
    if (!report) {
      throw new HttpsError("not-found", "The report does not exist.");
    }
    if (!canRespond(report.status, targetStatus)) {
      throw new HttpsError("failed-precondition", "That status change is not allowed now.");
    }
    if (manualReview && report.proofReview !== "needs_review") {
      throw new HttpsError("failed-precondition", "There is no proof awaiting review.");
    }
    if (manualReview === "approved" && report.status !== "published" && report.status !== "in_progress") {
      throw new HttpsError("failed-precondition", "Only a published case can be resolved.");
    }

    // Manual resolution of a proof the model could not verify on its own.
    if (manualReview === "approved") {
      const batch = db.batch();
      batch.set(parentRef.collection("report_updates").doc(), {
        reportId: input.reportId,
        authorUid: uid,
        message: message || "Prueba aprobada en revisión manual.",
        status: "resolved",
        proofPhotoUrl: report.resolvedPhotoUrl ?? null,
        createdAtEpochMillis: Date.now(),
      });
      batch.update(parentRef, {
        status: "resolved",
        proofReview: "approved",
        proofReviewReason: null,
        respondedAt: Date.now(),
      });
      await batch.commit();
      logger.info(`Official ${uid} manually approved the proof for report ${input.reportId}.`);
      return { ok: true };
    }
    if (manualReview === "rejected") {
      const batch = db.batch();
      batch.set(parentRef.collection("report_updates").doc(), {
        reportId: input.reportId,
        authorUid: uid,
        message: `Prueba rechazada en revisión manual: ${reviewReason}`,
        status: null,
        proofPhotoUrl: null,
        createdAtEpochMillis: Date.now(),
      });
      batch.update(parentRef, {
        proofReview: null,
        proofReviewReason: reviewReason,
        respondedAt: Date.now(),
      });
      await batch.commit();
      logger.info(`Official ${uid} manually rejected the proof for report ${input.reportId}.`);
      return { ok: true };
    }

    if (targetStatus === "resolved") {
      const proof = await verifyResolutionProof(report, proofPhotoUrl as string);
      if (!isProofAccepted(proof, report.category)) {
        // Persist the doubt instead of failing silently: the official queue
        // picks it up for manual review.
        const reason = proof?.reason
          ? `La foto no demuestra la reparación: ${proof.reason}`
          : "La foto no demuestra la reparación del mismo lugar.";
        const batch = db.batch();
        batch.set(parentRef.collection("report_updates").doc(), {
          reportId: input.reportId,
          authorUid: uid,
          message,
          status: null,
          proofPhotoUrl: proofPhotoUrl ?? null,
          createdAtEpochMillis: Date.now(),
        });
        batch.update(parentRef, {
          resolvedPhotoUrl: proofPhotoUrl,
          proofReview: "needs_review",
          proofReviewReason: reason,
          respondedAt: Date.now(),
        });
        await batch.commit();
        if (report.reportedByUid) {
          await notifyUser(
            report.reportedByUid,
            "Tu caso sigue en revisión",
            "La foto de obra terminada quedó en revisión manual.",
            { reportId: input.reportId }
          );
        }
        throw new HttpsError("failed-precondition", `${reason} Quedó en revisión manual.`);
      }
    }

    const batch = db.batch();
    batch.set(parentRef.collection("report_updates").doc(), {
      reportId: input.reportId,
      authorUid: uid,
      message,
      status: targetStatus ?? null,
      proofPhotoUrl: proofPhotoUrl ?? null,
      createdAtEpochMillis: Date.now(),
    });
    const parentUpdates: Record<string, unknown> = { respondedAt: Date.now() };
    if (targetStatus) parentUpdates.status = targetStatus;
    if (targetStatus === "resolved") {
      parentUpdates.resolvedPhotoUrl = proofPhotoUrl;
      parentUpdates.proofReview = "approved";
      parentUpdates.proofReviewReason = null;
    }
    batch.update(parentRef, parentUpdates);
    await batch.commit();
    logger.info(`Official ${uid} responded to report ${input.reportId}.`);

    const pushData = { reportId: input.reportId };
    const pushBody = message
      ? `Respuesta oficial: ${message.slice(0, 120)}`
      : targetStatus === "resolved"
        ? "El reporte fue marcado como obra terminada."
        : "Tu reporte cambió de estado.";
    if (report.reportedByUid) {
      await notifyUser(report.reportedByUid, "Actualización de tu reporte", pushBody, pushData);
    }
    const ratifiers = await parentRef.collection("ratifications").get();
    const ratifierUids = new Set(
      ratifiers.docs
        .map((doc) => (doc.data() as { userUid?: unknown }).userUid)
        .filter((candidate): candidate is string => typeof candidate === "string")
        .filter((candidate) => candidate !== report.reportedByUid)
    );
    for (const ratifier of ratifierUids) {
      await notifyUser(ratifier, "Actualización de un reporte que validaste", pushBody, pushData);
    }
    return { ok: true };
  }
);

// Daily reminder: proofs stuck in manual review for over 72 hours ping the
// officials of their zone again. Nothing is auto-approved.
export const remindStaleProofReviews = onSchedule(
  { schedule: "every 24 hours", timeZone: "America/Guatemala" },
  async () => {
    const db = getFirestore();
    const cutoff = Date.now() - 72 * 60 * 60 * 1000;
    const snapshot = await db.collection("incidents").where("proofReview", "==", "needs_review").get();
    const zones = new Set<string>();
    for (const doc of snapshot.docs) {
      const data = doc.data() as { zone?: string; respondedAt?: number };
      if (typeof data.zone === "string" && (data.respondedAt ?? 0) < cutoff) {
        zones.add(data.zone);
      }
    }
    for (const zone of zones) {
      await notifyZoneOfficials(
        zone,
        "Pruebas por revisar",
        "Hay fotos de obra terminada esperando tu revisión manual.",
        {}
      );
    }
  }
);

// Publication gate: only ratifications with photo evidence and a written
// description count, one per user. At the threshold the report becomes
// public (published, high priority); before that it waits in ratification.
export const onratificationcreated = onDocumentCreated(
  "incidents/{reportId}/ratifications/{ratificationId}",
  async (event) => {
    const { reportId } = event.params;
    const db = getFirestore();
    const parentRef = db.collection("incidents").doc(reportId);

    const parent = await parentRef.get();
    if (!parent.exists) {
      logger.warn(`Ratification for missing report ${reportId} ignored.`);
      return;
    }
    const parentData = parent.data() as
      | { reportedByUid?: string; status?: string; zone?: string; title?: string }
      | undefined;
    const snapshot = await parentRef.collection("ratifications").get();
    const validCount = countValidRatifications(
      snapshot.docs.map((doc) => doc.data() as RatificationEvidence),
      parentData?.reportedByUid
    );

    // The dashboard orders by this counter, so it is persisted on every
    // ratification; publication happens only at the valid threshold.
    const updates: Record<string, unknown> = {
      ratificationsCount: validCount,
      validRatificationsCount: validCount,
    };
    const alreadyPublished = parentData?.status === "published";
    if (!alreadyPublished && shouldEscalate(validCount, undefined)) {
      updates.status = "published";
      updates.priority = HIGH_PRIORITY;
      updates.publishedAt = Date.now();
    }
    await parentRef.update(updates);
    logger.info(`Report ${reportId} has ${validCount} valid ratifications.`);
    const ratifyData = { reportId };
    if (parentData?.reportedByUid && updates.status !== "published") {
      await notifyUser(
        parentData.reportedByUid,
        "Tu reporte suma validaciones",
        `Lleva ${validCount} de 3 ratificaciones para publicarse.`,
        ratifyData
      );
    }
    if (updates.status === "published") {
      logger.info(`Report ${reportId} published after reaching the ratification threshold.`);
      const title = parentData?.title ?? "reporte";
      if (parentData?.reportedByUid) {
        await notifyUser(
          parentData.reportedByUid,
          "Tu reporte fue publicado",
          `Tu reporte ya es visible para todos y llegó a los funcionarios.`,
          ratifyData
        );
      }
      if (parentData?.zone) {
        await notifyTopic(
          topicForZone(parentData.zone, "officials"),
          "Nuevo caso publicado en tu zona",
          title,
          ratifyData
        );
      }
    }
  }
);


