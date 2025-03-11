/*
 * Contains code adapted from FML under the LGPLv2.1 license.
 */

package com.mitchej123.jarjar.fml.relauncher;

import com.google.common.base.Throwables;
import com.google.common.collect.ObjectArrays;
import com.mitchej123.jarjar.discovery.ModCandidateV2Sorter;
import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import com.mitchej123.jarjar.util.JarUtil;
import cpw.mods.fml.common.asm.transformers.ModAccessTransformer;
import cpw.mods.fml.common.discovery.ContainerType;
import cpw.mods.fml.common.launcher.FMLTweaker;
import cpw.mods.fml.relauncher.CoreModManager;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.FileListHelper;
import cpw.mods.fml.relauncher.ModListHelper;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@SuppressWarnings({ "unused"})
/*
 * CoreModManagerV2 - Replacement for cpw.mods.fml.relauncher.CoreModManager
 *  Essentially an overwrite, but done via subclassing because more than several mods use reflection to access the original class.
 *  Includes funciontality for:
 *   - Jar in Jar identification, extraction, and loading
 *   - Handles mods that have tweakers, coremods, and mods (otherwise handled via SpongeMixins)
 *   - Version solving in the case of conflicting tweakers/coremods/mods
 */
public final class CoreModManagerV2 extends CoreModManager {
    public static final String NESTED_DIR = "nestedmods";
    public static final Attributes.Name FORCELOADASMOD = new Attributes.Name("ForceLoadAsMod");
    public static final String DISABLED_FILES = "jarjar.disabledFiles";
    public static final Set<String> loadedByCommandLine = new HashSet<>();

    public static final Comparator<ModCandidateV2> COREMOD_COMPARATOR = Comparator.nullsFirst(
        Comparator.comparing(ModCandidateV2::getSortOrder)
            .thenComparing(ModCandidateV2::getFilename)
            .thenComparing(ModCandidateV2::getVersion)
            .thenComparing(ModCandidateV2::getNestLevel));


    private static File modDir;
    private static File nestedDir;

    private final static List<ModCandidateV2> modCandidates = new ArrayList<>();

    public static void handleLaunch(File mcDir, LaunchClassLoader classLoader, FMLTweaker tweaker) {
        CoreModManager.mcDir = mcDir;
        CoreModManager.tweaker = tweaker;

        modDir = setupCoreModDir(mcDir);
        nestedDir = setupNestedModDir(mcDir);

        try {
            // Are we in a 'decompiled' environment?
            if (classLoader.getClassBytes("net.minecraft.world.World") != null) {
                FMLRelaunchLog.info("Managed to load a deobfuscated Minecraft name- we are in a deobfuscated environment. Skipping runtime deobfuscation");
                CoreModManager.deobfuscatedEnvironment = true;
            }
        } catch (IOException ignored) {
        }

        if (!CoreModManager.deobfuscatedEnvironment) {
            FMLRelaunchLog.fine("Enabling runtime deobfuscation");
        }

        loadForgeCoreMods(classLoader, tweaker);

        // Now that we have the root plugins loaded - lets see what else might be around
        loadCommandLineCoremods(mcDir, classLoader);

        discoverCoreMods(mcDir, classLoader);
        loadTweakersAndCoreMods(mcDir, classLoader);
    }

