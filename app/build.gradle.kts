import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)

}

// Load version properties
val versionProps = Properties().apply {
    val versionFile = file("${project.rootDir}/version.properties")
    if (versionFile.exists()) {
        FileInputStream(versionFile).use { load(it) }
    }
}

fun getVersionCode(): Int = versionProps.getProperty("VERSION_CODE", "1").toInt()
fun getVersionName(): String {
    val major = versionProps.getProperty("VERSION_MAJOR", "1")
    val minor = versionProps.getProperty("VERSION_MINOR", "0")
    val patch = versionProps.getProperty("VERSION_PATCH", "0")
    return "$major.$minor.$patch"
}

android {
    namespace = "ch.heuscher.safe_home_button"
    compileSdk = 36

    defaultConfig {
        applicationId = "ch.heuscher.safe_home_button"
        minSdk = 26
        targetSdk = 36
        versionCode = getVersionCode()
        versionName = getVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "key0"
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}