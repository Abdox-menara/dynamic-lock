package com.example.dynamiclock.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.dynamiclock.security.Crypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores private notes and photos, encrypted at rest in app-private storage. */
class VaultStore(context: Context) {

    private val appContext = context.applicationContext
    private val notesFile = File(appContext.filesDir, "notes.enc")
    private val photosDir = File(appContext.filesDir, "photos").apply { if (!exists()) mkdirs() }
    private val lock = Any()

    data class Note(val id: Long, val title: String, val body: String, val updated: Long)

    fun loadNotes(): List<Note> {
        val bytes = Crypto.readBytes(appContext, notesFile) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(String(bytes, Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(o.getLong("id"), o.optString("title"), o.optString("body"), o.optLong("updated"))
            }.sortedByDescending { it.updated }
        }.getOrDefault(emptyList())
    }

    private fun saveNotes(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("title", it.title); put("body", it.body); put("updated", it.updated)
            })
        }
        Crypto.writeBytes(appContext, notesFile, arr.toString().toByteArray(Charsets.UTF_8))
    }

    fun upsert(note: Note) {
        synchronized(lock) {
            val list = loadNotes().filter { it.id != note.id }.toMutableList()
            list.add(note)
            saveNotes(list)
        }
    }

    fun deleteNote(id: Long) = saveNotes(loadNotes().filter { it.id != id })

    fun getNote(id: Long): Note? = loadNotes().firstOrNull { it.id == id }

    // ---- Photos (each stored as an individually-encrypted file) ----

    fun listPhotos(): List<File> =
        (photosDir.listFiles()?.filter { it.isFile } ?: emptyList()).sortedByDescending { it.lastModified() }

    fun addPhoto(uri: Uri): Boolean = runCatching {
        val input = appContext.contentResolver.openInputStream(uri) ?: return false
        val bytes = input.use { it.readBytes() }
        val out = File(photosDir, "img_${System.currentTimeMillis()}.enc")
        Crypto.writeBytes(appContext, out, bytes)
        true
    }.getOrDefault(false)

    fun deletePhoto(file: File) = file.delete()

    /** Decodes a downsampled bitmap from an encrypted photo file. */
    fun decodeSampled(file: File, reqPx: Int): Bitmap? {
        val bytes = Crypto.readBytes(appContext, file) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        var half = maxOf(bounds.outWidth, bounds.outHeight) / 2
        while (half >= reqPx) { sample *= 2; half /= 2 }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
