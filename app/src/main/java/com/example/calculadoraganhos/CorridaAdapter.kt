package com.example.calculadoraganhos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CorridaAdapter : RecyclerView.Adapter<CorridaAdapter.VH>() {

    private val items = ArrayList<RideEntry>()

    fun update(list: List<RideEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_corrida, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(r.ts))
        holder.app.text = "${r.app} - $time"
        holder.fare.text = OverlayManager.formatMoney(r.fare)
        holder.details.text = buildString {
            if (r.km > 0) append(OverlayManager.formatKm(r.km))
            if (r.km > 0 && r.minutes > 0) append(" - ")
            if (r.minutes > 0) append(OverlayManager.formatTime(r.minutes))
            if (r.perHour > 0) append(" - ").append(OverlayManager.formatMoney(r.perHour)).append("/h")
            if (r.perKm > 0) append(" - ").append(OverlayManager.formatMoney(r.perKm)).append("/km")
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val app: TextView = v.findViewById(R.id.tvApp)
        val fare: TextView = v.findViewById(R.id.tvFare)
        val details: TextView = v.findViewById(R.id.tvDetails)
    }
}
