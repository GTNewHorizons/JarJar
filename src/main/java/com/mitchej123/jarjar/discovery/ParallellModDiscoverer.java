/*
 * Note: Adapted from Fabric's ModDiscoverer and is dual licensed under the LGPL v3.0 and Apache 2.0 licenses.
 *
 * Original Copyright notice:
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mitchej123.jarjar.discovery;

import com.mitchej123.jarjar.fml.common.LoaderUtil;
import com.mitchej123.jarjar.fml.common.ModContainerFactoryV2;
import com.mitchej123.jarjar.fml.common.ModContainerWrapper;
import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import com.mitchej123.jarjar.fml.common.discovery.asm.ASMModParserV2;
import com.mitchej123.jarjar.fml.common.discovery.finder.ClasspathModCandidateFinder;
import com.mitchej123.jarjar.fml.common.discovery.finder.DirectoryModCandidateFinder;
import com.mitchej123.jarjar.fml.common.discovery.finder.ModCandidateFinder;
import com.mitchej123.jarjar.util.JarUtil;
import com.mitchej123.jarjar.util.RewindableModInputStream;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.LoaderException;
import cpw.mods.fml.common.ModClassLoader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModContainerFactory;
import cpw.mods.fml.common.discovery.ModDiscoverer;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.ModListHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ParallellModDiscoverer extends ModDiscoverer {

    private static final Logger LOGGER = LogManager.getLogger("ParallellModDiscoverer");
    public static final String JARJAR_DEBUG_DISCOVERY_TIMEOUT = "jarjar.debug.discoveryTimeout";
    public static final String FORCE_LOAD_AS_MOD = "ForceLoadAsMod";

    public List<ModCandidateV2> getModCandidates() {
        return modCandidates;
    }

    private final List<ModCandidateV2> modCandidates = new ArrayList<>();
    private final List<ModCandidateFinder> candidateFinders = new ArrayList<>();
    private final Map<String, ModScanTask> jijDedupMap = new ConcurrentHashMap<>(); // avoids reading the same jar twice
    private final List<NestedModInitData> nestedModInitDatas = Collections.synchronizedList(new ArrayList<>()); // breaks potential cycles from deduplication
    private final File mcDir;


    public ParallellModDiscoverer(File modsDir, ModClassLoader modClassLoader) {
        this.mcDir = modsDir;
        candidateFinders.add(new ClasspathModCandidateFinder(modClassLoader));
        candidateFinders.add(new DirectoryModCandidateFinder(
            modsDir.toPath(),
            ModListHelper.additionalMods.values().toArray(new File[0]),
            true)
        );
        File versionSpecificModsDir = new File(modsDir, FMLInjectionData.mccversion);
        if (versionSpecificModsDir.isDirectory()) {
            FMLLog.info("Also searching %s for mods", versionSpecificModsDir);
            candidateFinders.add(new DirectoryModCandidateFinder(versionSpecificModsDir.toPath(), true));
        }

    }

    public final void discoverMods() {  // throws LoaderException
        LOGGER.info(String.format("Mod discovery started in %s", mcDir));
        long startTime = System.nanoTime();
        ForkJoinPool pool = new ForkJoinPool();
        Set<Path> processedPaths = new HashSet<>(); // suppresses duplicate paths
        List<Future<ModCandidateV2>> futures = new ArrayList<>();

        ModCandidateFinder.ModCandidateConsumer taskSubmitter = (paths, requiresRemap, isMinecraft, isClasspath) -> {
            List<Path> pendingPaths = new ArrayList<>(paths.size());

            for (Path path : paths) {
                assert path.equals(LoaderUtil.normalizeExistingPath(path));

                if (processedPaths.add(path)) {
                    pendingPaths.add(path);
                }
            }

            if (!pendingPaths.isEmpty()) {
                futures.add(pool.submit(new ModScanTask(pendingPaths, requiresRemap, isMinecraft, isClasspath)));
            }
        };

        for (ModCandidateFinder finder : candidateFinders) {
            finder.findCandidates(taskSubmitter);
        }

        List<ModCandidateV2> candidates = new ArrayList<>();
        RuntimeException exception = null;

        int timeout = Integer.getInteger(JARJAR_DEBUG_DISCOVERY_TIMEOUT, -1);
        if (timeout <= 0) timeout = Integer.MAX_VALUE;

        try {
            pool.shutdown();

            pool.awaitTermination(timeout, TimeUnit.SECONDS);

            for (Future<ModCandidateV2> future : futures) {
                if (!future.isDone()) {
                    throw new TimeoutException();
                }

                try {
                    ModCandidateV2 candidate = future.get();
                    if (candidate != null) candidates.add(candidate);
                } catch (ExecutionException e) {
                    exception = new RuntimeException("Mod discovery failed!", e);
                }
            }

            for (NestedModInitData data : nestedModInitDatas) {
                for (Future<ModCandidateV2> future : data.futures) {
                    if (!future.isDone()) {
                        throw new TimeoutException();
                    }

                    try {
                        ModCandidateV2 candidate = future.get();
                        if (candidate != null) data.target.add(candidate);
                    } catch (ExecutionException e) {
                        exception = new RuntimeException("Mod discovery failed!", e);
                    }
                }
            }
        } catch (TimeoutException e) {
            e.printStackTrace();
            throw new RuntimeException(
                String.format("Mod discovery took too long! Analyzing the mod folder contents took longer than %d seconds. This may be caused by unusually slow hardware, pathological antivirus interference or other issues. The timeout can be changed with the system property %s (-D%<s=<desired timeout in seconds>).",
                timeout, JARJAR_DEBUG_DISCOVERY_TIMEOUT));
        } catch (InterruptedException e) {
            throw new RuntimeException("Mod discovery interrupted!", e);
        }

        if (exception != null) {
            throw exception;
        }

        // gather all mods (root+nested), initialize parent data
        Set<ModCandidateV2> ret = Collections.newSetFromMap(new IdentityHashMap<>(candidates.size() * 2));
        Queue<ModCandidateV2> queue = new ArrayDeque<>(candidates);
        ModCandidateV2 mod;

        while ((mod = queue.poll()) != null) {
            if (!ret.add(mod)) continue;

            if(mod.getNestedModcandidates() == null) continue;
            for (ModCandidateV2 child : mod.getNestedModcandidates()) {
                if (child.addParent(mod)) {
                    queue.add(child);
                }
            }
        }


        long endTime = System.nanoTime();

        LOGGER.info(String.format("Mod discovery time: %.1f ms", (endTime - startTime) * 1e-6));

        modCandidates.addAll(ret);
        modCandidates.sort(Comparator.comparing(candidate -> candidate.getModContainer().getName()));
    }

    private static class NestedModInitData {
        final List<? extends Future<ModCandidateV2>> futures;
        final List<ModCandidateV2> target;

        NestedModInitData(List<? extends Future<ModCandidateV2>> futures, List<ModCandidateV2> target) {
            this.futures = futures;
            this.target = target;
        }
    }

    final class ModScanTask extends RecursiveTask<ModCandidateV2> {

        private final List<Path> paths;
        private final String localPath;
        private final RewindableModInputStream is;
        private final String hash;
        private final boolean requiresRemap;
        private final List<String> parentPaths;
        private final boolean isMinecraft;
        private final boolean isClasspath;

        ModScanTask(List<Path> paths, boolean requiresRemap) {
            this(paths, null, null, null, requiresRemap, Collections.emptyList(), false, false);
        }

        ModScanTask(List<Path> paths, boolean requiresRemap, boolean isMinecraft, boolean isClasspath) {
            this(paths, null, null, null, requiresRemap, Collections.emptyList(), isMinecraft, isClasspath);
        }

        private ModScanTask(List<Path> paths, String localPath, RewindableModInputStream is, String hash, boolean requiresRemap, List<String> parentPaths, boolean isMinecraft, boolean isClasspath) {
            this.paths = paths;
            this.localPath = localPath != null ? localPath : paths.get(0).toString();
            this.is = is;
            this.hash = hash;
            this.requiresRemap = requiresRemap;
            this.parentPaths = parentPaths;
            this.isMinecraft = isMinecraft;
            this.isClasspath = isClasspath;
        }

        @Override
        protected ModCandidateV2 compute() {
            try {
                for (Path path : paths) {
                    if (Files.isDirectory(path)) return null;

                    return computeJarFile(path);
                }
            } catch (Throwable t) {
                throw new RuntimeException(String.format("Error analyzing %s: %s", paths, t), t);
            }

            return null;
        }

        private ModCandidateV2 computeJarFile(Path path) throws IOException/*, ParseMetadataException*/ {
            final File modFile = path.toFile();
            final String modFileName = modFile.getName();
            FMLRelaunchLog.fine("Examining for mod candidacy %s", modFileName);
            final Attributes attributes;
            final ModCandidateV2 modCandidate;

            try (JarFile jar = new JarFile(modFile)) {
                attributes = jar.getManifest() != null ? jar.getManifest().getMainAttributes() : new Attributes();

                modCandidate = JarUtil.examineJarCandidate(jar, modFile, null, false, isMinecraft, isClasspath);
                if(modCandidate == null) return null;

                // If the tweaker does not specify ForceLoadAsMod, return as we would in FML otherwise continue like we do in Mixins
                if(modCandidate.hasTweaker() && !"true".equalsIgnoreCase(attributes.getValue(FORCE_LOAD_AS_MOD))) {
                    return modCandidate;
                }

                if (modCandidate.hasNestedJars()) {
                    final List<ModScanTask> tasks = new ArrayList<>(5);
                    ModScanTask localTask = null;
                    for(JarUtil.NestedJar nestedJar : modCandidate.getNestedJars()) {
                        ModScanTask task = jijDedupMap.get(nestedJar.hash());
                        if (task == null) {
                            task = new ModScanTask(Collections.singletonList(nestedJar.file().toPath()), true);
                            ModScanTask prev = jijDedupMap.putIfAbsent(nestedJar.hash(), task);
                            if (prev != null) {
                                task = prev;
                            } else if (localTask == null) { // don't fork first task, leave it for this thread
                                localTask = task;
                            } else {
                                task.fork();
                            }
                        }
                        tasks.add(task);
                    }
                    if(tasks.size() > 0) {
                        if (localTask != null) localTask.invoke();
                        List<ModCandidateV2> nestedMods = new ArrayList<>();
                        modCandidate.setNestedModcandidates(nestedMods);
                        nestedModInitDatas.add(new NestedModInitData(tasks, nestedMods));
                    }
                } else {
                    modCandidate.setNestedModcandidates(Collections.emptyList());
                }


                Enumeration<JarEntry> entries = jar.entries();
                final ModContainerFactoryV2 modContainerFactory = (ModContainerFactoryV2) ModContainerFactory.instance();
                final List<ModContainerWrapper> wrappedModList = new ArrayList<>();
                final List<ModContainer> modList = new ArrayList<>();
                final List<ASMModParserV2> asmDataCollection = modCandidate.getAsmDataCollection();
                modCandidate.setWrappedMods(wrappedModList);
                modCandidate.setMods(modList);

                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    final String entryName = entry.getName();
                    final ASMModParserV2 asmData;
                    if (entry.isDirectory() || entryName.startsWith("__MACOSX") || !entryName.endsWith(".class") || entryName.endsWith("$.class")) continue;
                    try(final InputStream classStream = jar.getInputStream(entry)) {
                        asmData = new ASMModParserV2(classStream, entryName);
                    } catch (LoaderException e) {
                        FMLRelaunchLog.log(Level.ERROR, e, "There was a problem reading the entry %s in the jar %s - probably a corrupt zip", entryName, modFileName);
                        return null;
                    }
                    asmData.validate();
                    asmDataCollection.add(asmData);

                    final ModContainerWrapper wrapped = modContainerFactory.buildV2(asmData, modCandidate.getModContainer(), modCandidate);
                    if(wrapped != null) {
                        wrappedModList.add(wrapped);
                        modList.add(wrapped.mod());
                    }
                }


            } catch (IOException ioe) {
                FMLRelaunchLog.log(Level.ERROR, ioe, "Unable to read the jar file %s - ignoring", modFileName);
                return null;

            }
            return modCandidate;
        }

    }

}
