// Step 1: Install required packages
// npm install express cloudinary dotenv firebase-admin

const express = require('express');
const cloudinary = require('cloudinary').v2;
const admin = require('firebase-admin'); // For Firebase Admin SDK
require('dotenv').config();

const app = express();
app.use(express.json()); // Middleware to parse JSON bodies
const PORT = process.env.PORT || 3000;

// --- FIREBASE ADMIN INITIALIZATION ---
try {
    const serviceAccountJson = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
    serviceAccountJson.private_key = serviceAccountJson.private_key.replace(/\\n/g, '\n');

    admin.initializeApp({
        credential: admin.credential.cert(serviceAccountJson)
    });
    console.log("Firebase Admin initialized:", admin.apps.length > 0);
} catch (error) {
    console.error("Firebase Admin initialization failed. Check FIREBASE_SERVICE_ACCOUNT_JSON environment variable.", error);
}

const db = admin.firestore();

// --- CLOUDINARY CONFIGURATION ---
cloudinary.config({
    cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
    api_key: process.env.CLOUDINARY_API_KEY,
    api_secret: process.env.CLOUDINARY_API_SECRET
});

// A. ENDPOINT 1: CLOUDINARY SIGNATURE (ENHANCED FOR STATUS MEDIA)
// ----------------------------------------------------------------------
app.post('/cloudinary-auth', (req, res) => {
    const { public_id, resource_type } = req.body;

    if (!public_id) {
        return res.status(400).send({ error: "Missing public_id in request body." });
    }

    const timestamp = Math.round((new Date).getTime() / 1000);

    // ✨ ENHANCED: Support resource_type for videos/images
    const params_to_sign = {
        public_id: public_id,
        timestamp: timestamp,
        upload_preset: 'ml_default'
    };

    // Add resource_type to signature if specified (optional but recommended)
    if (resource_type) {
        params_to_sign.resource_type = resource_type;
    }

    const signature = cloudinary.utils.api_sign_request(
        params_to_sign,
        cloudinary.config().api_secret
    );

    res.status(200).send({
        signature: signature,
        timestamp: timestamp,
        cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
        api_key: process.env.CLOUDINARY_API_KEY,
        upload_preset: 'ml_default'
    });
});

// ----------------------------------------------------------------------
// B. ENDPOINT 2: FCM NOTIFICATION TRIGGER
// ----------------------------------------------------------------------
app.post('/send-fcm-notification', async (req, res) => {
    const { recipientId, senderId, messageText, chatId } = req.body;

    if (!recipientId || !senderId || !messageText) {
        return res.status(400).send({ error: "Missing required fields (recipientId, senderId, messageText)." });
    }

    try {
        // 1. Check Recipient Presence
        const presenceSnap = await db.collection('presence').doc(recipientId).get();
        const isOnline = presenceSnap.data()?.online === true;

        if (isOnline) {
            console.log(`User ${recipientId} is online. Not sending FCM.`);
            return res.status(200).send({ status: "online", message: "Recipient is online, notification skipped." });
        }

        // 2. Get Recipient FCM Token and Sender Name
        const recipientSnap = await db.collection('users').doc(recipientId).get();
        const senderSnap = await db.collection('users').doc(senderId).get();

        const recipientToken = recipientSnap.data()?.fcmToken;
        const senderName = senderSnap.data()?.name || "Someone";

        if (!recipientToken) {
            console.log(`No FCM token found for user ${recipientId}.`);
            return res.status(200).send({ status: "no_token", message: "User offline but no FCM token found." });
        }

        // 3. Construct and Send the HIGH PRIORITY Data Message Payload
        const payload = {
            token: recipientToken,
            data: {
                chatId: chatId || 'N/A',
                senderId: senderId,
                title: senderName,
                body: messageText,
                type: 'CHAT_MESSAGE',
            },
            android: {
                priority: 'high',
            },
        };

        const fcmResponse = await admin.messaging().send(payload);
        console.log("FCM message successfully sent:", fcmResponse);

        return res.status(200).send({ status: "success", message: "Notification sent.", fcmId: fcmResponse.messageId });

    } catch (error) {
        console.error("Error in FCM notification endpoint:", error);
        return res.status(500).send({ error: "Internal server error during notification process." });
    }
});

// ----------------------------------------------------------------------
// C. ENDPOINT 3: CLOUDINARY MEDIA DELETION
// ----------------------------------------------------------------------
app.post('/cloudinary-delete', async (req, res) => {
    const { public_id, resource_type } = req.body;

    if (!public_id) {
        return res.status(400).send({ error: "Missing public_id in request body." });
    }

    try {
        // Delete from Cloudinary using the admin API
        // ✨ ENHANCED: Support resource_type for deleting videos
        const result = await cloudinary.uploader.destroy(public_id, {
            resource_type: resource_type || 'image', // "image", "video", or "auto"
            invalidate: true // Invalidate CDN cache
        });

        if (result.result === 'ok' || result.result === 'not found') {
            console.log(`Cloudinary deletion successful for public_id: ${public_id}`, result);
            return res.status(200).send({
                status: "success",
                message: `Media deleted: ${public_id}`,
                result: result
            });
        } else {
            console.error(`Cloudinary deletion failed for public_id: ${public_id}`, result);
            return res.status(500).send({
                error: "Deletion failed",
                details: result
            });
        }
    } catch (error) {
        console.error("Error deleting from Cloudinary:", error);
        return res.status(500).send({
            error: "Internal server error during Cloudinary deletion.",
            details: error.message
        });
    }
});

// --- START SERVER ---
app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server is running on port ${PORT}`);
    console.log(`Cloudinary cloud: ${process.env.CLOUDINARY_CLOUD_NAME}`);
});
