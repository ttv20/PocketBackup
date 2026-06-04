plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val releaseStoreFile = providers.environmentVariable("POCKETSYNC_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("POCKETSYNC_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("POCKETSYNC_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("POCKETSYNC_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val fdroidNativeAssetsDir = rootProject.layout.projectDirectory.dir("native/fdroid-out/assets")
val fdroidNativeJniLibsDir = rootProject.layout.projectDirectory.dir("native/fdroid-out/jniLibs")

android {
    namespace = "com.ttv20.rsyncbackup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ttv20.rsyncbackup"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.environmentVariable("POCKETSYNC_VERSION_CODE")
            .map { it.toInt() }
            .getOrElse(1)
        versionName = providers.environmentVariable("POCKETSYNC_VERSION_NAME")
            .getOrElse("0.1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("fdroidDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("debug")
        }
        create("fdroidRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = null
        }
    }

    sourceSets {
        getByName("debug") {
            assets.srcDir("src/sideload/assets")
            jniLibs.srcDir("src/sideload/jniLibs")
        }
        getByName("release") {
            assets.srcDir("src/sideload/assets")
            jniLibs.srcDir("src/sideload/jniLibs")
        }
        getByName("fdroidDebug") {
            assets.srcDir("src/fdroidRelease/assets")
            assets.srcDir(fdroidNativeAssetsDir.asFile)
            assets.srcDir(layout.buildDirectory.get().asFile.resolve("generated/fdroidNativeManifest/assets"))
            jniLibs.srcDir(fdroidNativeJniLibsDir.asFile)
        }
        getByName("fdroidRelease") {
            assets.srcDir(fdroidNativeAssetsDir.asFile)
            assets.srcDir(layout.buildDirectory.get().asFile.resolve("generated/fdroidNativeManifest/assets"))
            jniLibs.srcDir(fdroidNativeJniLibsDir.asFile)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val generateFdroidNativeManifest by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/fdroidNativeManifest/assets/native")
    val sourceRefs = providers.environmentVariable("FDROID_NATIVE_SOURCE_REFS")
        .orElse("pending-source-build")
    val androidImage = providers.environmentVariable("RSYNC_BACKUP_ANDROID_IMAGE")
        .orElse("cimg/android:2025.12")
    val nativeImage = providers.environmentVariable("GO_ANDROID_IMAGE")
        .orElse("golang:1.26-bookworm")

    inputs.property("sourceRefs", sourceRefs)
    inputs.property("androidImage", androidImage)
    inputs.property("nativeImage", nativeImage)
    outputs.dir(outputDir)

    doLast {
        val manifest = outputDir.get().asFile.resolve("fdroid-native-manifest.json")
        manifest.parentFile.mkdirs()
        manifest.writeText(
            """
            {
              "schemaVersion": 1,
              "variant": "fdroidRelease",
              "nativeSourceRefs": "${sourceRefs.get()}",
              "androidBuildImage": "${androidImage.get()}",
              "nativeBuildImage": "${nativeImage.get()}",
              "toolchainVersions": {},
              "outputHashes": []
            }
            """.trimIndent() + "\n",
        )
    }
}

val checkFdroidNoPrebuiltNative by tasks.registering(Exec::class) {
    commandLine(rootProject.file("scripts/fdroid-scan-source.sh"), "--gradle")
}

val checkFdroidGeneratedNative by tasks.registering {
    val requiredNativeAssets = listOf(
        "native/arm64-v8a/rsync",
        "native/arm64-v8a/ssh",
        "native/arm64-v8a/ssh-keygen",
        "native/arm64-v8a/ssh-keyscan",
        "native/arm64-v8a/scp",
        "native/arm64-v8a/sftp",
        "native/arm64-v8a/tsnet-nc",
        "native/arm64-v8a/lib/libcrypto.so.3",
        "native/arm64-v8a/lib/libz.so.1",
    )
    val requiredNativeLibraries = listOf(
        "arm64-v8a/librsync_exec.so",
        "arm64-v8a/libssh_exec.so",
        "arm64-v8a/libssh_keygen_exec.so",
        "arm64-v8a/libssh_keyscan_exec.so",
        "arm64-v8a/libscp_exec.so",
        "arm64-v8a/libsftp_exec.so",
        "arm64-v8a/libtsnet_nc_exec.so",
        "arm64-v8a/libcrypto.so.3",
        "arm64-v8a/libz.so.1",
    )
    val requiredFiles = requiredNativeAssets.map { fdroidNativeAssetsDir.file(it).asFile } +
        requiredNativeLibraries.map { fdroidNativeJniLibsDir.file(it).asFile }
    val projectDirPath = rootProject.projectDir.toPath()

    inputs.files(requiredFiles)

    doLast {
        val missing = requiredFiles
            .filterNot { it.exists() }
            .map { projectDirPath.relativize(it.toPath()).toString() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing generated F-Droid native assets:\n" +
                    missing.joinToString(separator = "\n") { "  $it" } +
                    "\nRun ./scripts/docker-fdroid-build-native.sh --from-source before building fdroidDebug or fdroidRelease.",
            )
        }
    }
}

tasks.matching {
    it.name == "preFdroidDebugBuild" ||
        it.name == "preFdroidReleaseBuild" ||
        it.name == "mergeFdroidDebugAssets" ||
        it.name == "mergeFdroidReleaseAssets"
}.configureEach {
    dependsOn(checkFdroidNoPrebuiltNative)
    dependsOn(checkFdroidGeneratedNative)
}

tasks.matching {
    it.name == "mergeFdroidDebugAssets" || it.name == "mergeFdroidReleaseAssets"
}.configureEach {
    dependsOn(generateFdroidNativeManifest)
}

tasks.matching {
    (it.name.contains("FdroidDebug") || it.name.contains("FdroidRelease")) &&
        it.name != "generateFdroidNativeManifest"
}.configureEach {
    dependsOn(generateFdroidNativeManifest)
}
