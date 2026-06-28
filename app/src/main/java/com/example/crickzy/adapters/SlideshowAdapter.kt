package com.example.crickzy.adapters

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R

data class SlideItem(
    val title: String,
    val description: String,
    val iconResId: Int,
    val startColorRes: Int,
    val endColorRes: Int
)

class SlideshowAdapter(private val slides: List<SlideItem>) :
    RecyclerView.Adapter<SlideshowAdapter.SlideViewHolder>() {

    class SlideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val ivSlideIllustration: ImageView = view.findViewById(R.id.ivSlideIllustration)
        val illustrationContainer: FrameLayout = view.findViewById(R.id.illustrationContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slide, parent, false)
        return SlideViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        val slide = slides[position]
        holder.tvTitle.text = slide.title
        holder.tvDescription.text = slide.description
        holder.ivSlideIllustration.setImageResource(slide.iconResId)

        // Create a premium dynamic gradient background programmatically
        val startColor = ContextCompat.getColor(holder.itemView.context, slide.startColorRes)
        val endColor = ContextCompat.getColor(holder.itemView.context, slide.endColorRes)
        val density = holder.itemView.context.resources.displayMetrics.density
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(startColor, endColor)
        ).apply {
            cornerRadius = density * 24f
        }
        
        holder.illustrationContainer.background = gradientDrawable
    }

    override fun getItemCount(): Int = slides.size
}
