// ActuaGT Cloud Functions: backend triggers for the citizen-report pipeline.
// Convention: code and comments in English.

import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { defineString } from "firebase-functions/params";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  buildChatMessages,
  buildSystemPrompt,
  ChatMessage,
  MUSE_API_URL,
  MUSE_MODEL,
  parseTriageResponse,
  TriageResult,
} from "./moderation";
import {
  buildTranscriptionMessages,
  parseCleanedTranscription,
} from "./transcription";
import { canRespond, isProofAccepted } from "./official";
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

initializeApp();

// Set with: firebase functions:secrets:set MUSE_API_KEY
const museApiKey = defineString("MUSE_API_KEY");

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
        max_tokens: 300,
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
      max_tokens: 300,
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

    const response = await fetch(MUSE_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${museApiKey.value()}`,
      },
      body: JSON.stringify({
        model: MUSE_MODEL,
        temperature: 0,
        max_tokens: 300,
        messages: buildTranscriptionMessages(transcript),
      }),
    });
    if (!response.ok) {
      logger.error(`Muse API error ${response.status} during transcription cleanup.`);
      throw new HttpsError("unavailable", "The cleanup service is unavailable.");
    }
    const payload = (await response.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    const cleaned = parseCleanedTranscription(
      payload.choices?.[0]?.message?.content ?? ""
    );
    if (!cleaned) {
      throw new HttpsError("unavailable", "The cleanup service returned an unusable answer.");
    }
    if (cleaned.kind === "security_alert") return { kind: "security_alert" };
    return { kind: "clean", text: cleaned.text };
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
    const profile = (await db.collection("users").doc(uid).get()).data() as
      | { role?: string; zone?: string }
      | undefined;
    if (profile?.role !== "OFFICIAL" || !profile.zone) {
      throw new HttpsError("permission-denied", "Only approved officials can respond.");
    }
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
    if (!report?.zone || report.zone !== profile.zone) {
      throw new HttpsError("permission-denied", "The report is outside your zone.");
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
