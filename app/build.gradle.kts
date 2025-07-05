import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

// ✅ Load properties from local.properties
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val defaultWebClientId: String = localProperties.getProperty("DEFAULT_WEB_CLIENT_ID") ?: ""

android {
    namespace = "com.nishant.Habitide"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nishant.Habitide"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Inject secure values into AndroidManifest.xml
        manifestPlaceholders.putAll(
            mapOf(
                "DEFAULT_WEB_CLIENT_ID" to defaultWebClientId
            )
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "androidx.core" &&
                (requested.name == "core" || requested.name == "core-ktx")) {
                useVersion("1.16.0")
                because("Avoid duplicate classes from old versions")
            }
        }
    }
}

dependencies {
    implementation("com.applandeo:material-calendar-view:1.9.2")

    // ✅ Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.15.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // ✅ Google Sign-In
    implementation("com.google.android.gms:play-services-auth")

    // ✅ UI Libraries
    implementation("com.google.android.material:material:1.12.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation("com.mikhaellopez:circularprogressbar:3.1.0")
    implementation("com.tbuonomo:dotsindicator:4.3")
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // ✅ AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ✅ Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
