package com.hdclark.diysoundboard

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SoundStorage {
    private const val PREFS_NAME = "soundboard_prefs"
    private const val KEY_BUTTONS = "buttons"
    private const val TAG = "SoundStorage"

    /**
     * Returns true when [fileName] is safe to use as a bare file name:
     * non-empty, no path separators, no parent-directory sequences, and the
     * expected `.m4a` extension.
     */
    private fun isValidAudioFileName(fileName: String): Boolean =
        fileName.isNotEmpty() &&
            !fileName.contains('/') &&
            !fileName.contains('\\') &&
            !fileName.contains("..") &&
            fileName.endsWith(".m4a", ignoreCase = true)

    fun loadButtons(context: Context): MutableList<SoundButton> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BUTTONS, null) ?: return mutableListOf()
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stored buttons JSON; returning empty list", e)
            return mutableListOf()
        }
        val list = mutableListOf<SoundButton>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                list.add(
                    SoundButton(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        audioFileName = obj.getString("audioFileName")
                    )
                )
            } catch (e: Exception) {
                // Skip this entry rather than wiping the entire list.
                Log.w(TAG, "Skipping malformed button entry at index $i", e)
            }
        }
        return list
    }

    fun saveButtons(context: Context, buttons: List<SoundButton>) {
        val array = JSONArray()
        for (button in buttons) {
            val obj = JSONObject()
            obj.put("id", button.id)
            obj.put("label", button.label)
            obj.put("audioFileName", button.audioFileName)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BUTTONS, array.toString())
            .apply()
    }

    fun getAudioFile(context: Context, fileName: String): File =
        File(context.filesDir, fileName)

    fun deleteAudioFile(context: Context, fileName: String) {
        if (fileName.isNotEmpty()) {
            getAudioFile(context, fileName).delete()
        }
    }

    fun exportToZip(context: Context): File {
        val buttons = loadButtons(context)
        val exportDir = File(context.cacheDir, "export")
        exportDir.deleteRecursively()
        exportDir.mkdirs()

        for (button in buttons) {
            if (button.audioFileName.isNotEmpty()) {
                if (!isValidAudioFileName(button.audioFileName)) {
                    Log.w(TAG, "Skipping export for button: audioFileName failed validation")
                    continue
                }
                val src = getAudioFile(context, button.audioFileName)
                if (src.exists()) {
                    src.copyTo(File(exportDir, button.audioFileName), overwrite = true)
                }
            }
        }

        val metadataFile = File(exportDir, "metadata.json")
        val array = JSONArray()
        for (button in buttons) {
            val obj = JSONObject()
            obj.put("id", button.id)
            obj.put("label", button.label)
            obj.put("audioFileName", button.audioFileName)
            array.put(obj)
        }
        metadataFile.writeText(array.toString())

        val zipFile = File(context.cacheDir, "soundboard_export.zip")
        zipFile.delete()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            exportDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zipFile
    }
}
