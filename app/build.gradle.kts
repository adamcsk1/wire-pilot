import org.gradle.api.GradleException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("com.android.application")
  jacoco
}

val versionCodeTimestamp: Int = ZonedDateTime.now(ZoneOffset.UTC)
  .format(DateTimeFormatter.ofPattern("yyMMddHH")).toInt()

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = releaseKeystorePath.isPresent &&
  releaseKeystorePassword.isPresent &&
  releaseKeyAlias.isPresent &&
  releaseKeyPassword.isPresent

base {
  archivesName.set("wire-pilot")
}

android {
  namespace = "com.wirepilot.app"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.wirepilot.app"
    minSdk = 35
    targetSdk = 37
    versionCode = versionCodeTimestamp
    versionName = "0.0.1"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseKeystorePath.get())
        storePassword = releaseKeystorePassword.get()
        keyAlias = releaseKeyAlias.get()
        keyPassword = releaseKeyPassword.get()
      }
    }
  }

  buildTypes {
    debug {
      enableUnitTestCoverage = true
    }
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

jacoco {
  toolVersion = "0.8.13"
}

val jacocoCoverageIncludes = listOf(
  "**/com/wirepilot/app/control/*.class",
  "**/com/wirepilot/app/control/**/*.class",
  "**/com/wirepilot/app/data/*.class",
  "**/com/wirepilot/app/data/**/*.class",
)

val jacocoCoverageExcludes = listOf(
  "**/R.class",
  "**/R$*.class",
  "**/BuildConfig.*",
  "**/Manifest*.*",
)

tasks.register<JacocoReport>("jacocoTestReport") {
  group = "verification"
  description = "Generate JaCoCo coverage for control and data packages"
  dependsOn("testDebugUnitTest")

  reports {
    xml.required.set(true)
    html.required.set(true)
  }

  val kotlinClasses = coverageClassTree()
  classDirectories.setFrom(files(kotlinClasses))
  sourceDirectories.setFrom(files("src/main/kotlin"))
  executionData.setFrom(
    fileTree(layout.buildDirectory) {
      include(
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        "jacoco/testDebugUnitTest.exec",
      )
    },
  )
  doFirst {
    if (kotlinClasses.isEmpty) {
      throw GradleException("JaCoCo class directories are empty; coverage path is wrong")
    }
  }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  group = "verification"
  description = "Fail if control/data instruction coverage is below 95%"
  dependsOn("jacocoTestReport")

  val kotlinClasses = coverageClassTree()

  classDirectories.setFrom(files(kotlinClasses))
  doFirst {
    if (kotlinClasses.isEmpty) {
      throw GradleException("JaCoCo class directories are empty; coverage path is wrong")
    }
  }
  sourceDirectories.setFrom(files("src/main/kotlin"))
  executionData.setFrom(
    fileTree(layout.buildDirectory) {
      include(
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        "jacoco/testDebugUnitTest.exec",
      )
    },
  )

  violationRules {
    rule {
      limit {
        counter = "INSTRUCTION"
        value = "COVEREDRATIO"
        minimum = "0.95".toBigDecimal()
      }
    }
  }
}

tasks.named("check") {
  dependsOn("jacocoTestCoverageVerification")
}

fun coverageClassTree(): ConfigurableFileTree {
  val classesDir = layout.buildDirectory
    .get()
    .dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
    .asFile
  return fileTree(classesDir) {
    include(jacocoCoverageIncludes)
    exclude(jacocoCoverageExcludes)
  }
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  implementation("androidx.core:core-ktx:1.19.0")
  implementation("androidx.appcompat:appcompat:1.8.0")
  implementation("com.google.android.material:material:1.14.0")
  implementation("androidx.recyclerview:recyclerview:1.4.0")
  implementation("com.wireguard.android:tunnel:1.0.20260102")
  implementation("androidx.security:security-crypto:1.1.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.21")
}
