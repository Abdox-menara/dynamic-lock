package com.example.dynamiclock.vault

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import com.example.dynamiclock.util.Options
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.WindowManager
import com.example.dynamiclock.R
import com.example.dynamiclock.databinding.ActivityVaultBinding
import com.example.dynamiclock.databinding.ItemNoteBinding
import com.example.dynamiclock.security.IntruderCaptureActivity
import java.io.File

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private lateinit var store: VaultStore
    private val adapter = NotesAdapter()
    private var searchQuery: String = ""

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null && store.addPhoto(uri)) refreshPhotos()
        }

    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = VaultStore(this)

        binding.rvNotes.layoutManager = LinearLayoutManager(this)
        binding.rvNotes.adapter = adapter

        // v5: Search functionality
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                refreshNotes()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // v5: Intruder photos button
        refreshIntruderSection()

        binding.btnAddNote.setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java))
        }
        binding.btnAddPhoto.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotes()
        refreshPhotos()
        refreshIntruderSection()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val timeoutMs = Options(this).vaultAutoLockSeconds * 1000L
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > timeoutMs) finish()
    }

    private fun refreshNotes() {
        val allNotes = store.loadNotes()
        val filtered = if (searchQuery.isEmpty()) allNotes
        else allNotes.filter {
            it.title.lowercase().contains(searchQuery) || it.body.lowercase().contains(searchQuery)
        }
        adapter.submit(filtered)
        binding.tvNotesEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (searchQuery.isNotEmpty() && filtered.isEmpty()) {
            binding.tvNotesEmpty.text = getString(R.string.no_notes_found)
        } else {
            binding.tvNotesEmpty.text = getString(R.string.no_notes_found)
        }
    }

    private fun refreshPhotos() {
        binding.llPhotos.removeAllViews()
        val sizePx = (resources.displayMetrics.density * 92).toInt()
        val marginPx = (resources.displayMetrics.density * 6).toInt()
        for (file in store.listPhotos()) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                    it.setMargins(0, 0, marginPx, 0)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(store.decodeSampled(file, 220))
                setOnClickListener { showPhoto(file) }
            }
            binding.llPhotos.addView(iv)
        }
    }

    private fun refreshIntruderSection() {
        val photos = IntruderCaptureActivity.listPhotos(this)
        if (photos.isNotEmpty()) {
            binding.tvIntruderSection.visibility = View.VISIBLE
            binding.llIntruderPhotos.visibility = View.VISIBLE; val intruderContainer = binding.llIntruderPhotosInner
            intruderContainer.removeAllViews()
            val sizePx = (resources.displayMetrics.density * 80).toInt()
            val marginPx = (resources.displayMetrics.density * 6).toInt()
            for (file in photos) {
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                        it.setMargins(0, 0, marginPx, 0)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(IntruderCaptureActivity.loadPhoto(this@VaultActivity, file))
                    setOnClickListener {
                        AlertDialog.Builder(this@VaultActivity)
                            .setTitle("Intruder Photo")
                            .setMessage("Captured: ${file.name.replace("intruder_", "").replace(".enc", "")}")
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Delete") { _, _ ->
                                IntruderCaptureActivity.deletePhoto(this@VaultActivity, file)
                                refreshIntruderSection()
                            }
                            .show()
                    }
                }
                intruderContainer.addView(iv)
            }
        } else {
            binding.tvIntruderSection.visibility = View.GONE
            binding.llIntruderPhotos.visibility = View.GONE
        }
    }

    private fun showPhoto(file: File) {
        val iv = ImageView(this).apply {
            setImageBitmap(store.decodeSampled(file, 1400))
            adjustViewBounds = true
        }
        AlertDialog.Builder(this)
            .setView(iv)
            .setNegativeButton("Close", null)
            .setPositiveButton("Delete") { _, _ ->
                store.deletePhoto(file); refreshPhotos()
            }
            .show()
    }

    private inner class NotesAdapter : RecyclerView.Adapter<NotesAdapter.VH>() {
        private val items = mutableListOf<VaultStore.Note>()

        fun submit(list: List<VaultStore.Note>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }

        inner class VH(val b: ItemNoteBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val note = items[position]
            holder.b.tvTitle.text = if (note.title.isBlank()) "(untitled)" else note.title
            holder.b.tvSnippet.text = note.body
            holder.b.root.setOnClickListener {
                startActivity(
                    Intent(this@VaultActivity, NoteEditorActivity::class.java)
                        .putExtra(NoteEditorActivity.EXTRA_ID, note.id)
                )
            }
        }
    }
}
