package com.kamiwa.quickgpstoraster.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.kamiwa.quickgpstoraster.R

class PointAdapter(private val items: MutableList<Triple<Double, Double, Double>>) :
    RecyclerView.Adapter<PointAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val item: ConstraintLayout = itemView.findViewById(R.id.pointAdapter_item)
        val latValue: TextView = itemView.findViewById(R.id.pointAdapter_latValue)
        val lonValue: TextView = itemView.findViewById(R.id.pointAdapter_lonValue)
        val altValue: TextView = itemView.findViewById(R.id.pointAdapter_altValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.point_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (lat, lon, alt) = items[position]
        holder.latValue.text = lat.toString()
        holder.lonValue.text = lon.toString()
        holder.altValue.text = alt.toString()

        holder.item.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
            }

        }
    }

    override fun getItemCount() = items.size

    fun updatePoints(newItems: MutableList<Triple<Double, Double, Double>>){
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}