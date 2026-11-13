package com.northerngo.duopaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Brygga mellan webbappen (duopaper.northerngo.com) och Android.
 *
 * @param background true när sidan körs dolt av [WallpaperSyncWorker] (appen stängd).
 * @param onSyncDone anropas när sidan har synkat klart i bakgrundsläge.
 */
class AndroidBridge(
    context: Context,
    private val background: Boolean = false,
    private val onSyncDone: ((String) -> Unit)? = null
) {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Webbappen frågar om den körs som dold bakgrundssynk. */
    @JavascriptInterface
    fun isBackground(): Boolean = background

    /** Webbappen meddelar att bakgrundssynken är klar ("ok" eller "error"). */
    @JavascriptInterface
    fun syncDone(status: String) {
        onSyncDone?.invoke(status)
    }

    /**
     * Sätter bakgrundsbild. JavascriptInterface-anrop körs på en egen bakgrundstråd,
     * så arbetet görs synkront här. Då är bilden satt innan sidan anropar [syncDone].
     */
    @JavascriptInterface
    fun setWallpaper(base64Image: String, targetScreen: String) {
        try {
            // Rensa bort eventuellt data-URI-prefix (t.ex. "data:image/jpeg;base64,")
            val cleanBase64 = if (base64Image.contains(",")) {
                base64Image.substringAfter(",")
            } else {
                base64Image
            }

            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val bitmap: Bitmap? = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap == null) {
                toast("Kunde inte avkoda bilden.", Toast.LENGTH_SHORT)
                return
            }

            // Välj målskärm baserat på inkommande sträng
            val flags = when (targetScreen.trim().lowercase()) {
                "lock", "låsskärm", "endast låsskärm" -> WallpaperManager.FLAG_LOCK
                "home", "hemskärm", "endast hemskärm" -> WallpaperManager.FLAG_SYSTEM
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }

            WallpaperManager.getInstance(appContext).setBitmap(bitmap, null, true, flags)
            Log.i(TAG, "Bakgrund satt (target=$targetScreen, background=$background)")
            toast("Bakgrundsbild uppdaterad!", Toast.LENGTH_SHORT)
        } catch (e: Exception) {
            Log.e(TAG, "Fel vid byte av bakgrund", e)
            toast("Fel vid byte av bakgrund: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    // Inga toasts från bakgrundssynken – den nya bakgrunden är beskedet.
    private fun toast(message: String, length: Int) {
        if (background) return
        mainHandler.post { Toast.makeText(appContext, message, length).show() }
    }

    private companion object {
        const val TAG = "DuoPaperBridge"
    }
}
