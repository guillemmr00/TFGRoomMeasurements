package edu.upf.aism.tfgroommeasurements


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class Adapter(var measures : ArrayList<RoomMeasure>, var listener: OnItemClickListener) : RecyclerView.Adapter<Adapter.ViewHolder>(){

    private var editMode = false

    fun isEditMode(): Boolean{return editMode }
    fun setEditMode(mode : Boolean){
        if(editMode!=mode){
            editMode=mode
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener{
        var tvFilename :TextView = itemView.findViewById(R.id.tvfilename)
        var tvDate :TextView = itemView.findViewById(R.id.tvDate)
        var tvSweepFreq :TextView = itemView.findViewById(R.id.tvSweepFreq)
        var tvSweepDur :TextView = itemView.findViewById(R.id.tvSweepDur)
        var tvSweepLevel :TextView = itemView.findViewById(R.id.tvSweepLevel)
        var checkbox : CheckBox = itemView.findViewById(R.id.checkbox)

        init{
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if(position != RecyclerView.NO_POSITION)
                listener.onItemClickListener(position)
        }

        override fun onLongClick(v: View?): Boolean {
            val position = adapterPosition
            if(position != RecyclerView.NO_POSITION)
                listener.onItemLongClickListener(position)
            return true
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.itemview_layout, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return measures.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(position != RecyclerView.NO_POSITION){
            var measure = measures[position]
            var strDate = measure.date


            holder.tvFilename.text = measure.filename
            holder.tvDate.text = "Date: ${strDate.replace("_", " ").replace("-", "/")}"
            holder.tvSweepFreq.text = "Mode: ${measure.mode}  f0: ${measure.f0.toInt()} Hz  f1:${measure.f1.toInt()} Hz"
            holder.tvSweepDur.text = "Duration: ${measure.duration} secs.  Samplerate:${measure.sampleRate}"

            val dbRms = 20 * Math.log10(measure.outGain / Short.MAX_VALUE)
            holder.tvSweepLevel.text = "Level: ${ BigDecimal(dbRms).setScale(2, RoundingMode.HALF_EVEN) } dBFS"

            if(editMode){
                holder.checkbox.visibility = View.VISIBLE
                holder.checkbox.isChecked = measure.isChecked
            }else{
                holder.checkbox.visibility = View.GONE
                holder.checkbox.isChecked = false
            }
        }
    }
}