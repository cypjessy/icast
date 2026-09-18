package com.prc.app.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Firebase Storage uploads for application CVs.
 *
 * Files land at cv/{uid}/{jobId}_{timestamp}_{fileName} so each application's
 * CV is versioned and never overwritten. On success the download URL is
 * handed back to be persisted with the application document in Firestore.
 * All failures degrade gracefully (null URL) — the application still saves
 * with just the filename.
 */
object CvStorage {

    /** @return public download URL, or null if upload failed / unavailable. */
    suspend fun uploadApplicationCv(jobId: Int, uri: Uri, fileName: String): String? = try {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val storage = try { FirebaseStorage.getInstance() } catch (_: Exception) { return null }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val path = "cv/$uid/${jobId}_${System.currentTimeMillis()}_$safeName"
        val url = storage.reference.child(path)
            .putFile(uri)
            .await()
            .storage
            .downloadUrl
            .await()
        android.util.Log.d("PRC-Cv", "CV uploaded: $path -> $url")
        url.toString()
    } catch (e: Exception) {
        android.util.Log.w("PRC-Cv", "CV upload failed: ${e.message}")
        null
    }

    /** Best-effort open of a stored CV via an ACTION_VIEW intent. */
    fun openCv(url: String, context: android.content.Context) {
        if (url.isBlank()) return
        try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            )
        } catch (e: Exception) {
            android.util.Log.w("PRC-Cv", "no browser/viewer for CV url: ${e.message}")
        }
    }
}
