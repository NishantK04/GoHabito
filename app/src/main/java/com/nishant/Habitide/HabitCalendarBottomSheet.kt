package com.nishant.Habitide

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.EventDay
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*

class HabitCalendarBottomSheet : BottomSheetDialogFragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var summaryText: TextView
    private lateinit var startEndDate: TextView
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

        setupCalendar()
        return view
    }

    private fun setupCalendar() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // ✅ Get start date from habit
        val start = dateFormat.parse(habit.startDate ?: "") ?: return

        // ✅ Calculate end date based on goalDays
        val calendar = Calendar.getInstance().apply {
            time = start
            add(Calendar.DAY_OF_MONTH, habit.goalDays - 1)
        }
        val end = calendar.time

        // ✅ Parse completed dates safely
        val completedDates = habit.completionDates.mapNotNull { dateString ->
            runCatching { dateFormat.parse(dateString) }.getOrNull()
        }

        // ✅ Prepare event list
        val events = mutableListOf<EventDay>()
        val missedDays = mutableListOf<Date>()
        val cal = Calendar.getInstance().apply { time = start }

        while (!cal.time.after(end)) {
            val dateCopy = cal.time
            val color = if (completedDates.any { sameDay(it, dateCopy) }) {
                Color.parseColor("#8AB4F8") // Completed days: Blue
            } else {
                missedDays.add(dateCopy)
                Color.parseColor("#FF7070") // Missed days: Red
            }
            events.add(EventDay(toCalendar(dateCopy), makeCircleDrawable(color)))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        // ✅ Set events into CalendarView
        calendarView.setEvents(events)

        // ✅ Set summary text and date range
        summaryText.text = "Missed Days: ${missedDays.size}, Completed Days: ${completedDates.size}"
        startEndDate.text = "Start: ${dateFormat.format(start)}, End: ${dateFormat.format(end)}"
    }

    // ✅ Helper: Convert Date -> Calendar
    private fun toCalendar(date: Date): Calendar = Calendar.getInstance().apply { time = date }

    // ✅ Helper: Check if two dates fall on the same day
    private fun sameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    // ✅ Helper: Create circular colored event drawable
    private fun makeCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(64, 64) // Circle size
        }
    }
}
