package com.nishant.gohabito

import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nishant.gohabito.databinding.ItemHabitCheckboxBinding

class MissionAdapter(
    private val habits: MutableList<MissionHabit>,
    private val onCheckChanged: (MissionHabit, Boolean) -> Unit
) : RecyclerView.Adapter<MissionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHabitCheckboxBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val checkBox: CheckBox = binding.habitCheckbox
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHabitCheckboxBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val habit = habits[position]

        // Remove any previous listener to avoid trigger conflicts
        holder.checkBox.setOnCheckedChangeListener(null)

        // Set text and checkbox state
        holder.checkBox.text = "${habit.name} (${habit.habitTitle})"
        holder.checkBox.isChecked = habit.checked
        holder.checkBox.isEnabled = !habit.checked
        setStrikeThrough(holder.checkBox, habit.checked)

        // Set new listener
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (!habit.checked && isChecked) {
                habit.checked = true
                habit.progressUpdated = true
                holder.checkBox.isEnabled = false
                animateStrikeThrough(holder.checkBox, true)

                val docId = "${habit.userId}_${habit.name}_${habit.habitTitle}".replace(" ", "_")
                val updateMap = mapOf(
                    "checked" to true,
                    "progressUpdated" to true
                )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(habit.userId)
                    .collection("missions")
                    .document(docId)
                    .set(updateMap, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("MissionAdapter", "✅ Firestore updated: $docId")
                        onCheckChanged(habit, true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("MissionAdapter", "❌ Firestore update failed", e)
                        habit.checked = false
                        holder.checkBox.isChecked = false
                        holder.checkBox.isEnabled = true
                        setStrikeThrough(holder.checkBox, false)
                    }
            } else {
                // Prevent toggling back manually
                holder.checkBox.isChecked = true
                holder.checkBox.isEnabled = false
            }
        }
    }

    override fun getItemCount(): Int = habits.size

    private fun setStrikeThrough(view: CheckBox, strike: Boolean) {
        view.paintFlags = if (strike) {
            view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private fun animateStrikeThrough(view: CheckBox, strike: Boolean) {
        view.animate().alpha(0f).setDuration(150).withEndAction {
            setStrikeThrough(view, strike)
            view.animate().alpha(1f).setDuration(150).start()
        }.start()
    }
}
