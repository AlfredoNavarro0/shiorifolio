import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compose.material3)
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "dev.meridian.folio.desktop.MainKt"
        nativeDistributions {
            modules("java.sql", "java.net.http", "java.management", "java.naming", "jdk.crypto.ec", "jdk.unsupported")
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "ShioriFolio"
            packageVersion = "1.0.0"
            description = "ShioriFolio (栞) — Engraved PDF & EPUB Editor and Reader"
            windows {
                shortcut = true
                menu = true
                menuGroup = "Meridian Works"
                upgradeUuid = "8A4412B3-91D2-4E7A-A14F-7C389102E5B1"
            }
            linux {
                shortcut = true
                menuGroup = "Office;Viewer;Literature"
            }
        }
    }
}
