package dev.tvsopen.transport

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dev.tvsopen.log.Log

/**
 * Observes telephony call states and notifies when the phone rings or when the
 * call ends/clears. Resolves contact names using ContactsContract.
 */
class CallObserver(
    private val context: Context,
    private val onIncomingCall: (number: String, name: String?) -> Unit,
    private val onCallEnded: () -> Unit,
) {
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            Log.d(TAG, "phone state broadcast: $stateStr (number=${incomingNumber ?: "unknown"})")
            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    handleRinging(incomingNumber)
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK,
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    onCallEnded()
                }
            }
        }
    }

    fun start() {
        if (isRegistered) return
        isRegistered = true

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "failed to register phone state receiver: ${e.message}")
        }

        telephonyManager?.let { tm ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            if (state == TelephonyManager.CALL_STATE_IDLE ||
                                state == TelephonyManager.CALL_STATE_OFFHOOK
                            ) {
                                onCallEnded()
                            }
                        }
                    }
                    telephonyCallback = cb
                    tm.registerTelephonyCallback(context.mainExecutor, cb)
                } else {
                    @Suppress("DEPRECATION")
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                            if (state == TelephonyManager.CALL_STATE_RINGING) {
                                handleRinging(incomingNumber)
                            } else {
                                onCallEnded()
                            }
                        }
                    }
                    phoneStateListener = listener
                    @Suppress("DEPRECATION")
                    tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                }
            } catch (e: Exception) {
                Log.w(TAG, "failed to register telephony callback: ${e.message}")
            }
        }
    }

    private fun handleRinging(number: String?) {
        val num = number?.takeIf { it.isNotBlank() } ?: "Incoming"
        val name = resolveContactName(num)
        Log.i(TAG, "incoming call from '$num' ($name)")
        onIncomingCall(num, name)
    }

    private fun resolveContactName(number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    fun stop() {
        if (!isRegistered) return
        isRegistered = false
        runCatching { context.unregisterReceiver(receiver) }
        telephonyManager?.let { tm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
                telephonyCallback = null
            } else {
                phoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    tm.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
        }
    }

    companion object {
        private const val TAG = "CallObserver"
    }
}
