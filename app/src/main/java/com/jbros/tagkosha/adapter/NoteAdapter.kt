package com.jbros.tagkosha.adapter

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.jbros.tagkosha.R
import com.jbros.tagkosha.databinding.ItemNoteBinding
import com.jbros.tagkosha.model.Note

class NoteAdapter(
    private var notes: List<Note>,
    private val onNoteClicked: (Note) -> Unit,
    private val onActionClicked: (Note, Action) -> Unit,
    private val onTagChipClicked: (String) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    enum class Action {
        DELETE, CLONE, SHARE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount(): Int = notes.size

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note) {
            binding.tvNoteTitle.text = note.title
            binding.tvNoteContentPreview.text = note.content

            binding.flexboxTags.removeAllViews()
            note.tags.forEach { tag ->
                // Use the new, simple style name
                val contextWrapper = ContextThemeWrapper(itemView.context, R.style.TagTextViewStyle)
                val tagView = TextView(contextWrapper)

                tagView.text = tag
                tagView.setOnClickListener {
                    onTagChipClicked(tag)
                }

                binding.flexboxTags.addView(tagView)
            }

            itemView.setOnClickListener {
                onNoteClicked(note)
            }

            itemView.setOnLongClickListener {
                val popup = PopupMenu(itemView.context, it)
                popup.inflate(R.menu.note_context_menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.context_delete -> { onActionClicked(note, Action.DELETE); true }
                        R.id.context_clone -> { onActionClicked(note, Action.CLONE); true }
                        R.id.context_share -> { onActionClicked(note, Action.SHARE); true }
                        else -> false
                    }
                }
                popup.show()
                true
            }
        }
    }
}