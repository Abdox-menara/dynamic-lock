package com.example.dynamiclock.vault

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import com.example.dynamiclock.databinding.ActivityNoteBinding

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteBinding
    private lateinit var store: VaultStore
    private var noteId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = VaultStore(this)

        noteId = intent.getLongExtra(EXTRA_ID, 0L)
        if (noteId != 0L) {
            store.getNote(noteId)?.let {
                binding.etTitle.setText(it.title)
                binding.etBody.setText(it.body)
            }
        }
        binding.btnDelete.visibility = if (noteId == 0L) android.view.View.GONE else android.view.View.VISIBLE

        binding.btnSave.setOnClickListener {
            val id = if (noteId == 0L) System.currentTimeMillis() else noteId
            store.upsert(
                VaultStore.Note(
                    id = id,
                    title = binding.etTitle.text.toString().trim(),
                    body = binding.etBody.text.toString(),
                    updated = System.currentTimeMillis()
                )
            )
            finish()
        }
        binding.btnDelete.setOnClickListener {
            if (noteId == 0L) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Delete note?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    store.deleteNote(noteId)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    companion object {
        const val EXTRA_ID = "note_id"
    }
}
