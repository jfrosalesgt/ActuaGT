// One-time bootstrap for the first ActuaGT admin. Run locally once, then
// delete or keep for emergencies only. Requires Google Cloud credentials
// with Firebase Admin rights:
//
//   gcloud auth application-default login
//   node scripts/grant-admin.mjs <firebase-auth-uid>
//
// It sets users/{uid}.role to ADMIN and the matching Auth custom claim, so
// the app Panel and the role functions recognize the admin without any
// hardcoded email.
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const uid = process.argv[2];
if (!uid) {
  console.error("Usage: node scripts/grant-admin.mjs <firebase-auth-uid>");
  process.exit(1);
}

initializeApp({ credential: applicationDefault() });

await getFirestore().collection("users").doc(uid).set(
  { role: "ADMIN", zone: "" },
  { merge: true }
);
await getAuth().setCustomUserClaims(uid, { admin: true });
console.log(`Admin granted to ${uid}. Sign out and sign back in on the device.`);
