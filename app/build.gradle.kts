import java.util.Properties

plugins {
    id("com.android.application")
}

fun signingValue(envName: String, propName: String, props: Properties): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: props.getProperty(propName)?.takeIf { it.isNotBlank() }

val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.isFile) {
    // UTF-8: storeFile may contain non-ASCII (e.g. ~/文档/...)
    keystorePropsFile.reader(Charsets.UTF_8).use { keystoreProps.load(it) }
}

val releaseStorePath = signingValue("SIGNING_STORE_FILE", "storeFile", keystoreProps)
val releaseStorePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword", keystoreProps)
val releaseKeyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias", keystoreProps)
val releaseKeyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword", keystoreProps)
val releaseStoreFile = releaseStorePath?.let { path ->
    val asFile = file(path)
    if (asFile.isAbsolute) asFile else rootProject.file(path)
}
val canSignRelease = releaseStoreFile != null &&
    releaseStoreFile.isFile &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

if (canSignRelease) {
    logger.lifecycle("Release signing with ${releaseStoreFile!!.absolutePath}")
} else {
    logger.lifecycle("Release signing fallback: debug keystore")
}

android {
    namespace = "io.github.xiaoyueyoqwq.ims"
    defaultConfig {
        applicationId = "io.github.xiaoyueyoqwq.ims"
        versionCode = 12
        versionName = "4.1.1"
    }
    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs["debug"]
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles("proguard-rules.pro")
            signingConfig = if (canSignRelease) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {
    compileOnly(project(":stub"))
    implementation(libs.hiddenapibypass)
    implementation(libs.kadb)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
