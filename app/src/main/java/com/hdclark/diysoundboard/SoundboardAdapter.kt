package com.hdclark.diysoundboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SoundboardAdapter(
    private val buttons: MutableList<SoundButton>,
    private val onEmptyClick: (position: Int) -> Unit,
    private val onSoundClick: (position: Int) -> Unit,
    private val onSoundLongPress: (position: Int) -> Unit
) : RecyclerView.Adapter<SoundboardAdapter.SoundViewHolder>() {

    inner class SoundViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val labelText: TextView = view.findViewById(R.id.tv_label)
        val micIcon: View = view.findViewById(R.id.iv_mic)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoundViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sound_button, parent, false)
        return SoundViewHolder(view)
    }

    override fun onBindViewHolder(holder: SoundViewHolder, position: Int) {
        val button = buttons[position]
        if (button.isEmpty) {
            holder.labelText.text = ""
            holder.micIcon.visibility = View.VISIBLE
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onEmptyClick(pos)
            }
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.alpha = 0.5f
        } else {
            holder.labelText.text = button.label
            holder.micIcon.visibility = View.GONE
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onSoundClick(pos)
            }
            holder.itemView.setOnLongClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onSoundLongPress(pos)
                true
            }
            holder.itemView.alpha = 1.0f
        }
    }

    override fun getItemCount() = buttons.size
}
