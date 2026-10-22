import java.net.URI

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val relayOrigin = providers.gradleProperty("relayUrl")
    .orElse(providers.environmentVariable("TVHOME_RELAY_URL"))
    .orElse(providers.environmentVariable("EXPO_PUBLIC_RELAY_URL"))
    .orElse("").get().trim().trimEnd('/')
if (relayOrigin.isNotBlank()) {
    val address = URI(relayOrigin)
    require(address.scheme == "https" && !address.host.isNullOrBlank() && address.userInfo == null && address.rawQuery == null && address.rawFragment == null && address.rawPath.isNullOrBlank()) { "Set an HTTPS relay origin without a page path or credentials." }
}

android {
    namespace = "app.tvhome.launcher"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.tvhome.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-preview"
        buildConfigField("String", "RELAY_URL", "\"$relayOrigin\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.activity:activity:1.11.0")
}
