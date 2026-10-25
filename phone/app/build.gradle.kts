plugins {
 alias(libs.plugins.android.application)
 alias(libs.plugins.kotlin.compose)
 alias(libs.plugins.ksp)
}
android {
 namespace = "com.dhyan.app"
 compileSdk { version = release(37) { minorApiLevel = 0 } }
 defaultConfig {
  applicationId = "com.dhyan.app"
  minSdk = 26
  targetSdk = 37
  versionCode = 1
  versionName = "0.1.0"
 }
 buildFeatures { compose = true }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation(platform(libs.compose.bom))
 implementation(libs.compose.ui)
 implementation(libs.compose.material3)
 implementation(libs.activity.compose)
 implementation(libs.room.runtime)
 implementation(libs.room.ktx)
 ksp(libs.room.compiler)
 implementation(libs.work.runtime)
 implementation(libs.coroutines.android)
 implementation(libs.okhttp)
 implementation(libs.koin.android)
 implementation(libs.koin.compose)
 testImplementation(libs.junit)
 testImplementation(libs.coroutines.core)
 testImplementation(libs.coroutines.test)
}
