package com.hdclark.diysoundboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SoundStorage {
    private const val PREFS_NAME = "soundboard_prefs"
    private const val KEY_BUTTONS = "buttons"

    fun loadButtons(context: Context): MutableList<SoundButton> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BUTTONS, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<SoundButton>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SoundButton(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        audioFileName = obj.getString("audioFileName")
                    )
                )
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
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
                val src = getAudioFile(context, button.audioFileName)
                if (src.exists()) {
                    src.copyTo(File(exportDir, button.audioFileName))
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
