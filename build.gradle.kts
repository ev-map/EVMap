// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    val kotlin_version by extra("1.9.0")
    val about_libs_version by extra("8.9.4")
    val nav_version by extra("2.7.2")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:$about_libs_version")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:$nav_version")
        classpath("pt.jcosta.resourceplaceholders:plugin:0.7")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        //noinspection JcenterRepositoryObsolete
        maven { setUrl("https://jitpack.io") }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
