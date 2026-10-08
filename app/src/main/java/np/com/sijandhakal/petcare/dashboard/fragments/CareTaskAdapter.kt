package np.com.sijandhakal.petcare.dashboard.fragments

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import np.com.sijandhakal.petcare.R
import np.com.sijandhakal.petcare.dashboard.data.CareCategory
import np.com.sijandhakal.petcare.dashboard.data.CareTask
import np.com.sijandhakal.petcare.databinding.ItemCareTaskBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Care task cards. With [upcoming] set, cards show the next due date instead of a tick box,
 * for routines that aren't due today.
 */
class CareTaskAdapter(
    private val petName: (petId: String) -> String,
    private val onToggleDone: (CareTask, Boolean) -> Unit,
    private val onEdit: (CareTask) -> Unit,
    private val onDelete: (CareTask) -> Unit,
    private val upcoming: Boolean = false
) : ListAdapter<CareTask, CareTaskAdapter.ViewHolder>(Diff) {

    private val dueDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

    inner class ViewHolder(val binding: ItemCareTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCareTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = getItem(position)
        val b = holder.binding
        val context = b.root.context

        val done = task.done && !upcoming
        b.tvTitle.text = context.getString(R.string.task_title_format, petName(task.petId), task.title)
        b.tvTitle.paintFlags = if (done) {
            b.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            b.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        b.tvMeta.text = when {
            upcoming -> {
                val today = LocalDate.now()
                val due = task.nextDueOnOrAfter(today)
                if (due == today.plusDays(1)) {
                    context.getString(R.string.due_tomorrow_format, task.timeLabel())
                } else {
                    context.getString(R.string.due_on_format, due.format(dueDateFormat), task.timeLabel())
                }
            }
            task.supplies.isBlank() -> task.timeLabel()
            else -> context.getString(R.string.task_meta_format, task.timeLabel(), task.supplies)
        }

        // Medication gets the terracotta "reminder" badge from the design; everything else stays sage.
        val isMedication = task.category == CareCategory.MEDICATION
        val category = context.getString(task.category.label)
        // Non-daily routines also say how often they repeat, e.g. "Vet Checkup · Quarterly".
        b.tvCategory.text = if (task.repeat.isDaily) {
            category
        } else {
            context.getString(R.string.task_meta_format, category, task.frequency.substringBefore(" ("))
        }
        b.tvCategory.backgroundTintList = ContextCompat.getColorStateList(
            context, if (isMedication) R.color.pc_badge_overdue_bg else R.color.pc_badge_done_bg
        )
        b.tvCategory.setTextColor(
            ContextCompat.getColor(context, if (isMedication) R.color.pc_tertiary else R.color.pc_primary_container)
        )

        b.tvNote.text = task.notes
        b.tvNote.isVisible = task.notes.isNotBlank() && !done
        b.root.alpha = if (done) 0.7f else 1f

        // Upcoming routines can't be ticked off until their due date.
        b.cbDone.visibility = if (upcoming) View.GONE else View.VISIBLE
        b.cbDone.setOnCheckedChangeListener(null)
        b.cbDone.isChecked = done
        b.cbDone.setOnCheckedChangeListener { _, checked -> onToggleDone(task, checked) }
        b.btnEdit.setOnClickListener { onEdit(task) }
        b.btnDelete.setOnClickListener { onDelete(task) }
    }

    private object Diff : DiffUtil.ItemCallback<CareTask>() {
        override fun areItemsTheSame(oldItem: CareTask, newItem: CareTask) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CareTask, newItem: CareTask) = oldItem == newItem
    }
}
