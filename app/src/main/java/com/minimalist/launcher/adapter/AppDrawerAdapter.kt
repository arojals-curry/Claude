package com.minimalist.launcher.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.R
import com.minimalist.launcher.model.AppInfo

class AppDrawerAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<DrawerItem>()

    sealed class DrawerItem {
        data class LetterHeader(val letter: String) : DrawerItem()
        data class App(val appInfo: AppInfo) : DrawerItem()
    }

    fun submitList(apps: List<AppInfo>) {
        items.clear()
        var currentLetter = ""
        for (app in apps) {
            val firstChar = app.displayName.first().uppercase()
            val letter = if (firstChar.first().isLetter()) firstChar else "#"
            if (letter != currentLetter) {
                currentLetter = letter
                items.add(DrawerItem.LetterHeader(letter))
            }
            items.add(DrawerItem.App(app))
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DrawerItem.LetterHeader -> TYPE_HEADER
            is DrawerItem.App -> TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_letter_header, parent, false) as TextView
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app, parent, false) as TextView
                AppViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DrawerItem.LetterHeader -> (holder as HeaderViewHolder).bind(item.letter)
            is DrawerItem.App -> (holder as AppViewHolder).bind(item.appInfo)
        }
    }

    override fun getItemCount() = items.size

    inner class HeaderViewHolder(private val textView: TextView) :
        RecyclerView.ViewHolder(textView) {
        fun bind(letter: String) {
            textView.text = letter
        }
    }

    inner class AppViewHolder(private val textView: TextView) :
        RecyclerView.ViewHolder(textView) {
        fun bind(app: AppInfo) {
            textView.text = app.displayName
            textView.setOnClickListener { onClick(app) }
            textView.setOnLongClickListener {
                onLongClick(app)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }
}
