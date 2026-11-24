const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.sendIssueStatusPush = onDocumentCreated("notifications/{notifId}", async (event) => {
  const data = event.data?.data();
  if (!data || !data.userId) return;

  const userDoc = await getFirestore().collection("users").doc(data.userId).get();
  const fcmToken = userDoc.get("fcmToken");
  if (!fcmToken) return;

  await getMessaging().send({
    token: fcmToken,
    notification: {
      title: `Issuegram: ${data.status || "Status Updated"}`,
      body: `${data.ticketId || ""}: ${data.message || data.issueTitle || "Your issue was updated"}`,
    },
    data: {
      ticketId: String(data.ticketId || ""),
      status: String(data.status || ""),
    },
  });
});
