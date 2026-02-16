package com.minimalist.launcher.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.R
import com.minimalist.launcher.model.AppInfo

class FavoritesAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, FavoritesAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app)
    }

    inner class ViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(app: AppInfo) {
            textView.text = app.displayName
            textView.setOnClickListener { onClick(app) }
            textView.setOnLongClickListener {
                onLongClick(app)
                true
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
            oldItem == newItem
    }
}
