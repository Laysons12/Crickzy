package com.example.crickzy.utils

import android.util.Log

/**
 * Utility for safely executing Supabase (network) calls.
 * Catches all exceptions, logs them, and returns null on failure.
 *
 * Usage:
 *   val result = safeSupabaseCall("addMatch") { SupabaseHelper.addMatch(match) }
 */
suspend fun <T> safeSupabaseCall(
    tag: String = "SupabaseCall",
    block: suspend () -> T
): T? {
    return try {
        block()
    } catch (e: Exception) {
        Log.e("SafeSupabase", "$tag failed: ${e.message}", e)
        null
    }
}
