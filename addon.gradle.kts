import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// Configuration for RFB bundle (Java 8 relaunch)
val rfbBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val rfbVersion: String by project

dependencies {
    rfbBundle("com.gtnewhorizons.retrofuturabootstrap:RetroFuturaBootstrap:$rfbVersion")
}

// Directory for compiled wrapper classes
val rfbWrapperClassesDir = layout.buildDirectory.dir("rfb-wrapper-classes")

// Task to compile the JarJarMain wrapper class
val compileRfbWrapper by tasks.registering(JavaCompile::class) {
    source = fileTree("src/rfb-wrapper/java")
    classpath = rfbBundle
    destinationDirectory.set(rfbWrapperClassesDir)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

// Task to create RFB fat jar for Java 8 relaunch
val rfbBundleJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Creates a fat jar containing RFB and its dependencies for Java 8 relaunch"
    archiveClassifier.set("rfb-bundle")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false

    // Include all rfbBundle dependencies as a fat jar
    rfbBundle.resolve().forEach { dep ->
        from(zipTree(dep)) {
            // Rename META-INF files to avoid conflicts
            filesMatching("META-INF/*") {
                if (name != "MANIFEST.MF" && !path.startsWith("META-INF/versions") && !path.startsWith("META-INF/services")) {
                    name = "${dep.name}-${name}"
                }
            }
            // Exclude module-info
            exclude("module-info.class")
            exclude("META-INF/versions/*/module-info.class")
        }
    }

    // Include compiled wrapper classes (JarJarMain)
    dependsOn(compileRfbWrapper)
    from(rfbWrapperClassesDir)

    manifest {
        attributes(
            "Multi-Release" to "true",
            // Use JarJarMain wrapper which pre-loads Log4j to prevent StackOverflowError on Linux
            "Main-Class" to "com.mitchej123.jarjar.rfb.JarJarMain"
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    dependsOn(rfbBundleJar)

    doLast {
        val bundleJarFile = rfbBundleJar.get().archiveFile.get().asFile
        ant.withGroovyBuilder {
            "jar"("destfile" to archiveFile.get().asFile, "update" to true) {
                "zipfileset"("file" to bundleJarFile, "fullpath" to "com/mitchej123/jarjar/launch/rfb-bundle.jar")
            }
        }
    }
}
