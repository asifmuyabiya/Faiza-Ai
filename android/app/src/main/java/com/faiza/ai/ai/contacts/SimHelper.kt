package com.faiza.ai.ai.contacts

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/** Dual-SIM calling (video 32:43): lists the phone's SIMs and builds a call on a chosen one. */
object SimHelper {

    data class Sim(val slot: Int, val label: String, val handle: PhoneAccountHandle)

    /** SIMs that can make calls, numbered 1, 2… Empty without the Phone permission. */
    @SuppressLint("MissingPermission")
    fun sims(ctx: Context): List<Sim> {
        val tm = ctx.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return try {
            tm.callCapablePhoneAccounts.mapIndexed { i, handle ->
                val label = tm.getPhoneAccount(handle)?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${i + 1}"
                Sim(i + 1, label, handle)
            }
        } catch (_: Exception) {
            emptyList() // no Phone permission, or no telephony on this device
        }
    }

    fun describe(sims: List<Sim>): String = sims.joinToString(", ") { "SIM ${it.slot}: ${it.label}" }

    /** ACTION_CALL for [number], on SIM [slot] when given and present (else the phone's default). */
    fun callIntent(ctx: Context, number: String, slot: Int?): Intent {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
        if (slot != null && slot > 0) {
            sims(ctx).firstOrNull { it.slot == slot }?.let {
                intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it.handle)
            }
        }
        return intent
    }
}
