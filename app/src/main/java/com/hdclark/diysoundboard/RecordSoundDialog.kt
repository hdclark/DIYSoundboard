package com.hdclark.diysoundboard

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import java.io.File
import java.util.UUID

class RecordSoundDialog : DialogFragment() {

    interface Listener {
        fun onSoundSaved(label: String, audioFileName: String)
    }

    private var listener: Listener? = null
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var tempFile: File? = null
    private var hasRecording = false

    private var existingLabel: String = ""
    private var existingAudioFileName: String = ""

    companion object {
        fun newInstance(label: String = "", audioFileName: String = ""): RecordSoundDialog {
            val dialog = RecordSoundDialog()
            dialog.arguments = Bundle().apply {
                putString("label", label)
                putString("audioFileName", audioFileName)
            }
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        existingLabel = arguments?.getString("label") ?: ""
        existingAudioFileName = arguments?.getString("audioFileName") ?: ""
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_record_sound, null)

        val labelEdit = view.findViewById<EditText>(R.id.edit_label)
        val recordButton = view.findViewById<Button>(R.id.btn_record)
        val playButton = view.findViewById<Button>(R.id.btn_play)
        val statusText = view.findViewById<TextView>(R.id.tv_status)

        labelEdit.setText(existingLabel)

        tempFile = File(requireContext().cacheDir, "temp_rec_${UUID.randomUUID()}.m4a")

        if (existingAudioFileName.isNotEmpty()) {
            val existing = SoundStorage.getAudioFile(requireContext(), existingAudioFileName)
            if (existing.exists()) {
                existing.copyTo(tempFile!!, overwrite = true)
                hasRecording = true
                statusText.text = getString(R.string.status_loaded)
                playButton.isEnabled = true
            }
        } else {
            playButton.isEnabled = false
        }

        recordButton.setOnClickListener {
            if (!isRecording) startRecording(statusText, recordButton, playButton)
            else stopRecording(statusText, recordButton, playButton)
        }

        playButton.setOnClickListener { playRecording(statusText) }

        return AlertDialog.Builder(requireContext())
            .setTitle(if (existingAudioFileName.isEmpty()) getString(R.string.add_sound) else getString(R.string.edit_sound))
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cleanupTemp() }
            .create()
    }

    override fun onStart() {
        super.onStart()
        val alertDialog = dialog as? AlertDialog
        alertDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val labelEdit = alertDialog.findViewById<EditText>(R.id.edit_label) ?: return@setOnClickListener
            val label = labelEdit.text.toString().trim()
            when {
                label.isEmpty() ->
                    Toast.makeText(requireContext(), R.string.error_no_label, Toast.LENGTH_SHORT).show()
                !hasRecording ->
                    Toast.makeText(requireContext(), R.string.error_no_recording, Toast.LENGTH_SHORT).show()
                else -> {
                    saveAndReturn(label)
                    dismiss()
                }
            }
        }
    }

    private fun startRecording(statusText: TextView, recordButton: Button, playButton: Button) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), R.string.error_no_permission, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(tempFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordButton.text = getString(R.string.stop)
            playButton.isEnabled = false
            statusText.text = getString(R.string.status_recording)
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            recordButton.text = getString(R.string.record)
            playButton.isEnabled = hasRecording
            statusText.text = getString(R.string.error_recording_failed, e.message)
        }
    }

    private fun stopRecording(statusText: TextView, recordButton: Button, playButton: Button) {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            hasRecording = true
            recordButton.text = getString(R.string.record_again)
            playButton.isEnabled = true
            statusText.text = getString(R.string.status_recorded)
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            statusText.text = getString(R.string.error_stop_failed, e.message)
        }
    }

    private fun playRecording(statusText: TextView) {
        if (!hasRecording || tempFile?.exists() != true) return
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile!!.absolutePath)
                prepare()
                start()
                setOnCompletionListener { completedPlayer ->
                    statusText.text = getString(R.string.status_playback_done)
                    completedPlayer.release()
                    if (mediaPlayer === completedPlayer) {
                        mediaPlayer = null
                    }
                }
            }
            statusText.text = getString(R.string.status_playing)
        } catch (e: Exception) {
            statusText.text = getString(R.string.error_playback_failed, e.message)
        }
    }

    private fun saveAndReturn(label: String) {
        stopRecordingIfActive()
        val fileName = "${UUID.randomUUID()}.m4a"
        val dest = SoundStorage.getAudioFile(requireContext(), fileName)
        tempFile?.copyTo(dest, overwrite = true)
        // Delete the old audio file if we are replacing it
        if (existingAudioFileName.isNotEmpty()) {
            SoundStorage.deleteAudioFile(requireContext(), existingAudioFileName)
        }
        tempFile?.delete()
        tempFile = null
        listener?.onSoundSaved(label, fileName)
    }

    private fun stopRecordingIfActive() {
        if (isRecording) {
            try { mediaRecorder?.stop() } catch (_: Exception) {
                // stop() can throw if recording failed to start; safe to ignore during cleanup
            }
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun cleanupTemp() {
        mediaPlayer?.release()
        mediaPlayer = null
        stopRecordingIfActive()
        tempFile?.delete()
        tempFile = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cleanupTemp()
    }
}
