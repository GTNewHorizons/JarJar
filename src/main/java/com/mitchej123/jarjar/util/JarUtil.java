package com.mitchej123.jarjar.util;

import com.github.bsideup.jabel.Desugar;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.gtnewhorizons.retrofuturabootstrap.versioning.DefaultArtifactVersion;
import com.mitchej123.jarjar.Config;
import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import com.mitchej123.jarjar.fml.relauncher.CoreModManagerV2;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.discovery.ContainerType;
import cpw.mods.fml.relauncher.CoreModManager;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"UnstableApiUsage"})
public class JarUtil {
    public static final Logger logger = LogManager.getLogger("NestedJarUtil");
    private static final String versionPattern = ".*-([0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9]+)?(?:\\+[a-zA-Z0-9]+)?).*\\.jar";
    private static final Pattern pattern = Pattern.compile(versionPattern);

    private static final String HODGEPODGE_COREMOD = "com.mitchej123.hodgepodge.core.HodgepodgeCore";
    private static final DefaultArtifactVersion HODGEPODGE_LATE_TRANSFORMER_VERSION = new DefaultArtifactVersion("2.7.147");
    private static final int HODGEPODGE_SORT_ORDER = 1;

    @Desugar public record NestedJar(File file, String sourceFilename, String hash) {
    }

    public static @Nullable NestedJar getNestedJar(JarFile jar, String nestedJarPath, File targetDir) {
        final JarEntry nestedEntry = jar.getJarEntry(nestedJarPath);
        if (nestedEntry == null) {
            FMLRelaunchLog.log(Level.ERROR, "Unable to find nested jar %s in %s - ignoring", nestedJarPath, jar.getName());
            return null;
        }

        // getName() is the full path, we only want the filename
        final String nestedJarFilename = FilenameUtils.getName(nestedJarPath);
        final String nestedJarName = FilenameUtils.getBaseName(nestedJarPath);
        final File outputJarFile;
        final String hash;

        try (RewindableModInputStream is = new RewindableModInputStream(jar.getInputStream(nestedEntry))) {
            hash = DigestUtils.sha256Hex(is);
            synchronized (JarUtil.class) {
                outputJarFile = new File(targetDir, nestedJarName + "-" + hash + ".jar");
                if (!outputJarFile.exists()) {
                    logger.info(String.format("Extracting nested jar %s from %s to %s", nestedJarPath, jar.getName(), outputJarFile));
                    is.rewind();
                    try (final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputJarFile))) {
                        final byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }

        } catch (IOException e) {
            logger.error(String.format("Unable to read the nested jar %s in %s - ignoring", nestedJarPath, jar.getName()));
            return null;
        }
        return new NestedJar(outputJarFile, nestedJarFilename, hash);
    }

