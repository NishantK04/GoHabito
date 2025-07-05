package com.nishant.Habitide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nishant.Habitide.databinding.ItemHabitBinding

class HabitAdapter(
    private val habits: MutableList<Habit>,
    private val deleteListener: HabitDeleteListener,
    private val clickListener: HabitClickListener   // ✅ Added for click handling
) : RecyclerView.Adapter<HabitAdapter.HabitViewHolder>() {

    inner class HabitViewHolder(
        private val binding: ItemHabitBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(habit: Habit) {
            binding.habitTitle.text = habit.title
            binding.habitData.text = "${habit.daysCompleted} / ${habit.goalDays} days"

            val progressPercent = if (habit.goalDays > 0)
                (habit.daysCompleted * 100 / habit.goalDays)
            else 0

            val progressColor = when {
                progressPercent < 34 -> 0xFFFF5252.toInt() // Red
                progressPercent < 67 -> 0xFFFF9800.toInt() // Orange
                else -> 0xFF4CAF50.toInt() // Green
            }

            binding.habitProgress.setIndicatorColor(progressColor)
            binding.habitProgress.setProgress(progressPercent, true)

            val daysLeft = (habit.goalDays - habit.daysCompleted).coerceAtLeast(0)
            binding.habitDaysLeft.text = daysLeft.toString()

            // ✅ Show start date
            binding.habitStartDate.text = "Started on ${habit.startDate ?: "N/A"}"

            // ✅ Handle click to open calendar
            binding.root.setOnClickListener {
                clickListener.onHabitClicked(habit)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val binding = ItemHabitBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HabitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(habits[position])
    }

    override fun getItemCount(): Int = habits.size

    fun addHabit(habit: Habit) {
        habits.add(habit)
        notifyItemInserted(habits.size - 1)
    }

    fun swapItems(fromPosition: Int, toPosition: Int) {
        val temp = habits[fromPosition]
        habits[fromPosition] = habits[toPosition]
        habits[toPosition] = temp
        notifyItemMoved(fromPosition, toPosition)
    }

    fun updateHabitDays(title: String, newDays: Int) {
        val habit = habits.find { it.title == title }
        habit?.let {
            it.daysCompleted = newDays
            notifyItemChanged(habits.indexOf(it))
        }
    }

    fun removeHabit(position: Int) {
        val removedHabit = habits.removeAt(position)
        notifyItemRemoved(position)
        deleteListener.onHabitDeleted(removedHabit.title)
    }
}
