plugins {
    id("com.android.application")
}

android {
    namespace = "com.mixer.xr18.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mixer.xr18.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 60
        versionName = "1.0060"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(project(":lib"))
}