    private static void loadForgeCoreMods(LaunchClassLoader classLoader, FMLTweaker tweaker) {
        tweaker.injectCascadingTweak("cpw.mods.fml.common.launcher.FMLInjectionAndSortingTweaker");

        try {
            classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.PatchingTransformer");
        } catch (Exception e) {
            FMLRelaunchLog.log(Level.ERROR, e, "The patch transformer failed to load! This is critical, loading cannot continue!");
            throw Throwables.propagate(e);
        }

        try {
            Field field = CoreModManager.class.getDeclaredField("loadPlugins");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        CoreModManager.loadPlugins = new ArrayList<>();

        try {
            Field field = CoreModManagerV2.class.getSuperclass().getDeclaredField("rootPlugins");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        for (String rootPluginName : CoreModManager.rootPlugins) {
            loadCoreMod(classLoader, rootPluginName, new File(FMLTweaker.getJarLocation()));
        }

        if (CoreModManager.loadPlugins.isEmpty()) {
            throw new RuntimeException("A fatal error has occured - no valid fml load plugin was found - this is a completely corrupt FML installation.");
        }

        FMLRelaunchLog.fine("All fundamental core mods are successfully located");
    }

    private static void loadCommandLineCoremods(File mcDir, LaunchClassLoader classLoader) {
        final String commandLineCoremods = System.getProperty("fml.coreMods.load", "");
        for (String coreModClassName : commandLineCoremods.split(",")) {
            if (coreModClassName.isEmpty()) {
                continue;
            }
            FMLRelaunchLog.info("Found a command line coremod : %s", coreModClassName);
            final CoreModManager.FMLPluginWrapper wrap = loadCoreMod(classLoader, coreModClassName, null);
            if (wrap != null) {
                loadedByCommandLine.add(coreModClassName);
                final URL coreModLocation = wrap.coreModInstance.getClass().getProtectionDomain().getCodeSource().getLocation();
                final String jarPath = coreModLocation.getPath();

                if (jarPath.endsWith(".jar")) {
                    try (final JarFile jar = new JarFile(jarPath)) {
                        final File coreModFile = new File(jarPath);
                        final ModCandidateV2 modCandidate = new ModCandidateV2(coreModFile, coreModFile, ContainerType.JAR, false, true);
                        JarUtil.checkNestedMods(jar, modCandidate, true);
                    } catch (IOException e) {
                        FMLRelaunchLog.log(Level.ERROR, e, "Unable to read the jar file %s - ignoring", jarPath);
                    }
                }
            }
        }
    }

    private static void checkDerps(File coreMods, File mcDir, LaunchClassLoader classLoader) {
        final FilenameFilter derpfilter = (dir, name) -> name.endsWith(".jar.zip");
        final File[] derplist = coreMods.listFiles(derpfilter);
        if (derplist != null && derplist.length > 0) {
            FMLRelaunchLog.severe("""
                FML has detected several badly downloaded jar files,  which have been named as zip files. \
                You probably need to download them again, or they may not work properly""");
            for (File f : derplist) {
                FMLRelaunchLog.severe("Problem file : %s", f.getName());
            }
        }
        final FileFilter derpdirfilter = pathname -> pathname.isDirectory() && new File(pathname, "META-INF").isDirectory();
        final File[] derpdirlist = coreMods.listFiles(derpdirfilter);
        if (derpdirlist != null && derpdirlist.length > 0) {
            FMLRelaunchLog.log(
                Level.FATAL, """
                    There appear to be jars extracted into the mods directory. This is VERY BAD and will almost NEVER WORK WELL.
                    You should place original jars only in the mods directory. NEVER extract them to the mods directory.
                    The directories below appear to be extracted jar files. Fix this before you continue.""");

            for (File f : derpdirlist) {
                FMLRelaunchLog.log(Level.FATAL, "Directory {} contains {}", f.getName(), Arrays.asList(new File(f, "META-INF").list()));
            }

            RuntimeException re = new RuntimeException("Extracted mod jars found, loading will NOT continue");
            // We're generating a crash report for the launcher to show to the user here
            try {
                Class<?> crashreportclass = classLoader.loadClass("b");
                Object crashreport = crashreportclass.getMethod("a", Throwable.class, String.class).invoke(
                    null, re, """
                        FML has discovered extracted jar files in the mods directory.
                        This breaks mod loading functionality completely.
                        Remove the directories and replace with the jar files originally provided.""");
                File crashreportfile = new File(
                    new File(coreMods.getParentFile(), "crash-reports"),
                    String.format("fml-crash-%1$tY-%1$tm-%1$td_%1$tT.txt", Calendar.getInstance()));
                crashreportclass.getMethod("a", File.class).invoke(crashreport, crashreportfile);
                System.out.println("#@!@# FML has crashed the game deliberately. Crash report saved to: #@!@# " + crashreportfile.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                // NOOP - hopefully
            }
            throw re;
        }

    }

    private static File setupNestedModDir(File mcDir) {
        File nestedModDir = new File(mcDir, NESTED_DIR);
        try {
            nestedModDir = nestedModDir.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to canonicalize the nested jar dir at %s", mcDir.getName()), e);
        }

        if (!nestedModDir.exists()) {
            nestedModDir.mkdir();
        } else if (!nestedModDir.isDirectory()) {
            throw new RuntimeException(String.format("Found a nested jar file in %s that's not a directory", mcDir.getName()));
        }
        return nestedModDir;
    }

    public static void discoverCoreMods(File mcDir, LaunchClassLoader classLoader) {
        ModListHelper.parseModList(mcDir);
        FMLRelaunchLog.fine("Discovering coremods");

        final FilenameFilter ff = (dir, name) -> name.endsWith(".jar");
        checkDerps(modDir, mcDir, classLoader);

        File[] coreModList = Objects.requireNonNull(modDir.listFiles(ff));
        File versionedModDir = new File(modDir, FMLInjectionData.mccversion);
        if (versionedModDir.isDirectory()) {
            File[] versionedCoreMods = Objects.requireNonNull(versionedModDir.listFiles(ff));
            coreModList = ObjectArrays.concat(coreModList, versionedCoreMods, File.class);
        }

        coreModList = ObjectArrays.concat(coreModList, ModListHelper.additionalMods.values().toArray(new File[0]), File.class);

        FileListHelper.sortFileList(coreModList);

        final List<File> modFilesToExample = new ArrayList<>(Arrays.asList(coreModList));

        for (File modFile : modFilesToExample) {
            FMLRelaunchLog.fine("Examining for coremod candidacy %s", modFile.getName());
            final ModCandidateV2 modCandidate = JarUtil.examineModCandidate(modFile, null, true);
            if (modCandidate == null) continue;
            if(modCandidate.hasCoreMod() && loadedByCommandLine.contains(modCandidate.getCoreMod())) {
                FMLRelaunchLog.info("Skipping coremod previously loaded via command line %s", modCandidate.getCoreMod());
                continue;
            }

            modCandidates.add(modCandidate);
            if(modCandidate.hasNestedMods()) {
                modCandidates.addAll(modCandidate.getNestedModcandidates());
            }
        }
    }
    private static final Set<String> injectedURLS = new HashSet<>();

    private static void loadTweakersAndCoreMods(File mcDir, LaunchClassLoader classLoader) {
        FMLRelaunchLog.fine("Loading Tweakers and coremods");

        // TODO: This needs to include any core mods loaded from the command line in the deduplication process
        final ModCandidateV2Sorter<ModCandidateV2> candidateSorter = new ModCandidateV2Sorter<>(modCandidates, COREMOD_COMPARATOR);
        final Optional<List<ModCandidateV2>> resolvedCandidates = candidateSorter.resolve();
        Launch.blackboard.put(DISABLED_FILES, candidateSorter.getDisabledFiles());

        if (!resolvedCandidates.isPresent()) {
            FMLRelaunchLog.log(Level.ERROR, "There was a critical error during coremod resolution, check the log for details");
            return;
        }
        modCandidates.clear();
        modCandidates.addAll(resolvedCandidates.get());

        boolean injectMixins = false;

        for (ModCandidateV2 candidate : modCandidates) {
            final File modFile = candidate.getModContainer();
            FMLRelaunchLog.info("Examining %s to load", modFile.getName());
            if (candidate.hasAccessTransformers()) {
                addModAccessTransformers(candidate.getAccessTransformers());
            }
            if (candidate.hasTweaker()) {
                FMLRelaunchLog.info("Tweaker is %s", candidate.getTweaker());
                handleCascadingTweak(modFile, null, candidate.getTweaker(), classLoader, candidate.getSortOrder());
                if ("org.spongepowered.asm.launch.MixinTweaker".equals(candidate.getTweaker())) {
                    injectMixins = true;
                    try {
                        if(injectedURLS.add(modFile.getName())) {
                            // Have to manually stuff the tweaker into the parent classloader
                            getADDURL().invoke(classLoader.getClass().getClassLoader(), modFile.toURI().toURL());
                        }
                    } catch (IllegalAccessException | InvocationTargetException | MalformedURLException | NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        classLoader.addURL(modFile.toURI().toURL());
                    } catch (MalformedURLException e) {
                        FMLRelaunchLog.log(Level.ERROR, e, "Unable to convert file into a URL. weird");
                    }
                }

                if (!candidate.containsMod()) {
                    loadedCoremods.add(modFile.getName());
                }
            }
            if (candidate.hasCoreMod()) {
                FMLRelaunchLog.info("Coremod is %s", candidate.getCoreMod());
                // Support things that are mod jars, but not FML mod jars
                try {
                    classLoader.addURL(modFile.toURI().toURL());
                } catch (MalformedURLException e) {
                    FMLRelaunchLog.log(Level.ERROR, e, "Unable to convert file into a URL. weird");
                }
                if (!candidate.containsMod()) {
                    FMLRelaunchLog.finer("Adding %s to the list of known coremods, it will not be examined again", modFile.getName());
                    loadedCoremods.add(modFile.getName());
                } else {
                    FMLRelaunchLog.finer(
                        "Found FMLCorePluginContainsFMLMod marker in %s, it will be examined later for regular @Mod instances",
                        modFile.getName());
                    reparsedCoremods.add(modFile.getName());
                }
                loadCoreMod(classLoader, candidate.getCoreMod(), modFile);
            }
        }
        if(injectMixins) {
            injectMixinTweaker(classLoader, mcDir, FMLInjectionData.mccversion);
        }
        modCandidates.clear();
    }

    public static boolean mixinInit = false;

    private static void injectMixinTweaker(LaunchClassLoader classLoader, File mcDir, String mccversion) {
        if (mixinInit) return;
        mixinInit = true;
        FMLRelaunchLog.info("Injecting Mixin Tweaker!");
        try {
            classLoader.addClassLoaderExclusion("org.spongepowered.asm.launch");
            Class<?> tweakerClass = Class.forName("org.spongepowered.asm.launch.MixinTweaker", true, classLoader);
            ITweaker tweaker = (ITweaker) tweakerClass.getConstructor().newInstance();
            tweaker.acceptOptions(new ArrayList<>(), mcDir, null, mccversion);
            tweaker.injectIntoClassLoader(classLoader);
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

    }

    private static Field embedded = null;

    @SuppressWarnings("unchecked")
    private static void addModAccessTransformers(Map<String, String> atMap) {
        if (embedded == null) {
            try {
                embedded = ModAccessTransformer.class.getDeclaredField("embedded");
                embedded.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            Map<String, String> map = (Map<String, String>) embedded.get(null);
            map.putAll(atMap);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Method getADDURL() throws NoSuchMethodException {
        if (ADDURL == null) {
            ADDURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            ADDURL.setAccessible(true);
        }

        return ADDURL;

    }

    public static void handleCascadingTweak(File coreMod, JarFile jar, String cascadedTweaker, LaunchClassLoader classLoader, Integer sortingOrder) {
        try {
            // Have to manually stuff the tweaker into the parent classloader
            getADDURL().invoke(classLoader.getClass().getClassLoader(), coreMod.toURI().toURL());
            classLoader.addURL(coreMod.toURI().toURL());
            CoreModManager.tweaker.injectCascadingTweak(cascadedTweaker);
            tweakSorting.put(cascadedTweaker, sortingOrder);
        } catch (Exception e) {
            FMLRelaunchLog.log(Level.INFO, e, "There was a problem trying to load the mod dir tweaker %s", coreMod.getAbsolutePath());
        }
    }

    private static boolean isValidNestedJarEntry(ZipEntry entry) {
        return entry != null && !entry.isDirectory() && entry.getName().endsWith(".jar");
    }

    public static File getModDir() {
        return modDir;
    }

    public static File getNestedDir() {
        if(nestedDir == null) {
            nestedDir = setupNestedModDir(mcDir);
        }
        return nestedDir;
    }

}
