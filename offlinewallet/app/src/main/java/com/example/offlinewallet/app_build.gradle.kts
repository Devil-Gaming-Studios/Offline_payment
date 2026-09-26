plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.offlinewallet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.offlinewallet"
        minSdk = 26          // BLE + modern crypto APIs need at least this
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
