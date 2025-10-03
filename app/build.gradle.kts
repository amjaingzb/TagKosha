plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.jbros.tagkosha"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jbros.tagkosha"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // --- ADDED AND EDITED FIREBASE DEPENDENCIES ---
    // ADDED: Import the Firebase BoM - this manages versions for other Firebase libs
    implementation(platform(libs.firebase.bom))

    // ADDED: The actual dependency for Firebase Authentication
    implementation(libs.firebase.auth)

    // This line is correct, it will now get its version from the BOM
    implementation(libs.firebase.firestore)
    // --- END FIREBASE DEPENDENCIES ---
    implementation(libs.timber) // ADD THIS LINE

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}