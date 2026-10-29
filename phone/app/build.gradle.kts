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
  // Export Room schemas for migration verification
  ksp {
   arg("room.schemaLocation", "$projectDir/schemas")
   arg("room.incremental", "true")
  }
 }
 buildFeatures { compose = true }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 sourceSets {
  getByName("debug") {
   java.srcDirs("src/debug/java")
  }
  getByName("test") {
   resources.srcDirs("src/test/resources")
   resources.srcDir(layout.buildDirectory.dir("generated/wire-resources"))
  }
  getByName("main") { assets.srcDir(layout.buildDirectory.dir("generated/wire-assets")) }
 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val copySyncTestVectors = tasks.register<Copy>("copySyncTestVectors") {
 from("${project.rootDir}/../contracts/sync/test-vectors.json")
 into(file("src/test/resources"))
}
val copyWireSchemas = tasks.register<Copy>("copyWireSchemas") {
 from("${project.rootDir}/../contracts/sync/schemas") { into("wire") }
 from("${project.rootDir}/../core-spec") {
  include("event.schema.json", "context-snapshot.schema.json", "forbidden-fields.json")
  into("wire")
 }
 into(layout.buildDirectory.dir("generated/wire-assets"))
 doLast {
  copy {
   from(layout.buildDirectory.dir("generated/wire-assets"))
   into(layout.buildDirectory.dir("generated/wire-resources"))
  }
 }
}
tasks.named("preBuild") { dependsOn(copyWireSchemas) }
val copyWireFixtures = tasks.register<Copy>("copyWireFixtures") {
 from("${project.rootDir}/../core-spec/fixtures") { into("core") }
 from("${project.rootDir}/../contracts/sync/fixtures") { into("sync") }
 into(layout.buildDirectory.dir("generated/wire-resources/fixtures"))
}
tasks.matching { it.name.contains("UnitTest", ignoreCase = true) }.configureEach {
 dependsOn(copySyncTestVectors)
 dependsOn(copyWireSchemas, copyWireFixtures)
}

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
