rootProject.name = "CloudstreamPlugins"

// This file sets what projects are included. All new projects should get automatically included unless specified in "disabled" variable.

val buildAll = providers.gradleProperty("buildAll").getOrElse("false").toBoolean()

val disabled = listOf("__Temel", "ExampleProvider")

File(rootDir, ".").eachDir { dir ->
    val buildFile = File(dir, "build.gradle.kts")
    if (!disabled.contains(dir.name) && buildFile.exists()) {
        val content = buildFile.readText()
        val hasStatusZero = content.contains(Regex("""status\s*=\s*0"""))

        if (buildAll || !hasStatusZero) {
            include(dir.name)
        } else {
            println("Skipping disabled extension: ${dir.name} (Use -PbuildAll=true to include)")
        }
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}


// To only include a single project, comment out the previous lines (except the first one), and include your plugin like so:
// include("PluginName")