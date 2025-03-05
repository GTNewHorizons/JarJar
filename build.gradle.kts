
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

