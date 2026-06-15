plugins {
    id("com.android.application")
    id("sh.measure.android.gradle")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun Provider<String>.nonBlankOrElse(fallback: Provider<String>): Provider<String> =
    orElse("").flatMap { value ->
        if (value.isBlank()) {
            fallback
        } else {
            providers.provider { value }
        }
    }

fun diagnosticsConfigValue(
    envName: String,
    propertyName: String,
    officialDefault: Provider<String>,
    defaultValue: String = "",
): Provider<String> =
    providers.environmentVariable(envName)
        .orElse("")
        .nonBlankOrElse(providers.gradleProperty(propertyName))
        .nonBlankOrElse(officialDefault)
        .nonBlankOrElse(providers.provider { defaultValue })

fun configBooleanValue(envName: String, propertyName: String, defaultValue: Boolean = false): Provider<Boolean> =
    providers.environmentVariable(envName)
        .orElse("")
        .nonBlankOrElse(providers.gradleProperty(propertyName))
        .orElse(defaultValue.toString())
        .map { value ->
            value.trim().lowercase() in setOf("1", "true", "yes", "on")
        }

fun quotedBuildConfig(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"") + "\""

fun normalizedVersionTag(tag: String): String =
    tag.trim().let { value ->
        if (value.startsWith("v", ignoreCase = true)) {
            value.drop(1)
        } else {
            value
        }
    }

fun isOfficialPocketBackupRemote(url: String): Boolean {
    val normalized = url
        .trim()
        .removeSuffix("/")
        .removeSuffix(".git")
        .replace("ssh://git@", "")
        .replace("git@", "")
        .replace(":", "/")
        .removePrefix("https://")
        .removePrefix("http://")
        .lowercase()

    return normalized in setOf(
        "codeberg.org/ttv20/pocketbackup",
        "github.com/ttv20/pocketbackup",
    )
}

val releaseStoreFile = providers.environmentVariable("POCKETBACKUP_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("POCKETBACKUP_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("POCKETBACKUP_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("POCKETBACKUP_RELEASE_KEY_PASSWORD").orNull
val pocketBackupVersionCode = providers.environmentVariable("POCKETBACKUP_VERSION_CODE")
    .orElse(providers.gradleProperty("pocketbackup.versionCode"))
val gitTagVersionName = providers.exec {
    commandLine(
        "sh",
        "-c",
        "git describe --tags --abbrev=0 --match 'v[0-9]*' --match '[0-9]*' 2>/dev/null",
    )
    workingDir(rootProject.projectDir)
    isIgnoreExitValue = true
    environment("GIT_CONFIG_COUNT", "1")
    environment("GIT_CONFIG_KEY_0", "safe.directory")
    environment("GIT_CONFIG_VALUE_0", rootProject.projectDir.absolutePath)
}.standardOutput.asText.map(::normalizedVersionTag)
val pocketBackupVersionName = providers.environmentVariable("POCKETBACKUP_VERSION_NAME")
    .orElse("")
    .nonBlankOrElse(providers.gradleProperty("pocketbackup.versionName"))
    .orElse("")
    .nonBlankOrElse(gitTagVersionName)
    .orElse("")
    .nonBlankOrElse(providers.provider { "0.1.0" })
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val officialMeasureApiKey = "msrsh_13b78d97e130432a1f03a956c8a28e8ed00985d6b9269db4447a0227e0679aaa_fcc17818"
val officialOpenObserveIngestUrl = "https://opos.ttv20.com/api/default/pocketbackup/_json"
val officialOpenObserveAuthHeader = "Basic cG9ja2V0YmFja3VwQGFuZHJvaWQuYXBwOktHWUwxMno4dmdzMmF2ZTI="
val emptyOfficialDiagnosticsDefault = providers.provider { "" }
val officialDiagnosticsEnabled = configBooleanValue(
    envName = "POCKETBACKUP_OFFICIAL_DIAGNOSTICS_ENABLED",
    propertyName = "pocketbackup.officialDiagnosticsEnabled",
)
val diagnosticsUserOptInRequired = configBooleanValue(
    envName = "POCKETBACKUP_DIAGNOSTICS_USER_OPT_IN_REQUIRED",
    propertyName = "pocketbackup.diagnosticsUserOptInRequired",
)
val measureBuildUploadEnabled = configBooleanValue(
    envName = "POCKETBACKUP_MEASURE_BUILD_UPLOAD_ENABLED",
    propertyName = "measure.buildUploadEnabled",
)
val gitRemoteOriginUrl = providers.exec {
    commandLine("git", "config", "--get", "remote.origin.url")
    workingDir(rootProject.projectDir)
    isIgnoreExitValue = true
    environment("GIT_CONFIG_COUNT", "1")
    environment("GIT_CONFIG_KEY_0", "safe.directory")
    environment("GIT_CONFIG_VALUE_0", rootProject.projectDir.absolutePath)
}.standardOutput.asText.map { it.trim() }
val officialDiagnosticsConfigIncluded = providers.provider {
    if (!officialDiagnosticsEnabled.get()) {
        false
    } else {
        val remoteUrl = gitRemoteOriginUrl.orNull.orEmpty()
        if (!isOfficialPocketBackupRemote(remoteUrl)) {
            throw org.gradle.api.GradleException(
                "pocketbackup.officialDiagnosticsEnabled=true can only use committed diagnostics defaults " +
                    "from the official Pocket Backup repository. Set explicit diagnostics env vars " +
                    "or Gradle properties for fork builds.",
            )
        }
        true
    }
}

fun officialDiagnosticsDefault(value: String): Provider<String> =
    officialDiagnosticsConfigIncluded.map { included -> if (included) value else "" }

val measureApiUrl = diagnosticsConfigValue(
    envName = "MEASURE_API_URL",
    propertyName = "measure.apiUrl",
    officialDefault = emptyOfficialDiagnosticsDefault,
    defaultValue = "https://ingest.measure.sh",
)
val measureApiKey = diagnosticsConfigValue(
    envName = "MEASURE_API_KEY",
    propertyName = "measure.apiKey",
    officialDefault = officialDiagnosticsDefault(officialMeasureApiKey),
)
val diagnosticsProxyUrl = diagnosticsConfigValue(
    envName = "DIAGNOSTICS_PROXY_URL",
    propertyName = "diagnostics.proxyUrl",
    officialDefault = emptyOfficialDiagnosticsDefault,
)
val openObserveIngestUrl = diagnosticsConfigValue(
    envName = "OPENOBSERVE_INGEST_URL",
    propertyName = "openobserve.ingestUrl",
    officialDefault = officialDiagnosticsDefault(officialOpenObserveIngestUrl),
)
val openObserveAuthHeader = diagnosticsConfigValue(
    envName = "OPENOBSERVE_AUTH_HEADER",
    propertyName = "openobserve.authHeader",
    officialDefault = officialDiagnosticsDefault(officialOpenObserveAuthHeader),
)
val diagnosticsBackendConfigured = providers.provider {
    val hasMeasure = measureApiUrl.get().isNotBlank() && measureApiKey.get().isNotBlank()
    val hasDirectOpenObserve = openObserveIngestUrl.get().isNotBlank() && openObserveAuthHeader.get().isNotBlank()
    val hasProxy = diagnosticsProxyUrl.get().isNotBlank()
    hasMeasure || hasDirectOpenObserve || hasProxy
}
val measureBuildUploadConfigured = providers.provider {
    measureBuildUploadEnabled.get() && measureApiUrl.get().isNotBlank() && measureApiKey.get().isNotBlank()
}
val fdroidNativeAssetsDir = rootProject.layout.projectDirectory.dir("native/fdroid-out/assets")
val fdroidNativeJniLibsDir = rootProject.layout.projectDirectory.dir("native/fdroid-out/jniLibs")

android {
    namespace = "com.ttv20.rsyncbackup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ttv20.rsyncbackup"
        minSdk = 29
        targetSdk = 36
        versionCode = pocketBackupVersionCode.map { it.toInt() }.getOrElse(1)
        versionName = pocketBackupVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["measureApiUrl"] = measureApiUrl.get()
        manifestPlaceholders["measureApiKey"] = measureApiKey.get()
        buildConfigField("String", "MEASURE_API_URL", quotedBuildConfig(measureApiUrl.get()))
        buildConfigField("String", "MEASURE_API_KEY", quotedBuildConfig(measureApiKey.get()))
        buildConfigField("String", "DIAGNOSTICS_PROXY_URL", quotedBuildConfig(diagnosticsProxyUrl.get()))
        buildConfigField("String", "OPENOBSERVE_INGEST_URL", quotedBuildConfig(openObserveIngestUrl.get()))
        buildConfigField("String", "OPENOBSERVE_AUTH_HEADER", quotedBuildConfig(openObserveAuthHeader.get()))
        buildConfigField("boolean", "OFFICIAL_DIAGNOSTICS_ENABLED", officialDiagnosticsConfigIncluded.get().toString())
        buildConfigField("boolean", "DIAGNOSTICS_BACKEND_CONFIGURED", diagnosticsBackendConfigured.get().toString())
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

    buildTypes.configureEach {
        val isFdroid = name.startsWith("fdroid", ignoreCase = true)
        val userOptInRequired = isFdroid || diagnosticsUserOptInRequired.get()
        buildConfigField("boolean", "IS_FDROID_BUILD", isFdroid.toString())
        buildConfigField("boolean", "DIAGNOSTICS_USER_OPT_IN_REQUIRED", userOptInRequired.toString())
        buildConfigField("boolean", "DIAGNOSTICS_WELCOME_DEFAULT_CHECKED", (!isFdroid && !userOptInRequired).toString())
        buildConfigField("String", "BUILD_CHANNEL", quotedBuildConfig(name))
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
        buildConfig = true
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
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("sh.measure:measure-android:0.17.0")
    implementation("ch.acra:acra-core:5.13.1")

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
    val androidImage = providers.environmentVariable("RSYNC_BACKUP_ANDROID_IMAGE")
        .orElse("cimg/android:2025.12")
    val nativeImage = providers.environmentVariable("GO_ANDROID_IMAGE")
        .orElse("golang:1.26-bookworm")
    val sourceRefsFile = fdroidNativeAssetsDir.file("native/arm64-v8a/fdroid-native-source-refs.txt").asFile

    inputs.property("sourceRefs", sourceRefs.orNull ?: "")
    inputs.file(sourceRefsFile).optional()
    inputs.property("androidImage", androidImage)
    inputs.property("nativeImage", nativeImage)
    outputs.dir(outputDir)

    doLast {
        val manifest = outputDir.get().asFile.resolve("fdroid-native-manifest.json")
        val sourceRefsValue = sourceRefs.orNull
            ?: sourceRefsFile.takeIf { it.isFile }?.readText()?.trim()
            ?: "pending-source-build"
        fun jsonEscape(value: String): String = buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

        manifest.parentFile.mkdirs()
        manifest.writeText(
            """
            {
              "schemaVersion": 1,
              "variant": "fdroidRelease",
              "nativeSourceRefs": "${jsonEscape(sourceRefsValue)}",
              "androidBuildImage": "${jsonEscape(androidImage.get())}",
              "nativeBuildImage": "${jsonEscape(nativeImage.get())}",
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

val stageFdroidReleaseForFdroidServer by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/fdroidRelease")) {
        include("*-unsigned.apk")
    }
    into(layout.buildDirectory.dir("outputs/apk/release"))
}

tasks.matching { it.name == "assembleFdroidRelease" }.configureEach {
    finalizedBy(stageFdroidReleaseForFdroidServer)
}

tasks.matching { it.name.startsWith("upload") && it.name.endsWith("BuildToMeasure") }.configureEach {
    enabled = measureBuildUploadConfigured.get()
}
