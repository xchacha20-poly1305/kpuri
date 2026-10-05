import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }
    // Browser tests need a local Chrome; the same code runs under the Node.js tests.
    js {
        nodejs()
        browser { testTask { enabled = false } }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser { testTask { enabled = false } }
    }
    linuxX64()
    linuxArm64()
    mingwX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = "kpuri"
        description = "Kotlin multiplatform URI library."
        inceptionYear = "2026"
        url = "https://github.com/xchacha20-poly1305/kpuri"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "xchacha20-poly1305"
                url = "https://github.com/xchacha20-poly1305"
            }
        }
        scm {
            url = "https://github.com/xchacha20-poly1305/kpuri"
            connection = "scm:git:https://github.com/xchacha20-poly1305/kpuri.git"
            developerConnection = "scm:git:ssh://git@github.com/xchacha20-poly1305/kpuri.git"
        }
    }
}
