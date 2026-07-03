package com.example.dialermobilepoc

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dialermobilepoc.model.CallLog
import java.text.SimpleDateFormat
import java.util.Locale

class CallLogAdapter(
    private val onPlayClick: (CallLog) -> Unit,
    private val onCheckRecordingClick: (CallLog) -> Unit,
    private val onDownloadClick: (CallLog, (Boolean) -> Unit) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

    private var items: List<CallLog> = emptyList()
    private var playingUrl: String? = null

    fun submitList(newItems: List<CallLog>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setPlayingUrl(url: String?) {
        playingUrl = url
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val customerNumberText: TextView = view.findViewById(R.id.customerNumberText)
        val agentIdText: TextView = view.findViewById(R.id.agentIdText)
        val statusBadgeText: TextView = view.findViewById(R.id.statusBadgeText)
        val startTimeText: TextView = view.findViewById(R.id.startTimeText)
        val recordingRow: View = view.findViewById(R.id.recordingRow)
        val playRecordingButton: Button = view.findViewById(R.id.playRecordingButton)
        val downloadRecordingButton: Button = view.findViewById(R.id.downloadRecordingButton)
        val checkRecordingButton: Button = view.findViewById(R.id.checkRecordingButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = items[position]

        holder.customerNumberText.text = log.customerNumber
        holder.agentIdText.text = "Agent: ${log.agentId}"
        holder.startTimeText.text = formatStartTime(log.startTime)

        holder.statusBadgeText.text = log.status
        holder.statusBadgeText.backgroundTintList = ColorStateList.valueOf(statusColor(log.status))

        val recordingUrl = log.recordingUrl
        if (!recordingUrl.isNullOrBlank()) {
            holder.recordingRow.visibility = View.VISIBLE
            holder.playRecordingButton.text = if (recordingUrl == playingUrl) "Stop" else "Play"
            holder.playRecordingButton.setOnClickListener { onPlayClick(log) }
            holder.downloadRecordingButton.text = "Download"
            holder.downloadRecordingButton.isEnabled = true
            holder.downloadRecordingButton.setOnClickListener {
                holder.downloadRecordingButton.isEnabled = false
                onDownloadClick(log) { success ->
                    holder.downloadRecordingButton.text = if (success) "Downloaded" else "Download"
                    holder.downloadRecordingButton.isEnabled = true
                }
            }
            holder.checkRecordingButton.visibility = View.GONE
            holder.checkRecordingButton.setOnClickListener(null)
        } else if (log.status.equals("COMPLETED", ignoreCase = true)) {
            holder.recordingRow.visibility = View.GONE
            holder.playRecordingButton.setOnClickListener(null)
            holder.downloadRecordingButton.setOnClickListener(null)
            holder.checkRecordingButton.visibility = View.VISIBLE
            holder.checkRecordingButton.setOnClickListener { onCheckRecordingClick(log) }
        } else {
            holder.recordingRow.visibility = View.GONE
            holder.playRecordingButton.setOnClickListener(null)
            holder.downloadRecordingButton.setOnClickListener(null)
            holder.checkRecordingButton.visibility = View.GONE
            holder.checkRecordingButton.setOnClickListener(null)
        }
    }

    private fun statusColor(status: String): Int {
        return when (status.uppercase(Locale.US)) {
            "COMPLETED" -> Color.parseColor("#4CAF50")
            "IN_PROGRESS" -> Color.parseColor("#2196F3")
            "RINGING", "DIALING" -> Color.parseColor("#FF9800")
            "NO_ANSWER", "BUSY" -> Color.parseColor("#9E9E9E")
            "FAILED" -> Color.parseColor("#F44336")
            else -> Color.parseColor("#9E9E9E")
        }
    }

    private fun formatStartTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "-"
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(raw) ?: continue
                return SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(parsed)
            } catch (e: Exception) {
                // try the next pattern
            }
        }
        return raw
    }
}