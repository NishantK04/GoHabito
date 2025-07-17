package com.nishant.Habitide

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.EventDay
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HabitCalendarBottomSheet : BottomSheetDialogFragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var summaryText: TextView
    private lateinit var startEndDate: TextView
    private lateinit var calendarProgress: ProgressBar
    private lateinit var habit: Habit

    companion object {
        private const val ARG_HABIT = "habit"
        fun newInstance(habit: Habit): HabitCalendarBottomSheet {
            return HabitCalendarBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_HABIT, habit)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        habit = arguments?.getSerializable(ARG_HABIT) as Habit
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.bottom_sheet_calendar, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        summaryText = view.findViewById(R.id.summaryText)
        startEndDate = view.findViewById(R.id.startEndDate)
        calendarProgress = view.findViewById(R.id.calendarProgress)

        calendarView.visibility = View.INVISIBLE
        summaryText.visibility = View.INVISIBLE
        startEndDate.visibility = View.INVISIBLE
        calendarProgress.visibility = View.VISIBLE

        view.post {
            lifecycleScope.launch {
                setupCalendarAsync()
            }
        }

        return view
    }

    private suspend fun setupCalendarAsync() = withContext(Dispatchers.Default) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val start = dateFormat.parse(habit.startDate ?: "") ?: return@withContext

        val calendar = Calendar.getInstance().apply {
            time = start
            add(Calendar.DAY_OF_MONTH, habit.goalDays - 1)
        }
        val end = calendar.time

        val completedDates = habit.completionDates.mapNotNull {
            runCatching { dateFormat.parse(it) }.getOrNull()
        }

        val events = mutableListOf<EventDay>()
        val missedDays = mutableListOf<Date>()
        val cal = Calendar.getInstance().apply { time = start }

        while (!cal.time.after(end)) {
            val dateCopy = cal.time
            val isCompleted = completedDates.any { sameDay(it, dateCopy) }

            val color = if (isCompleted) {
                Color.parseColor("#4CAF50") // Green
            } else {
                missedDays.add(dateCopy)
                Color.parseColor("#FF7070") // Red
            }

            events.add(EventDay(toCalendar(dateCopy), makeCircleDrawable(color)))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        withContext(Dispatchers.Main) {
            calendarView.setEvents(events)
            summaryText.text = "Missed Days: ${missedDays.size}, Completed Days: ${completedDates.size}"
            startEndDate.text = "Start: ${dateFormat.format(start)}, End: ${dateFormat.format(end)}"

            calendarProgress.visibility = View.GONE
            calendarView.visibility = View.VISIBLE
            summaryText.visibility = View.VISIBLE
            startEndDate.visibility = View.VISIBLE

            fixCalendarHeaderTextColors()
        }
    }

    private fun fixCalendarHeaderTextColors() {
        try {
            val color = Color.parseColor("#0A5DC5") // Blue or any color you want

            // Recursively find all children in the CalendarView
            fun findAllTextViews(view: View): List<TextView> {
                val result = mutableListOf<TextView>()
                if (view is TextView) {
                    result.add(view)
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        result.addAll(findAllTextViews(view.getChildAt(i)))
                    }
                }
                return result
            }

            val textViews = findAllTextViews(calendarView)
            for (tv in textViews) {
                if (tv.text.toString().matches(Regex(".*\\d{4}.*")) || // Likely "July 2025"
                    tv.text.toString().matches(Regex("(?i)january|february|march|april|may|june|july|august|september|october|november|december"))
                ) {
                    tv.setTextColor(color)
                }
            }

            // Set color for navigation arrows
            val prevButton = calendarView.findViewById<ImageView>(
                resources.getIdentifier("previousButton", "id", requireContext().packageName)
            )
            val nextButton = calendarView.findViewById<ImageView>(
                resources.getIdentifier("forwardButton", "id", requireContext().packageName)
            )
            prevButton?.setColorFilter(color)
            nextButton?.setColorFilter(color)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun toCalendar(date: Date): Calendar = Calendar.getInstance().apply { time = date }

    private fun sameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    private fun makeCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(64, 64)
        }
    }
}
