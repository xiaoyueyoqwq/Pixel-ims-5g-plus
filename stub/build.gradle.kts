plugins {
    id("com.android.library")
}

android {
    namespace = "stub"
    compileSdk {
        version = release(37)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        aidl = true
    }
}
