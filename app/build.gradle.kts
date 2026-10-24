plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "org.kbraj003.rfphysiology"
    compileSdk = 35
    defaultConfig { applicationId = "org.kbraj003.rfphysiology"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


// Automatically aligned with Java 17.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}


dependencies {
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.activity:activity-ktx:1.9.3")
}