    public static DefaultArtifactVersion guessVersion(MetadataCollection mc, String name) {
        if (mc != null) {
            if (mc.modList == null) {
                return new DefaultArtifactVersion("0.0.0");
            }
            for (ModMetadata meta : mc.modList) {
                final DefaultArtifactVersion version = new DefaultArtifactVersion(meta.version);
                if (!meta.version.equals(version.getQualifier())) {
                    return version;
                }
            }
        }
        // parse the version from the filename
        final Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
            return new DefaultArtifactVersion(matcher.group(1));
        } else {
            return new DefaultArtifactVersion("0.0.0");
        }
    }

    public static DefaultArtifactVersion guessVersion(ModMetadata md, String modid) {
        if(md != null) {
            final DefaultArtifactVersion version = new DefaultArtifactVersion(md.version);
            if (!md.version.equals(version.getQualifier())) {
                return version;
            }
        }
        return new DefaultArtifactVersion("0.0.0");

    }

    private static ModCandidateV2 examineModCandidate(NestedJar nestedJar, ModCandidateV2 parent, boolean coremodPass) {
        final ModCandidateV2 candidate = examineModCandidate(nestedJar.file, parent, coremodPass);
        if (candidate != null) {
            candidate.setHash(nestedJar.hash);
            candidate.setSourceFilename(nestedJar.sourceFilename);
        }

        return candidate;
    }

    public static @Nullable ModCandidateV2 examineModCandidate(File modFile, ModCandidateV2 parent, boolean coremodPass) {
        return examineModCandidate(modFile, parent, coremodPass, false, false);
    }

    public static @Nullable ModCandidateV2 examineModCandidate(File modFile, ModCandidateV2 parent, boolean coremodPass, boolean isMinecraft, boolean isClasspath) {
        try (final JarFile jar = new JarFile(modFile)) {
            return examineJarCandidate(jar, modFile, parent, coremodPass, isMinecraft, isClasspath);

        } catch (IOException ioe) {
            FMLRelaunchLog.log(Level.ERROR, ioe, "Unable to read the jar file %s - ignoring", modFile.getName());
            return null;
        }

    }

    public static @Nullable ModCandidateV2 examineJarCandidate(JarFile jar, File modFile, ModCandidateV2 parent, boolean coremodPass, boolean isMinecraft, boolean isClasspath) throws IOException {
        final ModCandidateV2 modCandidate;
        final Attributes attributes;
        if (jar.getManifest() == null && coremodPass) {
            // No manifest means we don't really know anything about it other than it's not a coremod and has no access transformer list
            return null;
        }

        attributes = jar.getManifest() != null ? jar.getManifest().getMainAttributes() : new Attributes();

        modCandidate = new ModCandidateV2(modFile, modFile, ContainerType.JAR, isMinecraft, isClasspath);
        modCandidate.addParent(parent);

        final JarEntry modInfoEntry = jar.getJarEntry("mcmod.info");
        final MetadataCollection meta;
        if (modInfoEntry != null) {
            FMLLog.finer("Located mcmod.info file in file %s", modFile);
            meta = MetadataCollection.from(jar.getInputStream(modInfoEntry), modFile.getName());
        } else {
            meta = MetadataCollection.from(null, "");
        }
        modCandidate.setMetadataCollection(meta);
        final DefaultArtifactVersion version = guessVersion(meta, modFile.getName());
        modCandidate.setVersion(version);

        if(coremodPass) {
            modCandidate.setNestedModcandidates(checkNestedMods(jar, modCandidate, coremodPass));
        } else {
            modCandidate.setNestedJars(checkNestedJars(jar));
        }

        final String atList = attributes.getValue("FMLAT");
        if (atList != null && coremodPass) {
            final Map<String, String> modAccessTransformers = new HashMap<>();
            for (String at : atList.split(" ")) {
                final JarEntry jarEntry = jar.getJarEntry("META-INF/" + at);
                if (jarEntry == null) continue;
                modAccessTransformers.put(
                    String.format("%s!META-INF/%s", jar.getName(), at),
                    new JarByteSource(jar, jarEntry).asCharSource(Charsets.UTF_8).read());
            }
            modCandidate.setAccessTransformers(modAccessTransformers);
        }

        final String cascadedTweaker = attributes.getValue("TweakClass"); // *
        if (cascadedTweaker != null) {
            FMLRelaunchLog.info("Identified tweaker %s from %s", cascadedTweaker, modFile.getName());
            final int sortOrder = Optional.ofNullable(Ints.tryParse(Strings.nullToEmpty(attributes.getValue("TweakOrder")))).orElse(0);

            modCandidate.setTweaker(cascadedTweaker).setSortOrder(sortOrder);
        }

        final List<String> modTypes = attributes.containsKey(CoreModManager.MODTYPE) ? Arrays.asList(attributes.getValue(CoreModManager.MODTYPE).split(",")) : ImmutableList.of("FML");
        if (!modTypes.contains("FML") && (!modCandidate.hasTweaker() || !coremodPass)) {
            FMLRelaunchLog.fine("Adding %s to the list of things to skip. It is not an FML mod,  it has types %s", modFile.getName(), modTypes);
            if(coremodPass) addLoadedCoremod(modFile.getName());
            return null;
        }

        final String modSide = attributes.containsKey(CoreModManager.MODSIDE) ? attributes.getValue(CoreModManager.MODSIDE) : "BOTH";
        if (!("BOTH".equals(modSide) || FMLLaunchHandler.side.name().equals(modSide))) {
            FMLRelaunchLog.fine(
                "Mod %s has ModSide meta-inf value %s, and we're %s. It will be ignored",
                modFile.getName(), modSide, FMLLaunchHandler.side.name());
            if(coremodPass) addLoadedCoremod(modFile.getName());
            return null;
        }

        final String fmlCorePlugin = attributes.getValue("FMLCorePlugin");
        if (fmlCorePlugin != null) {
//            FMLRelaunchLog.info("Found FMLCorePluginContainsFMLMod marker in %s", modFile.getName());
            modCandidate.setCoreMod(fmlCorePlugin)
                .setContainsMod(attributes.containsKey(CoreModManager.COREMODCONTAINSFMLMOD) || "true".equalsIgnoreCase(attributes.getValue(CoreModManagerV2.FORCELOADASMOD)));
        } else if (coremodPass) {
            FMLRelaunchLog.fine("Not found coremod data in %s", modFile.getName());
        }
        if (coremodPass) applyConfiguredSortOrder(modCandidate);
        if (coremodPass && !isMinecraft) {
            scanPackagesAndApi(jar, modCandidate);
        }
        return modCandidate;
    }

    private static void scanPackagesAndApi(JarFile jar, ModCandidateV2 candidate) {
        final Enumeration<JarEntry> entries = jar.entries();
        String lastDir = null;
        String lastPkg = null;
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            final String name = entry.getName();
            if (!name.endsWith(".class")) continue;
            final int slash = name.lastIndexOf('/');
            final String dir = slash < 0 ? "" : name.substring(0, slash);
            final String pkg;
            if (dir.equals(lastDir)) {
                pkg = lastPkg;
            } else {
                pkg = dir.replace('/', '.');
                candidate.addEarlyPackage(pkg);
                lastDir = dir;
                lastPkg = pkg;
            }
            if (name.endsWith("/package-info.class")) {
                try {
                    final DefaultArtifactVersion apiVersion = readApiVersion(new JarByteSource(jar, entry).read());
                    if (apiVersion != null) candidate.addDeclaredApiPackage(pkg, apiVersion);
                } catch (Exception e) {
                    FMLRelaunchLog.log(Level.WARN, e, "Failed reading @API from %s in %s", name, candidate.getFilename());
                }
            }
        }
    }

    private static DefaultArtifactVersion readApiVersion(byte[] classBytes) {
        final boolean[] seen = {false};
        final String[] version = {null};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (!ModCandidateV2.API_ANNOTATION_DESC.equals(desc)) return null;
                seen[0] = true;
                return new AnnotationVisitor(Opcodes.ASM5) {
                    @Override
                    public void visit(String n, Object value) {
                        if ("apiVersion".equals(n) && value instanceof String) version[0] = (String) value;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!seen[0]) return null;
        return new DefaultArtifactVersion(version[0] == null || version[0].isEmpty() ? "0.0.0" : version[0]);
    }

    private static void applyConfiguredSortOrder(ModCandidateV2 candidate) {
        final String coreMod = candidate.getCoreMod();
        if (coreMod == null || !Config.enableSortingIndexOverrides) return;
        if (Config.sortingIndexOverrides.containsKey(coreMod)) {
            final int sortOrder = Config.sortingIndexOverrides.get(coreMod);
            candidate.setSortOrder(sortOrder);
            FMLRelaunchLog.log(Level.INFO, "Applying configured SortingIndex %d to coremod %s", sortOrder, coreMod);
        } else if (HODGEPODGE_COREMOD.equals(coreMod) && candidate.getVersion().compareTo(HODGEPODGE_LATE_TRANSFORMER_VERSION) < 0) {
            // Hodgepodge pre 2.7.147 must register after NEI or OreDictionary won't verify.
            candidate.setSortOrder(HODGEPODGE_SORT_ORDER);
            FMLRelaunchLog.log(Level.INFO, "Forcing SortingIndex %d for Hodgepodge (%s).", HODGEPODGE_SORT_ORDER, candidate.getVersion());
        }
    }

    public static List<ModCandidateV2> checkNestedMods(JarFile jar, ModCandidateV2 parent, boolean coremodPass) throws IOException {
        final List<NestedJar> nestedJars = checkNestedJars(jar);
        if (nestedJars.isEmpty()) return Collections.emptyList();
        final List<ModCandidateV2> nestedMods = new ArrayList<>();

        for (NestedJar nestedJar : nestedJars) {
            final ModCandidateV2 nestedMod = examineModCandidate(nestedJar, parent, coremodPass);
            if (nestedMod == null) continue;
            nestedMod.setHash(nestedJar.hash);
            nestedMods.add(nestedMod);
        }
        return nestedMods;
    }


    private static void addLoadedCoremod(String name) {
        synchronized (CoreModManager.loadedCoremods) {
            CoreModManager.loadedCoremods.add(name);
        }
    }

    public static List<NestedJar> checkNestedJars(JarFile jar) throws IOException {
        final Manifest manifest = jar.getManifest();
        if (manifest == null) return Collections.emptyList();

        final String nestedJarsEntry = manifest.getMainAttributes().getValue("Jar-In-Jar");

        if (nestedJarsEntry == null) return Collections.emptyList();

        final List<NestedJar> nestedJars = new ArrayList<>();
        final Set<String> nestedJarPaths = new HashSet<>(Arrays.asList(nestedJarsEntry.split(",")));
        for (String nestedJarPath : nestedJarPaths) {
            final NestedJar nestedJar = getNestedJar(jar, nestedJarPath, CoreModManagerV2.getNestedDir());
            if (nestedJar == null) continue;

            nestedJars.add(nestedJar);
        }
        return nestedJars;
    }
}
