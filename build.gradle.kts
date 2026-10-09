// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.spotless)
}

spotless {
    java {
        target("app/src/**/*.java")
        googleJavaFormat(libs.versions.googleJavaFormat.get()).aosp().reorderImports(true)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target(".gitignore", "*.md", ".github/**/*.yml", ".githooks/*", "scripts/*", "config/**/*.xml", "docs/*.css")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
