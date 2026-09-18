/**
 * Paystack payment backend.
 *
 * Secret key lives ONLY here, in Firebase config:
 *   firebase functions:config:set paystack.secret_key="sk_test_xxx" paystack.public_key="pk_test_xxx"
 * (or with the newer env syntax:)
 *   firebase functions:secrets:set PAYSTACK_SECRET_KEY
 *
 * Flow:
 *   1. App calls initializePayment({ product, jobId?, email, name })  -> access_code
 *   2. App shows Paystack PaymentSheet with that access_code
 *   3. App calls verifyPayment({ reference }) on Completed
 *      -> Function re-checks with Paystack API (amount + status) and only then
 *         grants the entitlement in Firestore (boost / subscription / application unlock).
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/v2/params");
const admin = require("firebase-admin");
const https = require("https");

admin.initializeApp();
const db = admin.firestore();

/**
 * Real push via FCM. Reads tokens from users/{uid}.fcmTokens (array) and
 * devices/{contact}.tokens (array); sends to whichever exist. Fire-and-forget.
 */
async function sendPush({ uid, contact, title, body, type }) {
  const tokens = new Set();
  try {
    if (uid) {
      const snap = await db.collection("users").doc(uid).get();
      (snap.get("fcmTokens") || []).forEach((t) => tokens.add(t));
    }
    if (contact) {
      const dsnap = await db
        .collection("devices")
        .doc(String(contact).trim().toLowerCase())
        .get();
      (dsnap.get("tokens") || []).forEach((t) => tokens.add(t));
    }
  } catch (e) {
    console.warn("sendPush token lookup failed:", e.message);
  }
  if (tokens.size === 0) return;
  const message = {
    tokens: [...tokens],
    notification: { title, body },
    data: { type: type || "general", title, body },
    android: { priority: "high" },
  };
  try {
    const res = await admin.messaging().sendEachForMulticast(message);
    // Drop tokens that are no longer valid.
    const dead = res.responses
      .map((r, i) => (r.error && i) || null)
      .filter(Boolean);
    if (dead.length) {
      const deadTokens = dead.map((i) => message.tokens[i]);
      console.log("pruning dead push tokens", deadTokens.length);
      if (uid) {
        db.collection("users").doc(uid).update({
          fcmTokens: admin.firestore.FieldValue.arrayRemove(...deadTokens),
        }).catch(() => {});
      }
    }
  } catch (e) {
    console.warn("sendPush failed:", e.message);
  }
}

/**
 * Mirror of the in-app bell: whenever a notification doc is written to the
 * notifications collection, push it to the recipient's devices as a real
 * system notification. Works for every producer (client bell events, sweeps,
 * payment verification) because they all write here.
 */
exports.onNotificationCreated = onDocumentCreated(
  "notifications/{id}",
  async (event) => {
    const n = event.data?.data() || {};
    const contact = n.recipientContact || "";
    if (!contact) return;
    // Resolve the uid so we can also use tokens stored on the user doc.
    let uid = null;
    try {
      const usnap = await db
        .collection("users")
        .where("contact", "==", contact)
        .limit(1)
        .get();
      if (!usnap.empty) uid = usnap.docs[0].id;
    } catch (_) {}
    await sendPush({
      uid,
      contact,
      title: n.title || "PRC Jobs",
      body: n.body || "",
      type: n.type || "general",
    });
  }
);

const PAYSTACK_SECRET_KEY = defineSecret("PAYSTACK_SECRET_KEY");

// ---- Product catalog (keep in sync with the Android BillingCatalog) ----
const PRODUCTS = {
  boost_3d:    { kind: "boost",   amount: 50000,  days: 3,  label: "Featured Boost — 3 days" },
  boost_7d:    { kind: "boost",   amount: 100000, days: 7,  label: "Featured Boost — 7 days" },
  app_unlock:  { kind: "unlock",  amount: 20000,  label: "Premium application unlock" },
  pro_monthly: { kind: "subscription", amount: 50000, days: 30, label: "Pro plan — monthly" },
  pro_yearly:  { kind: "subscription", amount: 500000, days: 365, label: "Pro plan — yearly" },
};

