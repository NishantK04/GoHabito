package com.nishant.gohabito

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsetsController
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), HabitDeleteListener, HabitClickListener {
    private var habitAddCount = 0
    private val PREF_WIDGET_PROMPT_SHOWN = "widget_prompt_shown"


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
    private lateinit var feedbackbtn: ImageButton

    private var isUpdatingMasterCheckBox = false



    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Make layout go under the status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set your light background color
        window.statusBarColor = Color.parseColor("#F0F4FF")

        // Make status bar icons dark (for light background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    )
        }
        // Your color
        val sharedPreferences = getSharedPreferences("onboarding", MODE_PRIVATE)
        val onboardingCompleted = sharedPreferences.getBoolean("onboardingCompleted", false)

        if (!onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }


        setContentView(R.layout.activity_main)


        val prefs = getSharedPreferences("my_app_prefs", MODE_PRIVATE)
        habitAddCount = prefs.getInt("habit_add_count", 0)



        FirebaseAuth.getInstance().currentUser?.let { user ->
            userId = user.uid
            // Call the new function to save the token
            saveFCMToken()
        } ?: run {
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

        val displayName = FirebaseAuth.getInstance().currentUser?.displayName ?: "User"
        val firstName = displayName.split(" ")[0] // Get only the first name
        userName.text = "Welcome, $firstName"


        FirebaseAuth.getInstance().currentUser?.photoUrl?.let { Picasso.get().load(it).into(userImage) }

        habitRecyclerView.layoutManager = LinearLayoutManager(this)
        habitAdapter = HabitAdapter(habits, this, this)
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

        feedbackbtn = findViewById(R.id.feedbackbtn)

        feedbackbtn.setOnClickListener {
            showFeedbackDialog()
        }

        saveInstallDateIfFirstTime()

        loadHabits()


        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        FirebaseFirestore.getInstance().collection("users")
                            .document(userId)
                            .update("fcmToken", token)
                            .addOnSuccessListener {}
                            .addOnFailureListener {}
                    }
                }
            }


    }
    private fun saveInstallDateIfFirstTime() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        if (!prefs.contains("installDate")) {
            val installTime = System.currentTimeMillis()
            prefs.edit().putLong("installDate", installTime).apply()
        }
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
        if(!isInternetAvailable()){
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        db.child("habits").child(userId).get().addOnSuccessListener { snapshot ->
            habits.clear()
            snapshot.children.mapNotNullTo(habits) { it.getValue(Habit::class.java) }
            habitAdapter.notifyDataSetChanged()
            showPlaceholder(habits.isEmpty())
            loadMissions()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load habits", Toast.LENGTH_SHORT).show()
        }
    }
    // Inside MainActivity.kt, modify the loadMissions function
    private fun loadMissions() {
        firestore.collection("users").document(userId).collection("missions").get()
            .addOnSuccessListener { result ->
                missionHabits.clear()
                val allMissions = result.mapNotNull { it.toObject(MissionHabit::class.java) }

                // Create a set of titles for habits that have reached their goal
                val completedHabitTitles = habits.filter {
                    // This is the core logic: check if daysCompleted equals goalDays
                    it.daysCompleted >= it.goalDays
                }.map { it.title }.toSet()

                // Filter out missions whose habits are completed
                val filteredMissions = allMissions.filter {
                    it.habitTitle !in completedHabitTitles
                }

                missionHabits.addAll(filteredMissions)
                updatedMissions.clear()

                // Call the reset logic AFTER filtering the missions.
                checkAndResetMissionsIfNewDay()

                missionAdapter.notifyDataSetChanged()
                updateMissionProgress()
                saveMissionsToPrefs()
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to load missions", Toast.LENGTH_SHORT).show()
            }
    }

    // Ensure loadHabits is called before loadMissions, maybe in onCreate


    private fun checkAndResetMissionsIfNewDay() {
        val prefs = getSharedPreferences("HabitPrefs", MODE_PRIVATE)
        val lastDate = prefs.getString("lastResetDate", null)
        val today = getTodayDate()

        if (lastDate != today) {
            // Find missions that need to be deleted from the previous day
            val missionsToDelete = missionHabits.filter { it.progressUpdated }

            // Remove these missions from the local list and Firestore
            for (mission in missionsToDelete) {
                deleteMissionForHabit(mission.habitTitle)
            }

            // Now, reset the remaining, active missions
            for (mission in missionHabits) {
                // This loop will only contain missions that haven't been deleted yet
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
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())

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
        habits.find { it.title == mission.habitTitle }?.let { habit ->
            // This is the core change: we increment outside the if statement
            // to ensure the completion check runs every time.
            if (habit.daysCompleted < habit.goalDays) {
                habit.daysCompleted++
                val today = getTodayDate()
                if (!habit.completionDates.contains(today)) {
                    habit.completionDates.add(today)
                }
                db.child("habits").child(userId).child(habit.title).setValue(habit)
                habitAdapter.notifyItemChanged(habits.indexOf(habit))
            }

            // If the habit is now completed, mark the mission and prepare for removal
            if (habit.daysCompleted >= habit.goalDays) {
                // Set the progressUpdated flag to signal it's done for the day and ready for deletion
                mission.progressUpdated = true

                // Now remove the habit from the local list so it doesn't appear in the adapter
                habits.remove(habit)
                habitAdapter.notifyDataSetChanged()

                // We do NOT delete the mission from Firestore yet.
                // That will happen on the next day's reset.
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

                if (title.isNotEmpty() && goal != null && goal > 0 && mission.isNotBlank()) {
                    val newHabit = Habit(
                        title = title,
                        daysCompleted = 0,
                        goalDays = goal,
                        startDate = startDate
                    )
                    habits.add(newHabit)
                    habitAdapter.notifyItemInserted(habits.size - 1)
                    habitAddCount++
                    getSharedPreferences("my_app_prefs", MODE_PRIVATE)
                        .edit()
                        .putInt("habit_add_count", habitAddCount)
                        .apply()

                    val prefs = getSharedPreferences("my_app_prefs", MODE_PRIVATE)
                    val widgetPromptShown = prefs.getBoolean(PREF_WIDGET_PROMPT_SHOWN, false)

                    if (habitAddCount >= 1 && !widgetPromptShown) {
                        prefs.edit().putBoolean(PREF_WIDGET_PROMPT_SHOWN, true).apply()
                        showWidgetPromptDialog()
                    }
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
                        saveMissionsToPrefs()  //  <--- This is what was missing!
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

        // ✨ Animate the progress bar smoothly
        val animator = ValueAnimator.ofInt(habitProgress.progress, percent)
        animator.duration = 500  // snappier
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            val animatedValue = it.animatedValue as Int
            habitProgress.progress = animatedValue
        }
        animator.start()

        //  Text in center
        habitDaysLeft.text = "$completed/$total"

        // 🎨 Change color dynamically based on progress
        val color = when {
            percent >= 80 -> Color.parseColor("#4CAF50") // Green
            percent >= 50 -> Color.parseColor("#FFC107") // Amber
            else -> Color.parseColor("#F44336")          // Red
        }
        habitProgress.setIndicatorColor(color)

        //  Optional PULSE animation on 100% completion
        if (percent == 100) {
            habitProgress.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).withEndAction {
                habitProgress.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
        }

        //  Checkbox safe state update
        isUpdatingMasterCheckBox = true
        masterCheckBox.isChecked = total > 0 && completed == total
        isUpdatingMasterCheckBox = false

        //  Optional feedback
        if (total > 0 && completed == total) {
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
            saveMissionsToPrefs() //  Add this line

            firestore.collection("users").document(userId).collection("missions")
                .whereEqualTo("habitTitle", habitTitle).get().addOnSuccessListener { result ->
                    result.forEach { it.reference.delete() }
                }
        }
        db.child("habits").child(userId).child(habitTitle).removeValue()
        showPlaceholder(habitAdapter.itemCount == 0)
    }
    private fun deleteMissionForHabit(habitTitle: String) {
        // 1. Remove from local list
        missionHabits.removeAll { it.habitTitle == habitTitle }
        missionAdapter.notifyDataSetChanged()

        // 2. Remove from Firestore
        firestore.collection("users").document(userId).collection("missions")
            .whereEqualTo("habitTitle", habitTitle)
            .get()
            .addOnSuccessListener { result ->
                result.forEach { doc ->
                    doc.reference.delete()
                }
                // 3. Update UI and widget
                updateMissionProgress()
                saveMissionsToPrefs()
            }
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


    //  Calendar click - Open Bottom Sheet
    override fun onHabitClicked(habit: Habit) {
        val bottomSheet = HabitCalendarBottomSheet.newInstance(habit)
        bottomSheet.show(supportFragmentManager, "HabitCalendarBottomSheet")
    }
    override fun onResume() {
        super.onResume()
        updateMissionWidget() // 🔁 Refresh the widget every time user returns to app
        maybeShowRatingDialog()
        loadHabits()
    }
    private fun showFeedbackDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.feedback_dialog, null)

        val spinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerFeedbackType)
        val editText = dialogView.findViewById<EditText>(R.id.editTextFeedbackMsg)

        val types = arrayOf("Feedback", "Bug Report")
        val adapter = ArrayAdapter(this, R.layout.dropdown_item, types) // Use a custom layout if desired
        spinner.setAdapter(adapter)

        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Submit", null) // Set later to prevent auto-dismiss
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.setOnShowListener {
            val submitButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submitButton.setOnClickListener {
                val type = spinner.text.toString()
                val message = editText.text.toString().trim()

                if (type.isNotEmpty() && message.isNotEmpty()) {
                    val data = hashMapOf(
                        "type" to type,
                        "message" to message,
                        "timestamp" to System.currentTimeMillis()
                    )

                    firestore.collection("Feedbacks")
                        .add(data)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Thanks for your $type!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            if (type == "Feedback") {
                                showRatingStarsDialog()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to submit.", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showRatingStarsDialog() {
        val ratingView = LayoutInflater.from(this).inflate(R.layout.dialog_rating_stars, null)
        val ratingBar = ratingView.findViewById<RatingBar>(R.id.ratingBar)
        val laterText = ratingView.findViewById<TextView>(R.id.laterText)

        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedAlertDialog)
            .setView(ratingView)
            .setCancelable(true)
            .create()

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            playBounceAnimation(ratingBar)

            getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("hasRatedApp", true)
                .apply()

            dialog.dismiss()
            if (rating >= 2.5) {
                openPlayStoreForRating()
            } else {
                Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
            }
        }

        laterText.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun openPlayStoreForRating() {
        val packageName = packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }
    private fun playBounceAnimation(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.3f, 1f)

        scaleX.duration = 300
        scaleY.duration = 300

        scaleX.interpolator = android.view.animation.OvershootInterpolator()
        scaleY.interpolator = android.view.animation.OvershootInterpolator()

        scaleX.start()
        scaleY.start()
    }

    private fun maybeShowRatingDialog() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        // Check if user already rated
        val hasRated = prefs.getBoolean("hasRatedApp", false)
        if (hasRated) return

        // Track and update launch count
        val launchCount = prefs.getInt("launchCount", 0) + 1
        prefs.edit().putInt("launchCount", launchCount).apply()

        // Get install date and calculate days passed
        val installDate = prefs.getLong("installDate", 0L)
        val daysSinceInstall = (System.currentTimeMillis() - installDate) / (1000 * 60 * 60 * 24)

        // Show rating dialog only after 5 launches and 3 days
        if (launchCount >= 5 || daysSinceInstall >= 3) {
            showRatingStarsDialog()
        }
    }

    private fun saveFCMToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            val userDocRef = firestore.collection("users").document(userId)

            // Use a transaction to check if the document exists before writing.
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef)

                // If the document doesn't exist, create it first.
                if (!snapshot.exists()) {
                    val newUser = hashMapOf("fcmToken" to token)
                    transaction.set(userDocRef, newUser)
                } else {
                    // If it exists, simply update the token.
                    transaction.update(userDocRef, "fcmToken", token)
                }
                null
            }.addOnSuccessListener {
            }.addOnFailureListener {
            }
        }
    }

    private fun showWidgetPromptDialog() {
        val dialogTheme = R.style.RoundedAlertDialog

        MaterialAlertDialogBuilder(this, dialogTheme)
            .setTitle("Add Habit Widget")
            .setMessage("Want quick access? Add our habit widget to your home screen!")
            .setNeutralButton("How to Add Widget") { _, _ ->
                MaterialAlertDialogBuilder(this, dialogTheme)
                    .setTitle("How to Add Widget")
                    .setMessage("1. Long-press on your home screen.\n2. Tap on 'Widgets'.\n3. Find 'GoHabito' widget.\n4. Drag it to your home screen.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
