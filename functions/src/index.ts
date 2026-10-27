// ActuaGT Cloud Functions: backend triggers for the citizen-report pipeline.
// Convention: code and comments in English.

import { initializeApp } from "firebase-admin/app";
import { logger } from "firebase-functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

initializeApp();

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

// Phase 4 scaffold: reads the report text and photo references of every new
// pending report and logs them. AI verification lands here in a later phase.
export const onpendingreportcreated = onDocumentCreated(
  "pending_reports/{reportId}",
  (event) => {
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
  }
);
