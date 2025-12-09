plugins {
    id("com.gtnewhorizons.gtnhconvention")
}
minecraft {
//    extraRunJvmArguments.add("-verbose:class")
//    extraRunJvmArguments.add("-Xlog:class+init,class+load")
//    extraRunJvmArguments.addAll(
//        "-Dlegacy.debugClassLoading=true",
//        "-Dlegacy.debugClassLoadingFiner=true",
//        "-Dlegacy.debugClassLoadingSave=true"
//    )
}
tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("META-INF/rfb-plugin/*") {
        expand("version" to project.version.toString())
    }
}

// Configure manifest for CLI and tweaker support
tasks.jar {
    manifest {
        attributes(
            // CLI entry point (java -jar jarjar.jar --help)
            "Main-Class" to "com.mitchej123.jarjar.launch.cli.JarJarCli",

            // LaunchWrapper tweaker
            "TweakClass" to "com.mitchej123.jarjar.launch.JarJarTweaker",
            "TweakOrder" to "-10000",

            // Force mod discovery even though we're a tweaker
            "ForceLoadAsMod" to "true"
        )
    }
}

