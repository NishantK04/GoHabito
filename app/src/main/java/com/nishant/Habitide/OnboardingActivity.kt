package com.nishant.Habitide

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator


class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var getStartedBtn: Button

    private lateinit var adapter: OnboardingAdapter
    private lateinit var handler: Handler
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val slideRunnable = Runnable {
        val nextItem = if (viewPager.currentItem == adapter.itemCount - 1) 0 else viewPager.currentItem + 1
        viewPager.currentItem = nextItem
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.BLACK
        setContentView(R.layout.activity_onboarding)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // from google-services.json
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        viewPager = findViewById(R.id.onboardingViewPager)
        getStartedBtn = findViewById(R.id.btnGetStarted)
        progressBar = findViewById(R.id.loadingBar)

        val items = listOf(
            OnboardingItem(R.drawable.intro1),
            OnboardingItem(R.drawable.intro2),
        )

        adapter = OnboardingAdapter(items)
        viewPager.adapter = adapter


        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {

            }
        })

        getStartedBtn.setOnClickListener {
//            startActivity(Intent(this, MainActivity::class.java))
//            finish()
            launchGoogleSignIn()
        }
        val dotsIndicator = findViewById<WormDotsIndicator>(R.id.dotsIndicator)
        dotsIndicator.setViewPager2(viewPager)


        handler = Handler(Looper.getMainLooper())
        autoSlide()
    }

    private fun autoSlide() {
        handler.postDelayed(slideRunnable, 3000)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                handler.removeCallbacks(slideRunnable)
                handler.postDelayed(slideRunnable, 3000)
            }
        })
    }
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) {
            val account = task.result
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            progressBar.visibility = View.VISIBLE
            getStartedBtn.visibility = View.GONE

            auth.signInWithCredential(credential).addOnCompleteListener {
                if (it.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Firebase auth failed", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    getStartedBtn.visibility = View.VISIBLE
                }
            }
        } else {
            Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
        }
    }
    private fun launchGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

}