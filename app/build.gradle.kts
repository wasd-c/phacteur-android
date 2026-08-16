plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val googleServicesFile = file("google-services.json")

if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val verifyReleaseFirebaseConfiguration by tasks.registering {
    group = "verification"
    description = "Fails release builds that cannot receive production email notifications."
    doLast {
        check(googleServicesFile.isFile) {
            "android/app/google-services.json is required for release builds"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseFirebaseConfiguration)
}

val baseUrl = providers.gradleProperty("PHACTEUR_BASE_URL")
    .orElse("https://phacteur.app/")
    .get()
    .let { if (it.endsWith('/')) it else "$it/" }

android {
    namespace = "app.phacteur.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.phacteur.android"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true

        buildConfigField("String", "PHACTEUR_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "MOBILE_AUTH_CLIENT_ID", "\"phacteur-android\"")
        buildConfigField("String", "MOBILE_AUTH_REDIRECT_URI", "\"phacteur://auth/callback\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
