import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.spotbugs)
    checkstyle
    pmd
    jacoco
}

// Release signing secrets live in keystore.properties (git-ignored). See keystore.properties.example.
val keystoreProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

android {
    namespace = "com.jd_s4nd_b0x.CountMe"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "com.jd_s4nd_b0x.CountMe"
        minSdk = 30
        targetSdk = 36
        // Release builds pass the tag's version in (see .github/workflows/release.yml).
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // The legal pages are published from /docs and bundled so they also work offline.
    sourceSets {
        getByName("main") {
            assets.directories.add(rootProject.file("docs").path)
        }
    }

    buildTypes {
        debug {
            isDebuggable = false
            enableUnitTestCoverage = true
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkDependencies = true
        checkTestSources = true
        checkGeneratedSources = true
        checkReleaseBuilds = true
        explainIssues = true
        // Version-bump nags change with the calendar, not with our code; Dependabot owns those.
        disable +=
            setOf(
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                "OldTargetApi",
                // Library-API hygiene, meaningless for an app module (132 hits on every override).
                "UnknownNullness",
                "SyntheticAccessor",
                // Text here is labels, not content worth selecting; the source logo art is a PNG.
                "SelectableText",
                "ConvertToWebp",
            )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
            all {
                // Robolectric loads classes through its own classloader; without this the JaCoCo
                // agent sees no code and reports 0% coverage.
                it.extensions.configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }

    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "META-INF/*.version",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/*.kotlin_module",
                    "DebugProbesKt.bin",
                    "kotlin/**",
                    "kotlinx/**",
                    "org/**",
                    "com/google/**",
                    "androidx/appcompat/res/**",
                    "androidx/constraintlayout/**",
                    "com/google/android/material/**",
                ),
            )
        }
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime)
    // Error Prone runs as a javac plugin; see the JavaCompile configuration below.
    annotationProcessor(libs.errorprone.core)
    testAnnotationProcessor(libs.errorprone.core)
    androidTestAnnotationProcessor(libs.errorprone.core)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

// ---------------------------------------------------------------------------------------------
// Static analysis. Everything here fails the build; `./gradlew qualityCheck` runs the lot.
// ---------------------------------------------------------------------------------------------

// Every warning is an error: the build is warning-free by policy. Error Prone is attached as a javac
// plugin (the net.ltgt plugin does not hook into AGP's compile tasks).
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-processing",
            "-Xlint:-options",
            "-Xlint:-classfile",
            "-Xlint:-this-escape",
            "-Werror",
            "-XDcompilePolicy=simple",
            "-XDaddTypeAnnotationsToSymbol=true",
            "--should-stop=ifError=FLOW",
            "-Xplugin:ErrorProne -XepDisableWarningsInGeneratedCode -XepExcludedPaths:.*/build/generated/.* " +
                "-Xep:MissingOverride:ERROR -Xep:UnusedVariable:ERROR -Xep:UnusedMethod:ERROR " +
                "-Xep:FallThrough:ERROR -Xep:ReferenceEquality:ERROR",
        ),
    )
}

val javaSources = fileTree("src") { include("**/*.java") }

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isShowViolations = true
}

val checkstyleAll by tasks.registering(Checkstyle::class) {
    group = "verification"
    description = "Checkstyle over main and test sources."
    source = javaSources
    classpath = files()
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    ruleSets = emptyList()
    isConsoleOutput = true
    isIgnoreFailures = false
}

val pmdAll by tasks.registering(Pmd::class) {
    group = "verification"
    description = "PMD over production sources (tests are covered by Error Prone and Checkstyle)."
    source = fileTree("src/main") { include("**/*.java") }
    classpath = files()
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    ruleSets = emptyList()
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotbugs {
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.LOW)
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    showProgress.set(false)
    // SpotBugs exits non-zero for classes absent from Android's android.jar (java.rmi, ...). Those
    // are not our findings, so the exit code is ignored and spotbugsVerify gates on real bugs.
    ignoreFailures.set(true)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("xml") { required.set(true) }
    reports.create("html") { required.set(true) }
}

val spotbugsVerify by tasks.registering {
    group = "verification"
    description = "Fails when SpotBugs reported any bug instance."
    dependsOn("spotbugsDebug")
    val report = layout.buildDirectory.file("reports/spotbugs/debug.xml")
    doLast {
        val xml = report.get().asFile
        check(xml.exists()) { "SpotBugs XML report missing: $xml" }
        val bugs = Regex("<BugInstance ").findAll(xml.readText()).count()
        check(bugs == 0) { "SpotBugs found $bugs bug(s); see ${xml.parentFile}" }
    }
}

// SpotBugs needs the full runtime classpath to resolve classes our dependencies reference.
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    auxClassPaths.from(configurations.getByName("debugRuntimeClasspath"))
}

// ---------------------------------------------------------------------------------------------
// Coverage
// ---------------------------------------------------------------------------------------------

jacoco { toolVersion = libs.versions.jacoco.get() }

val coverageClasses =
    fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
        exclude(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.class",
            // Android UI is covered by lint, static analysis and on-device checks, not JVM tests.
            "**/ui/**",
            "**/adapter/**",
            // Bound to Play services / WorkManager runtime / real graphics; verified on a device.
            "**/drive/DriveAuth*.class",
            "**/drive/SyncWorker*.class",
            "**/export/PdfReport*.class",
            "**/CountMeApp.class",
        )
    }
val coverageExec =
    fileTree(layout.buildDirectory.dir("outputs/unit_test_code_coverage/debugUnitTest")) { include("*.exec") }

val jacocoDebugReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(coverageExec)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val jacocoDebugVerify by tasks.registering(JacocoCoverageVerification::class) {
    group = "verification"
    dependsOn(jacocoDebugReport)
    classDirectories.setFrom(coverageClasses)
    executionData.setFrom(coverageExec)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Every static check, the unit tests with coverage gate, lint, and both builds."
    dependsOn(
        ":spotlessCheck",
        checkstyleAll,
        pmdAll,
        spotbugsVerify,
        "lintDebug",
        "testDebugUnitTest",
        jacocoDebugVerify,
        "assembleDebug",
        "assembleRelease",
    )
}
