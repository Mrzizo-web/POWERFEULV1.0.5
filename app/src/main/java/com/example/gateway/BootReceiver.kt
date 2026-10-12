package com.example.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Automatically recover the background gateway service after device restart
            try {
                GatewayService.startGateway(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
