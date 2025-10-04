package com.jbros.tagkosha.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.chip.Chip
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
                // The theme from the FlexboxLayout now automatically styles any Chip created within it.
                // This is the simplest and most correct creation.
                val chip = Chip(binding.flexboxTags.context)
                chip.text = tag
                chip.setOnClickListener {
                    onTagChipClicked(tag)
                }

                // We still need to set margins for Flexbox spacing
                val params = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 16, 8)
                chip.layoutParams = params

                binding.flexboxTags.addView(chip)
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
