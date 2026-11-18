plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin: shared brain used by both Android apps. No Android APIs here.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
