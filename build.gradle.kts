import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion").orElse("2025.3.6.1"))
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create("IU", "2025.3.6.1")
            create("IU", "2026.1")
            create("IU", "2026.1.5")
            create("IU", "2026.2.3")
        }
    }
}

tasks {
    processResources {
        from("LICENSE.upstream") {
            into("META-INF")
            rename { "LICENSE.synthwave84" }
        }
    }

    runIde {
        // Periodic paint-cost / atlas statistics in the sandbox idea.log (see GlowStats).
        jvmArgs("-Dide.neon.glow.debug=true")
    }
}