function paystackRequest(path, body) {
  return new Promise((resolve, reject) => {
    const payload = body ? JSON.stringify(body) : null;
    const req = https.request(
      {
        hostname: "api.paystack.co",
        path,
        method: payload ? "POST" : "GET",
        headers: {
          Authorization: `Bearer ${process.env.PAYSTACK_SECRET_KEY}`,
          "Content-Type": "application/json",
        },
      },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => {
          try {
            const json = JSON.parse(data);
            if (json.status) resolve(json.data);
            else reject(new Error(json.message || "Paystack request failed"));
          } catch (e) {
            reject(e);
          }
        });
      }
    );
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

/** Step 1: initialize a transaction for a product. Returns access_code for the PaymentSheet. */
exports.initializePayment = onCall(
  { secrets: [PAYSTACK_SECRET_KEY], region: "us-central1" },
  async (req) => {
    if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
    const { productId, jobId, email, name } = req.data || {};
    const product = PRODUCTS[productId];
    if (!product) throw new HttpsError("invalid-argument", `Unknown product: ${productId}`);
    if (product.kind === "boost" && !jobId)
      throw new HttpsError("invalid-argument", "Boost requires a jobId.");
    if (!email) throw new HttpsError("invalid-argument", "Email required.");

    const uid = req.auth.uid;
    const ref = `prc_${uid.slice(0, 8)}_${Date.now()}`;

    const init = await paystackRequest("/transaction/initialize", {
      email,
      amount: product.amount,           // kobo
      reference: ref,
      currency: "KES",
      metadata: { uid, productId, jobId: jobId || null, kind: product.kind },
    });

    // Record intent before the sheet opens.
    await db.collection("payments").doc(ref).set({
      uid, productId, kind: product.kind, jobId: jobId || null,
      amount: product.amount, currency: "KES",
      status: "initialized", email,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { accessCode: init.access_code, reference: ref, publicKeyPublic: "set-in-app" };
  }
);

/** Step 3: verify after PaymentSheet Completed; grant entitlement only server-side. */
exports.verifyPayment = onCall(
  { secrets: [PAYSTACK_SECRET_KEY], region: "us-central1" },
  async (req) => {
    if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
    const { reference } = req.data || {};
    if (!reference) throw new HttpsError("invalid-argument", "reference required");
    if (req.auth.uid) {
      const payDoc = await db.collection("payments").doc(reference).get();
      if (payDoc.exists && payDoc.data().uid !== req.auth.uid)
        throw new HttpsError("permission-denied", "Not your transaction.");
    }

    const tx = await paystackRequest(`/transaction/verify/${encodeURIComponent(reference)}`);
    if (tx.status !== "success")
      throw new HttpsError("failed-precondition", `Payment not successful (${tx.status}).`);

    const payRef = db.collection("payments").doc(reference);
    const paySnap = await payRef.get();
    const pay = paySnap.data() || {};
    const product = PRODUCTS[pay.productId];
    if (product && tx.amount < product.amount)
      throw new HttpsError("failed-precondition", "Amount mismatch.");

    await payRef.update({ status: "success", verifiedAt: admin.firestore.FieldValue.serverTimestamp() });

    const uid = pay.uid;
    const now = Date.now();
    if (pay.kind === "boost" && pay.jobId) {
      await db.collection("jobs").doc(`job_${pay.jobId}`).update({
        featuredUntil: now + product.days * 86400000,
        featuredPlan: pay.productId,
      });
    } else if (pay.kind === "unlock") {
      await db.collection("users").doc(uid).set(
        { applicationUnlocks: admin.firestore.FieldValue.increment(1) },
        { merge: true }
      );
    } else if (pay.kind === "subscription") {
      const userRef = db.collection("users").doc(uid);
      const userSnap = await userRef.get();
      const current = userSnap.get("proUntil")?.toMillis?.() || 0;
      const base = Math.max(current, now); // stack time on an active plan
      await userRef.set(
        { proUntil: base + product.days * 86400000, proPlan: pay.productId },
        { merge: true }
      );
    }

    return { status: "success", kind: pay.kind, productId: pay.productId };
  }
);

// =====================================================================
// Scheduled: job deadline expiry — runs server-side every 6 hours so
// listings auto-close (and admins get reminded) even when no device
// with the app installed is online.
//
// Behavior (mirrors the client worker, but authoritative):
//   1. Provider job has a deadline (deadlineDays) and it has elapsed
//      -> closed = true (removed from the public feed everywhere).
//   2. Provider job has NO deadline and has been live 14+ days
//      -> one-time reminder notification for the admin to review/delete.
// =====================================================================
exports.deadlineSweep = onSchedule(
  {
    schedule: "every 6 hours",
    region: "us-central1",
    timeZone: "Africa/Nairobi",
  },
  async (event) => {
    const now = Date.now();
    let closed = 0;
    let reminded = 0;
    let warned = 0;

    const jobs = await db
      .collection("jobs")
      .where("source", "==", "provider")
      .get();

    const batch = db.batch();
    const reminders = [];
    const warnings = [];

    jobs.forEach((doc) => {
      const data = doc.data() || {};
      const postedAt = data.postedAtMillis || 0;
      if (!postedAt) return;
      // Client worker already auto-closed this one; skip (belt & braces).
      if (data.closed === true) return;
      if (data.draft === true) return;

      const deadline = data.deadlineDays; // may be undefined/null
      if (typeof deadline === "number" && deadline > 0) {
        const expiresAt = postedAt + deadline * 86400000;
        // 1) hard deadline reached -> auto-close
        if (now >= expiresAt) {
          batch.update(doc.ref, {
            closed: true,
            closedReason: "deadline_reached",
            closedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
          closed++;
        } else if (
          now >= expiresAt - 2 * 86400000 &&
          data.expiryWarningSent !== true
        ) {
          // 1b) deadline within 2 days -> warn admin once so they can extend
          batch.update(doc.ref, { expiryWarningSent: true });
          const daysLeft = Math.max(
            1,
            Math.ceil((expiresAt - now) / 86400000)
          );
          warnings.push({
            title: data.title || "A job",
            provider: data.postedBy || "",
            daysLeft,
          });
          warned++;
        }
      } else if (deadline === undefined || deadline === null) {
        // 2) no written deadline + 14 days live -> remind admin once
        const remindedAlready = data.expiryReminderSent === true;
        if (!remindedAlready && now >= postedAt + 14 * 86400000) {
          batch.update(doc.ref, { expiryReminderSent: true });
          reminders.push({
            title: data.title || "A job",
            provider: data.postedBy || "",
          });
          reminded++;
        }
      }
    });

    await batch.commit();

    // Admin bell notifications for the reminder path.
    for (const r of reminders) {
      await db.collection("notifications").add({
        recipientContact: "admin@prc.app",
        title: "Job past 2 weeks — review",
        body: `"${r.title}"${r.provider ? " from " + r.provider : ""} had no deadline and has been live for 2+ weeks. Delete it from the portal if it should no longer appear.`,
        type: "admin",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    // Admin bell notifications for the 2-day expiry warning path.
    for (const w of warnings) {
      await db.collection("notifications").add({
        recipientContact: "admin@prc.app",
        title: `Job expires in ${w.daysLeft} day${w.daysLeft === 1 ? "" : "s"}`,
        body: `"${w.title}"${w.provider ? " from " + w.provider : ""} reaches its deadline in about ${w.daysLeft} day${w.daysLeft === 1 ? "" : "s"}. Extend it from the portal if it should stay open — otherwise it closes automatically.`,
        type: "admin",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    console.log(
      `deadlineSweep: scanned=${jobs.size} closed=${closed} reminded=${reminded} warned=${warned}`
    );
    return null;
  }
);
