import com.android.build.api.dsl.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.1.1")
        classpath("com.github.recloudstream:gradle:81b1d424d2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: LibraryExtension.() -> Unit) =
    extensions.getByName<LibraryExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions",
                "-Xannotation-default-target=param-property"
            )
        }
    }

    cloudstream {
        setRepo(
            System.getenv("GITHUB_REPOSITORY")
                ?: "https://github.com/Kraptor123/Cs-GizliKeyif"
        )

        authors = listOf("kraptor")
    }

    android {
        namespace = "com.kraptor.${
            project.name.lowercase()
                .replace("-", "_")
                .let {
                    if (it.firstOrNull()?.isDigit() == true) "p$it" else it
                }
        }"

        compileSdk = 36

        defaultConfig {
            minSdk = 21
        }

        lint {
            targetSdk = 36
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        cloudstream("com.lagradost:cloudstream3:pre-release")

        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.13")
        implementation("org.jsoup:jsoup:1.22.1")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.5")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.13.5")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        implementation("org.mozilla:rhino:1.9.0")
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
        implementation("com.google.code.gson:gson:2.13.2")
        implementation("app.cash.quickjs:quickjs-android:0.9.2")
        implementation("com.github.vidstige:jadb:v1.2.1")
    }
}

tasks.register("derle") {
    group = "help"

    doLast {
        println("Filtreleme modu aktif: status=1 olanlar disindaki eklentiler derleme disi birakildi.")
    }
}

gradle.taskGraph.whenReady {
    if (hasTask(":derle")) {
        allTasks.forEach { task ->
            if (task.project != rootProject) {
                val csExt = task.project.extensions.findByType<CloudstreamExtension>()

                if (csExt != null && csExt.status != 1) {
                    task.enabled = false
                }
            }
        }
    }
}
