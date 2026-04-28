package com.hdclark.diysoundboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val recordAudioPermissionRequest = 1001
    private val initialRows = 6
    private val columns = 2

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SoundboardAdapter
    private val buttons = mutableListOf<SoundButton>()
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        buttons.addAll(SoundStorage.loadButtons(this))
        ensureEmptySlots()

        recyclerView = findViewById(R.id.recycler_view)
        adapter = SoundboardAdapter(
            buttons,
            onEmptyClick = { pos -> handleEmptyClick(pos) },
            onSoundClick = { pos -> handleSoundClick(pos) },
            onSoundLongPress = { pos -> handleSoundLongPress(pos) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, columns)
        recyclerView.adapter = adapter

        requestMicPermission()
    }

    /**
     * Ensure the list always has at least [initialRows]*[columns] entries and
     * always at least [columns]*2 empty trailing slots so there is always room
     * to add more sounds by scrolling down.
     */
    private fun ensureEmptySlots() {
        val minSize = initialRows * columns
        val lastFilled = buttons.indexOfLast { !it.isEmpty }
        val target = maxOf(minSize, lastFilled + 1 + columns * 2)
        while (buttons.size < target) buttons.add(SoundButton())
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                recordAudioPermissionRequest
            )
        }
    }

    private fun handleEmptyClick(position: Int) {
        val dialog = RecordSoundDialog.newInstance()
        dialog.setListener(object : RecordSoundDialog.Listener {
            override fun onSoundSaved(label: String, audioFileName: String) {
                if (position < buttons.size) {
                    buttons[position] = SoundButton(label = label, audioFileName = audioFileName)
                } else {
                    buttons.add(SoundButton(label = label, audioFileName = audioFileName))
                }
                ensureEmptySlots()
                adapter.notifyDataSetChanged()
                persistButtons()
            }
        })
        dialog.show(supportFragmentManager, "record_sound")
    }

    private fun handleSoundClick(position: Int) {
        val button = buttons.getOrNull(position) ?: return
        if (button.isEmpty) return
        val file = SoundStorage.getAudioFile(this, button.audioFileName)
        if (!file.exists()) {
            Toast.makeText(this, R.string.error_file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { completedPlayer ->
                    completedPlayer.setOnCompletionListener(null)
                    completedPlayer.release()
                    if (mediaPlayer === completedPlayer) {
                        mediaPlayer = null
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_playback_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSoundLongPress(position: Int) {
        val button = buttons.getOrNull(position) ?: return
        val dialog = RecordSoundDialog.newInstance(button.label, button.audioFileName)
        dialog.setListener(object : RecordSoundDialog.Listener {
            override fun onSoundSaved(label: String, audioFileName: String) {
                buttons[position] = button.copy(label = label, audioFileName = audioFileName)
                adapter.notifyItemChanged(position)
                persistButtons()
            }
        })
        dialog.show(supportFragmentManager, "edit_sound")
    }

    private fun persistButtons() {
        SoundStorage.saveButtons(this, buttons.filter { !it.isEmpty })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                exportSoundboard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportSoundboard() {
        try {
            val zipFile = SoundStorage.exportToZip(this)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", zipFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_export_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
