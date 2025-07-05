package com.nishant.Habitide

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), HabitDeleteListener, HabitClickListener {

    private lateinit var addHabitBtn: ImageButton
    private lateinit var resetMissionBtn: ImageButton
    private lateinit var logoutBtn: ImageButton
    private lateinit var habitRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var userName: TextView
    private lateinit var userImage: de.hdodenhof.circleimageview.CircleImageView
    private lateinit var missionRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var habitProgress: CircularProgressIndicator
    private lateinit var habitDaysLeft: TextView
    private lateinit var masterCheckBox: CheckBox

    private lateinit var habitAdapter: HabitAdapter
    private lateinit var missionAdapter: MissionAdapter

    private val habits = mutableListOf<Habit>()
    private val missionHabits = mutableListOf<MissionHabit>()
    private val updatedMissions = mutableSetOf<String>()
    private val db = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var userId: String

    private var isUpdatingMasterCheckBox = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.BLACK
        setContentView(R.layout.activity_main)

        FirebaseAuth.getInstance().currentUser?.let { user -> userId = user.uid } ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ConstraintLayout>(R.id.main).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top,
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
            )
            insets
        }

        addHabitBtn = findViewById(R.id.addHabitBtn)
        resetMissionBtn = findViewById(R.id.resetMissionBtn)
        logoutBtn = findViewById(R.id.logoutBtn)
        habitRecyclerView = findViewById(R.id.habitRecyclerView)
        emptyText = findViewById(R.id.emptyText)
        userName = findViewById(R.id.userName)
        userImage = findViewById(R.id.userImage)
        missionRecyclerView = findViewById(R.id.missionRecyclerView)
        habitProgress = findViewById(R.id.habitProgress)
        habitDaysLeft = findViewById(R.id.habitDaysLeft)
        masterCheckBox = findViewById(R.id.masterCheckBox)

        userName.text = "Welcome, ${FirebaseAuth.getInstance().currentUser?.displayName ?: "User"}"
        FirebaseAuth.getInstance().currentUser?.photoUrl?.let { Picasso.get().load(it).into(userImage) }

        habitRecyclerView.layoutManager = LinearLayoutManager(this)
        habitAdapter = HabitAdapter(habits, this, this)   // ✅ Passing click listener
        habitRecyclerView.adapter = habitAdapter
        showPlaceholder(habits.isEmpty())

        missionRecyclerView.layoutManager = LinearLayoutManager(this)
        missionAdapter = MissionAdapter(missionHabits) { missionHabit, isChecked ->
            if (isChecked) incrementHabitDay(missionHabit)
            updateMissionProgress()
            saveMissionsToFirestore()
        }
        missionRecyclerView.adapter = missionAdapter

        addHabitBtn.setOnClickListener { showAddHabitDialog() }
        resetMissionBtn.setOnClickListener { showResetConfirmation() }
        logoutBtn.setOnClickListener { showLogoutConfirmation() }

        masterCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingMasterCheckBox) return@setOnCheckedChangeListener
            if (isChecked) checkAllMissionsAndIncrement() else uncheckAllMissionsAndDecrement()
        }

        val touchHelper = ItemTouchHelper(HabitItemTouchHelperCallback(habitAdapter))
        touchHelper.attachToRecyclerView(habitRecyclerView)

        loadHabits()
        loadMissions()
    }

    private fun showLogoutConfirmation() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF0000"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#808080"))
        }

        dialog.show()
    }

    private fun logout() {
        val logoutProgressBar = findViewById<ProgressBar>(R.id.progressBarCenter)
        logoutProgressBar.visibility = View.VISIBLE
        logoutBtn.isEnabled = false

        FirebaseAuth.getInstance().signOut()
        logoutBtn.postDelayed({
            Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }, 500)
    }

    private fun loadHabits() {
        db.child("habits").child(userId).get().addOnSuccessListener { snapshot ->
            habits.clear()
            snapshot.children.mapNotNullTo(habits) { it.getValue(Habit::class.java) }
            habitAdapter.notifyDataSetChanged()
            showPlaceholder(habits.isEmpty())
        }
    }

    private fun loadMissions() {
        firestore.collection("users").document(userId).collection("missions").get()
            .addOnSuccessListener { result ->
                missionHabits.clear()
                missionHabits.addAll(result.mapNotNull { it.toObject(MissionHabit::class.java) })
                updatedMissions.clear()
                missionAdapter.notifyDataSetChanged()
                checkAndResetMissionsIfNewDay()
                updateMissionProgress()
                saveMissionsToPrefs()
            }

    }

    private fun checkAndResetMissionsIfNewDay() {
        val prefs = getSharedPreferences("HabitPrefs", MODE_PRIVATE)
        val lastDate = prefs.getString("lastResetDate", null)
        val today = getTodayDate()

        if (lastDate != today) {
            for (mission in missionHabits) {
                mission.checked = false
                mission.progressUpdated = false
                val docId = "${mission.userId}_${mission.name}_${mission.habitTitle}".replace(" ", "_")
                firestore.collection("users").document(userId).collection("missions")
                    .document(docId).update(
                        mapOf(
                            "checked" to false,
                            "progressUpdated" to false
                        )
                    )
            }
            updatedMissions.clear()
            missionAdapter.notifyDataSetChanged()
            prefs.edit().putString("lastResetDate", today).apply()
        }
    }

    private fun getTodayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun checkAllMissionsAndIncrement() {
        for (mission in missionHabits) {
            if (!mission.checked) {
                mission.checked = true
                incrementHabitDay(mission)
                mission.progressUpdated = false
            }
        }
        missionAdapter.notifyDataSetChanged()
        updateMissionProgress()
        saveMissionsToFirestore()
    }

    private fun uncheckAllMissionsAndDecrement() {
        for (mission in missionHabits) {
            if (mission.checked) {
                mission.checked = false
                decrementHabitDay(mission)
                mission.progressUpdated = false
            }
        }
        missionAdapter.notifyDataSetChanged()
        updateMissionProgress()
        saveMissionsToFirestore()
    }

    private fun incrementHabitDay(mission: MissionHabit) {
        habits.find { it.title == mission.habitTitle }?.let {
            if (it.daysCompleted < it.goalDays) {
                it.daysCompleted++
                val today = getTodayDate()
                if (!it.completionDates.contains(today)) {
                    it.completionDates.add(today)
                }
                db.child("habits").child(userId).child(it.title).setValue(it)
                habitAdapter.notifyItemChanged(habits.indexOf(it))
            }
        }
    }

    private fun decrementHabitDay(mission: MissionHabit) {
        habits.find { it.title == mission.habitTitle }?.let {
            if (it.daysCompleted > 0) {
                it.daysCompleted--
                db.child("habits").child(userId).child(it.title).setValue(it)
                habitAdapter.notifyItemChanged(habits.indexOf(it))
            }
        }
    }

    private fun saveMissionsToFirestore() {
        for (mission in missionHabits) {
            val docId = "${userId}_${mission.name}_${mission.habitTitle}".replace(" ", "_")
            firestore.collection("users").document(userId).collection("missions")
                .document(docId).set(mission, SetOptions.merge())
        }

        saveMissionsToPrefs()
    }
    private fun updateMissionWidget() {
        val intent = Intent(this, MissionWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(
            ComponentName(this, MissionWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }
    private fun saveMissionsToPrefs() {
        val prefs = getSharedPreferences("MissionPrefs", MODE_PRIVATE)
        val editor = prefs.edit()
        val missionJsonList = missionHabits.map {
            "${it.name}##${it.habitTitle}##${it.checked}"
        }
        editor.putStringSet("missions", missionJsonList.toSet())
        editor.apply()

        updateMissionWidget()

    }

    private fun showAddHabitDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habit, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.inputHabitTitle)
        val goalInput = dialogView.findViewById<EditText>(R.id.inputHabitGoal)
        val missionInput = dialogView.findViewById<EditText>(R.id.inputTodayMission)
        val decreaseButton = dialogView.findViewById<ImageButton>(R.id.decreaseButton)
        val increaseButton = dialogView.findViewById<ImageButton>(R.id.increaseButton)

        decreaseButton.setOnClickListener {
            val current = goalInput.text.toString().toIntOrNull() ?: 0
            goalInput.setText((current - 1).coerceAtLeast(0).toString())
        }
        increaseButton.setOnClickListener {
            val current = goalInput.text.toString().toIntOrNull() ?: 0
            goalInput.setText((current + 1).toString())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("New Habit")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString().trim()
                val goal = goalInput.text.toString().toIntOrNull()
                val mission = missionInput.text.toString().trim()
                val startDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

                if (title.isNotEmpty() && goal != null && goal > 0) {
                    val newHabit = Habit(
                        title = title,
                        daysCompleted = 0,
                        goalDays = goal,
                        startDate = startDate
                    )
                    habits.add(newHabit)
                    habitAdapter.notifyItemInserted(habits.size - 1)
                    db.child("habits").child(userId).child(title).setValue(newHabit)
                    showPlaceholder(habits.isEmpty())

                    if (mission.isNotEmpty()) {
                        val newMission = MissionHabit(
                            name = mission,
                            habitTitle = title,
                            checked = false,
                            userId = userId,
                            progressUpdated = false
                        )
                        missionHabits.add(newMission)
                        missionAdapter.notifyItemInserted(missionHabits.size - 1)

                        val docId = "${userId}_${mission}_${title}".replace(" ", "_")
                        firestore.collection("users").document(userId).collection("missions")
                            .document(docId).set(newMission, SetOptions.merge())

                        updateMissionProgress()
                        saveMissionsToPrefs()  // ✅ <--- This is what was missing!
                    }

                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
                updateMissionProgress()
                updateMissionWidget()

            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMissionProgress() {
        val total = missionHabits.size
        val completed = missionHabits.count { it.checked }
        val percent = if (total > 0) (completed * 100 / total) else 0

        habitProgress.setProgress(percent, true)
        habitDaysLeft.text = "$completed/$total"

        isUpdatingMasterCheckBox = true
        masterCheckBox.isChecked = missionHabits.isNotEmpty() && completed == total
        isUpdatingMasterCheckBox = false

        if (missionHabits.isNotEmpty() && completed == total) {
            Toast.makeText(this, "All missions completed! 🎯", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaceholder(show: Boolean) {
        emptyText.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onHabitDeleted(habitTitle: String) {
        val removed = missionHabits.removeAll { it.habitTitle == habitTitle }
        if (removed) {
            missionAdapter.notifyDataSetChanged()
            updateMissionProgress()
            saveMissionsToPrefs() // ✅ Add this line

            firestore.collection("users").document(userId).collection("missions")
                .whereEqualTo("habitTitle", habitTitle).get().addOnSuccessListener { result ->
                    result.forEach { it.reference.delete() }
                }
        }
        db.child("habits").child(userId).child(habitTitle).removeValue()
        showPlaceholder(habitAdapter.itemCount == 0)
    }


    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset All Data")
            .setMessage("Delete all habits and missions?")
            .setPositiveButton("Yes") { _, _ -> resetAllData() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetAllData() {
        db.child("habits").child(userId).removeValue().addOnSuccessListener {
            habits.clear()
            habitAdapter.notifyDataSetChanged()
            showPlaceholder(true)
        }
        firestore.collection("users").document(userId).collection("missions")
            .get().addOnSuccessListener { result ->
                result.forEach { it.reference.delete() }
                missionHabits.clear()
                missionAdapter.notifyDataSetChanged()
                updateMissionProgress()
                saveMissionsToPrefs() // ✅ Add this
            }
        Toast.makeText(this, "All data has been reset", Toast.LENGTH_SHORT).show()
    }


    // ✅ Calendar click - Open Bottom Sheet
    override fun onHabitClicked(habit: Habit) {
        val bottomSheet = HabitCalendarBottomSheet.newInstance(habit)
        bottomSheet.show(supportFragmentManager, "HabitCalendarBottomSheet")
    }
    override fun onResume() {
        super.onResume()
        updateMissionWidget() // 🔁 Refresh the widget every time user returns to app
    }

}
