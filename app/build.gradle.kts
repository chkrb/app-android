dependencies {
    // AndroidX Compose (with BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    // Firebase (with BOM)
    implementation(platform(libs.firebase.bom))
    // Other Dependencies
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.jna) {
        artifact {
            extension = "aar"
            type = "aar"
        }
    }
    implementation(libs.kotlinx.serialization.json)

    // AndroidX Compose Testing (with BOM)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Other Testing Dependencies
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val abiTargets = listOf("armeabi-v7a", "arm64-v8a", "x86_64")

android {
    namespace = "io.github.easeatten"

    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileSdk { version = release(37) }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        applicationId = "io.github.easeatten"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true

            reset()
            abiTargets.forEach { include(it) }
        }
    }
}

tasks.register<Exec>("rustSxcapiUniFfi") {
    description = "Generates bindings for rust crate dependency `sxcapi` with UniFFI."
    group = "rust"

    val abi = abiTargets.first()

    workingDir = file("src/main/rust/sxcapi")
    inputs.dir(workingDir)
    inputs.file("src/main/jniLibs/$abi/libsxcapi.so")
    inputs.file("src/main/uniffi.toml")
    outputs.dir("src/main/kotlin/generated/sxcapi")

    commandLine(
        "uniffi-bindgen",
        "generate",
        "../../jniLibs/$abi/libsxcapi.so",
        "--language=kotlin",
        "--out-dir=../../kotlin/generated",
        "--config=../../uniffi.toml",
        "--no-format",
    )
}

tasks.named("preBuild") { dependsOn("rustSxcapiUniFfi") }

abiTargets.forEach { abi ->
    val rustSxcapiBuildTask = "rustSxcapiBuild_${abi.replace("-", "_")}"
    tasks.register<Exec>(rustSxcapiBuildTask) {
        description = "Builds rust crate dependency `sxcapi` ($abi)."
        group = "rust"

        workingDir = file("src/main/rust/sxcapi")
        inputs.dir(workingDir)
        outputs.dir("src/main/jniLibs/$abi")

        commandLine(
            "cargo",
            "ndk",
            "--target=$abi",
            "--output-dir=../../jniLibs",
            "build",
            "--release",
            "--features=openssl/vendored",
        )
    }

    tasks.named("rustSxcapiUniFfi") { dependsOn(rustSxcapiBuildTask) }
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach { exclude("**/generated/**") }

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    exclude("**/generated/**")
}
