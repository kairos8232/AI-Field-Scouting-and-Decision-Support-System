import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

fun loadPropertiesFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val props = Properties()
    file.inputStream().use { props.load(it) }
    return props.entries.associate { (k, v) -> k.toString().trim() to v.toString().trim() }
}

fun loadDotEnvFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val result = mutableMapOf<String, String>()
    file.forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine

        val normalized = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
        val sep = normalized.indexOf('=')
        if (sep <= 0) return@forEachLine

        val key = normalized.substring(0, sep).trim()
        var value = normalized.substring(sep + 1).trim()
        if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
            value = value.substring(1, value.length - 1)
        }
        result[key] = value
    }
    return result
}

val moduleDotEnv = loadDotEnvFile(project.file(".env"))
val rootDotEnv = loadDotEnvFile(rootProject.file(".env"))
val moduleLocalProperties = loadPropertiesFile(project.file("local.properties"))
val rootLocalProperties = loadPropertiesFile(rootProject.file("local.properties"))

fun env(name: String): String = configValue(name)
fun envOrDefault(name: String, fallback: String): String = configValue(name).ifBlank { fallback }
fun asBuildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
fun configValue(name: String): String =
    (
        (project.findProperty(name) as String?)
            ?: moduleLocalProperties[name]
            ?: rootLocalProperties[name]
            ?: moduleDotEnv[name]
            ?: rootDotEnv[name]
            ?: System.getenv(name)
            ?: ""
        ).trim()

fun firstConfigured(vararg names: String): String =
    names.asSequence().map(::configValue).firstOrNull { it.isNotBlank() }.orEmpty()

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.alleyz15.farmtwinai.composeapp")
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.alleyz15.farmtwinai"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.alleyz15.farmtwinai"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        val mapsApiKey = firstConfigured(
            "GOOGLE_MAPS_API_KEY",
            "MAPS_API_KEY",
        )
        val googleOAuthClientId = firstConfigured(
            "GOOGLE_OAUTH_CLIENT_ID",
            "GOOGLE_WEB_CLIENT_ID",
        )
        val googleOAuthRedirectUri = envOrDefault(
            "GOOGLE_OAUTH_REDIRECT_URI",
            "farmtwinai://oauth2redirect/google",
        )
        if (mapsApiKey.isBlank()) {
            logger.warn("GOOGLE_MAPS_API_KEY is blank. Checked project/root local.properties and .env files.")
        }
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = mapsApiKey
        manifestPlaceholders["GOOGLE_OAUTH_REDIRECT_URI"] = googleOAuthRedirectUri
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", asBuildConfigString(mapsApiKey))
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", asBuildConfigString(googleOAuthClientId))
        buildConfigField("String", "GOOGLE_OAUTH_REDIRECT_URI", asBuildConfigString(googleOAuthRedirectUri))
        buildConfigField(
            "String",
            "FIELD_INSIGHTS_BASE_URL",
            asBuildConfigString(envOrDefault("FIELD_INSIGHTS_BASE_URL", "")),
        )
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

// Compatibility shim for IDEs/plugins that still request this legacy sync task.
tasks.register("prepareKotlinBuildScriptModel")

