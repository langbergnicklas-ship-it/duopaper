package com.northerngo.duopaper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Bakgrundssynk som körs ungefär var 15:e minut även när appen är stängd.
 *
 * Laddar webbappen i en dold WebView. Sidan ser via AndroidBridge.isBackground() att den
 * körs i bakgrunden, hämtar och dekrypterar partnerns senaste bild med samma kod och
 * nycklar som när appen är öppen, sätter bakgrunden och anropar AndroidBridge.syncDone().
 * Inget av krypteringen behöver alltså dupliceras i Kotlin.
 */
class WallpaperSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val status = try {
            runHeadlessSync()
        } catch (e: Exception) {
            Log.e(TAG, "Bakgrundssynk kraschade", e)
            "exception"
        }
        Log.i(TAG, "Bakgrundssynk klar: $status")
        // Periodiskt jobb: nästa körning sker ändå om 15 min, så ingen retry behövs.
        return Result.success()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runHeadlessSync(): String = withContext(Dispatchers.Main) {
        val mainHandler = Handler(Looper.getMainLooper())
        val webView = WebView(applicationContext)
        try {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true // samma localStorage (nycklar/parkod) som appen

            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<String> { cont ->
                    // Körs alltid på huvudtråden, så ingen dubbel resume kan ske
                    fun finish(status: String) {
                        if (cont.isActive) cont.resume(status)
                    }

                    webView.addJavascriptInterface(
                        AndroidBridge(applicationContext, background = true) { status ->
                            mainHandler.post { finish(status) }
                        },
                        "AndroidBridge"
                    )
                    webView.webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) finish("load-error")
                        }
                    }
                    webView.loadUrl(MainActivity.APP_URL)
                }
            } ?: "timeout"
        } finally {
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
    }

    companion object {
        private const val TAG = "DuoPaperSync"
        private const val WORK_NAME = "duopaper-wallpaper-sync"

        // Render-backenden kan behöva upp till ~1 min för att vakna
        private const val TIMEOUT_MS = 90_000L

        /** Schemalägger synken. Anropas vid varje appstart; KEEP behåller befintligt schema. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WallpaperSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
