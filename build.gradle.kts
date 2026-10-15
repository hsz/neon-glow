import org.jetbrains.changelog.markdownToHTML
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

    patchPluginXml {
        // The IntelliJ Platform Gradle Plugin already renders the current version section of CHANGELOG.md into the change notes
        // (and wires `pluginRepositoryUrl` into the changelog). The Marketplace "What's New" is read at update time, when the
        // plugin has just delivered something new, so only a one-line sponsorship nudge is prepended to that default.
        val nudge = markdownToHTML(
            "_Neon Glow is free and sponsor-supported. If it brightens your day, consider " +
                "[♥ sponsoring it on GitHub](https://github.com/sponsors/hsz)._"
        )
        changeNotes = intellijPlatform.pluginConfiguration.changeNotes.map { nudge + it }
    }
}

providers.gradleProperty("testIdePath").orNull?.let { path ->
    intellijPlatformTesting.testIde.register("testCurrentIde") {
        localPath = file(path)
        val frameworkVersion = providers.gradleProperty("testFrameworkVersion").orNull
        if (frameworkVersion == null) testFramework(TestFrameworkType.Platform)
        else testFramework(TestFrameworkType.Platform, frameworkVersion)
        plugins {
            bundledPlugin("JUnit")
        }
    }
}