package com.example.dialerrecorderpoc

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseService : FirebaseMessagingService() {

    // Called when a push arrives from our backend
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // The backend sends the customer number in the "data" part of the push
        val number = message.data["customerNumber"]
        Log.d("FCM", "Push received. customerNumber = $number")

        if (!number.isNullOrEmpty()) {
            placeCall(number)
        }
    }

    // Auto-dial via the native dialer (same approach proven earlier)
    private fun placeCall(number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                // Required because we're starting a call from outside an Activity
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d("FCM", "Auto-dialing $number")
        } catch (e: SecurityException) {
            Log.e("FCM", "CALL_PHONE permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("FCM", "Dial error: ${e.message}")
        }
    }

    // Called when this device gets its FCM token (its unique push address)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New device token: $token")
        // (We'll send this token to the backend in Stage 4)
    }
}