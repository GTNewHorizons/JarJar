package com.mitchej123.jarjar.fml.common;

import java.io.File;

/*
 * This class is used to determine if a library is a default library to speed up the classloading process.
 *  Taken from Forge 1.12.2-14.23.5.2847 and CoreTweaks
 */
public class DefaultLibraries {
    final static String[] prefixes =
        {
            "launchwrapper-",
            "asm-all-",
            "akka-actor_2.11-",
            "config-",
            "scala-",
            "jopt-simple-",
            "lzma-",
            "realms-",
            "httpclient-",
            "httpcore-",
            "vecmath-",
            "trove4j-",
            "icu4j-core-mojang-",
            "codecjorbis-",
            "codecwav-",
            "libraryjavawound-",
            "librarylwjglopenal-",
            "soundsystem-",
            "netty-all-",
            "guava-",
            "commons-lang3-",
            "commons-compress-",
            "commons-logging-",
            "commons-io-",
            "commons-codec-",
            "jinput-",
            "jutils-",
            "gson-",
            "authlib-",
            "log4j-api-",
            "log4j-core-",
            "lwjgl-",
            "lwjgl_util-",
            "twitch-",
            "jline-",
            "jna-",
            "platform-",
            "oshi-core-",
            "netty-",
            "libraryjavasound-",
            "fastutil-",
            "lombok-"
        };

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public static boolean isDefaultLibrary(File file) {
        final String home = System.getProperty("java.home"); // Nullcheck just in case some JVM decides to be stupid
        if (home != null && file.getAbsolutePath().startsWith(home)) return true;
        final String name = file.getName();
        if (!name.endsWith(".jar")) return false;

        for(int i = 0; i < prefixes.length; i++) {
            if (name.startsWith(prefixes[i])) return true;
        }

        return false;
    }

}
