// Pure push helpers: topic naming matches the Android client exactly,
// and sends fan out through the Admin SDK (which bypasses client rules).
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";

export function topicForZone(zone: string, suffix: "ratify" | "officials"): string {
  const normalized = zone
    .toLowerCase()
    .split("")
    .map((char) => (/[a-z0-9]/.test(char) ? char : "_"))
    .join("");
  return `zone_${normalized}_${suffix}`;
}

async function tokensForUser(
  db: ReturnType<typeof getFirestore>,
  uid: string
): Promise<string[]> {
  const snapshot = await db.collection("users").doc(uid).collection("devices").get();
  return snapshot.docs
    .map((doc) => (doc.data() as { token?: unknown }).token)
    .filter((token): token is string => typeof token === "string" && token.length > 0);
}

export async function notifyUser(
  uid: string,
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
  const db = getFirestore();
  const tokens = await tokensForUser(db, uid);
  if (tokens.length === 0) return;
  const messaging = getMessaging();
  const response = await messaging.sendEachForMulticast({ tokens, notification: { title, body }, data });
  if (response.failureCount > 0) {
    logger.warn(`Push to user ${uid} had ${response.failureCount} failures.`);
  }
}

export async function notifyTopic(
  topic: string,
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
  await getMessaging().send({ topic, notification: { title, body }, data });
}

export async function notifyZoneOfficials(
  zone: string,
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
  const db = getFirestore();
  const officials = await db
    .collection("users")
    .where("role", "==", "OFFICIAL")
    .where("zone", "==", zone)
    .get();
  const uids = new Set(
    officials.docs.map((doc) => (doc.data() as { uid?: unknown }).uid ?? doc.id)
  );
  let tokens: string[] = [];
  for (const uid of uids) {
    if (typeof uid === "string" && uid.length > 0) {
      tokens = tokens.concat(await tokensForUser(db, uid));
    }
  }
  if (tokens.length === 0) return;
  const response = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data,
  });
  if (response.failureCount > 0) {
    logger.warn(`Push to officials of ${zone} had ${response.failureCount} failures.`);
  }
}
