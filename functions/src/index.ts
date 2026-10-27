// ActuaGT Cloud Functions: backend triggers for the citizen-report pipeline.
// Convention: code and comments in English.

import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { defineString } from "firebase-functions/params";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import {
  buildChatMessages,
  MUSE_API_URL,
  MUSE_MODEL,
  parseVerdict,
} from "./moderation";

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
  status?: string;
  createdAtEpochMillis?: number;
}

// Moderates every new pending report with Meta Muse AI. APROBADO moves the
// report to `active_reports`; RECHAZADO marks it rejected with the motive;
// anything else leaves it pending for manual review.
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

    let verdictText: string;
    try {
      const response = await fetch(MUSE_API_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${museApiKey.value()}`,
        },
        body: JSON.stringify({
          model: MUSE_MODEL,
          temperature: 0,
          max_tokens: 200,
          messages: buildChatMessages(
            {
              title: report.title ?? "",
              description: report.description ?? "",
              category: report.category ?? "",
            },
            photoUrls
          ),
        }),
      });
      if (!response.ok) {
        logger.error(`Muse API error ${response.status} for report ${reportId}.`);
        return;
      }
      const payload = (await response.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
      };
      verdictText = payload.choices?.[0]?.message?.content ?? "";
    } catch (error) {
      logger.error(`Muse API call failed for report ${reportId}.`, error);
      return;
    }

    const verdict = parseVerdict(verdictText);
    if (!verdict) {
      logger.error(`Unparseable verdict for report ${reportId}: ${verdictText}`);
      return;
    }

    const db = getFirestore();
    if (verdict.decision === "APROBADO") {
      const batch = db.batch();
      batch.set(db.collection("active_reports").doc(reportId), {
        ...report,
        status: "verified",
        moderationVerdict: "APROBADO",
        moderatedAt: Date.now(),
      });
      batch.delete(snapshot.ref);
      await batch.commit();
      logger.info(`Report ${reportId} approved and moved to active_reports.`);
    } else {
      await snapshot.ref.update({
        status: "rejected",
        rejectionReason: verdict.reason,
        moderatedAt: Date.now(),
      });
      logger.info(`Report ${reportId} rejected: ${verdict.reason}`);
    }
  }
);
