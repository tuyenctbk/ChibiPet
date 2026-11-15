package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private val Context.stickerDataStore: DataStore<Preferences> by preferencesDataStore(name = "chibipet_stickers")

data class SavedSticker(
    val id: String,
    val fileName: String,
    val prompt: String,
    val createdAtMillis: Long
)

class StickerGalleryStore(private val context: Context) {

    private val appContext = context.applicationContext
    private val stickersDir: File
        get() = File(appContext.filesDir, "stickers").apply { mkdirs() }

    val stickersFlow: Flow<List<SavedSticker>> = appContext.stickerDataStore.data.map { prefs ->
        parseManifest(prefs[KEY_MANIFEST_JSON])
    }

    suspend fun saveSticker(bitmap: Bitmap, prompt: String): SavedSticker = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().take(12)
        val fileName = "sticker_${System.currentTimeMillis()}_$id.png"
        val targetFile = File(stickersDir, fileName)
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        val entry = SavedSticker(
            id = id,
            fileName = fileName,
            prompt = prompt.trim().take(200),
            createdAtMillis = System.currentTimeMillis()
        )
        appContext.stickerDataStore.edit { prefs ->
            val current = parseManifest(prefs[KEY_MANIFEST_JSON]).toMutableList()
            current.add(0, entry)
            if (current.size > MAX_SAVED_STICKERS) {
                val overflow = current.subList(MAX_SAVED_STICKERS, current.size).toList()
                overflow.forEach { old -> File(stickersDir, old.fileName).delete() }
                current.subList(MAX_SAVED_STICKERS, current.size).clear()
            }
            prefs[KEY_MANIFEST_JSON] = encodeManifest(current)
        }
        entry
    }

    suspend fun deleteSticker(sticker: SavedSticker) = withContext(Dispatchers.IO) {
        File(stickersDir, sticker.fileName).delete()
        appContext.stickerDataStore.edit { prefs ->
            val updated = parseManifest(prefs[KEY_MANIFEST_JSON]).filterNot { it.id == sticker.id }
            prefs[KEY_MANIFEST_JSON] = encodeManifest(updated)
        }
    }

    suspend fun loadBitmap(fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(stickersDir, fileName)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    fun getShareableUri(fileName: String): Uri? {
        val file = File(stickersDir, fileName)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    private fun parseManifest(raw: String?): List<SavedSticker> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val fileName = obj.optString("fileName")
                    if (fileName.isNotBlank() && File(stickersDir, fileName).exists()) {
                        add(
                            SavedSticker(
                                id = obj.optString("id"),
                                fileName = fileName,
                                prompt = obj.optString("prompt"),
                                createdAtMillis = obj.optLong("createdAtMillis", 0L)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeManifest(items: List<SavedSticker>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("prompt", item.prompt)
                    put("createdAtMillis", item.createdAtMillis)
                }
            )
        }
        return array.toString()
    }

    private companion object {
        val KEY_MANIFEST_JSON = stringPreferencesKey("stickers_manifest_json")
        const val MAX_SAVED_STICKERS = 20
    }
}
