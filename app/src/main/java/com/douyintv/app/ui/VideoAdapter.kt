package com.douyintv.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.douyintv.app.databinding.ItemVideoCardBinding
import com.douyintv.app.model.DouyinVideo

class VideoAdapter(
    private val onItemClick: (position: Int) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val items = mutableListOf<DouyinVideo>()

    fun submitList(newItems: List<DouyinVideo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<DouyinVideo> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class VideoViewHolder(
        private val binding: ItemVideoCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: DouyinVideo, position: Int) {
            binding.tvTitle.text = video.desc
            binding.tvAuthor.text = "@${video.authorName}"
            binding.tvLikes.text = formatCount(video.diggCount)
            binding.tvDuration.text = formatDuration(video.durationMs)
            binding.ivCover.load(video.coverUrl) {
                crossfade(true)
            }

            binding.root.setOnClickListener {
                onItemClick(position)
            }

            binding.root.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.06f else 1.0f
                v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(140)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }

        private fun formatCount(count: Long): String {
            return when {
                count >= 10000L -> String.format("%.1f万赞", count / 10000.0)
                count > 0L -> "${count}赞"
                else -> ""
            }
        }
    }
}
