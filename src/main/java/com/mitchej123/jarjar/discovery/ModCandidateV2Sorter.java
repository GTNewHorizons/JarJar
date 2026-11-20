package com.mitchej123.jarjar.discovery;

import com.gtnewhorizons.retrofuturabootstrap.versioning.DefaultArtifactVersion;
import com.mitchej123.jarjar.config.CoremodExemptions;
import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import cpw.mods.fml.common.LoaderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/*
 * Modified/Adapted from RFB
 */
public class ModCandidateV2Sorter<T extends SortableCandidate> {

    private static final Logger LOGGER = LogManager.getLogger("ModCandidateV2Sorter");

    protected final List<T> candidates = new ArrayList<>();
    protected final Set<T> disabled = Collections.newSetFromMap(new IdentityHashMap<>());
    protected boolean criticalIssuesFound = false;
    private final Comparator<T> comparator;
    private List<T> classpathOrder;

    public Set<String> getDisabledFiles() {
        return disabled.stream()
            .map(t -> t.getFile().getName())
            .collect(Collectors.toSet());
    }

    public ModCandidateV2Sorter(Collection<T> candidates, Comparator<T> comparator) {
        this.candidates.addAll(candidates);
        this.comparator = comparator;
    }

    public Optional<List<T>> resolve() {
        handleDuplicates();
        try {
            if (criticalIssuesFound) return Optional.empty();
            if (comparator != null) candidates.sort(comparator);
            classpathOrder = computeClasspathOrder();
            return Optional.of(candidates);
        } finally {
            releaseEarlyScan();
        }
    }

    public List<T> getClasspathOrder() {
        return classpathOrder != null ? classpathOrder : candidates;
    }

    // Ensures API owners load before non owning API Packagers.
    private List<T> computeClasspathOrder() {
        final int n = candidates.size();
        if (n < 2) return new ArrayList<>(candidates);
        for (T c : candidates) {
            if (!(c instanceof ModCandidateV2)) return new ArrayList<>(candidates);
        }

        final Map<String, Integer> ownerByPkg = new HashMap<>();
        final Map<String, DefaultArtifactVersion> ownerVersionByPkg = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (Map.Entry<String, DefaultArtifactVersion> e : ((ModCandidateV2) candidates.get(i)).getDeclaredApiPackages().entrySet()) {
                final DefaultArtifactVersion cur = ownerVersionByPkg.get(e.getKey());
                if (cur == null || e.getValue().compareTo(cur) > 0) {
                    ownerByPkg.put(e.getKey(), i);
                    ownerVersionByPkg.put(e.getKey(), e.getValue());
                }
            }
        }
        if (ownerByPkg.isEmpty()) return new ArrayList<>(candidates);

        final List<Set<Integer>> successors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) successors.add(new HashSet<>());
        final int[] indegree = new int[n];
        final Map<String, Set<String>> conflicts = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            for (String pkg : ((ModCandidateV2) candidates.get(i)).getEarlyPackages()) {
                final Integer owner = ownerByPkg.get(pkg);
                if (owner == null || owner == i) continue;
                if (successors.get(owner).add(i)) indegree[i]++;
                conflicts.computeIfAbsent(
                    ((ModCandidateV2) candidates.get(owner)).getFilename() + " ahead of " + ((ModCandidateV2) candidates.get(i)).getFilename(),
                    k -> new TreeSet<>()).add(pkg);
            }
        }
        if (conflicts.isEmpty()) return new ArrayList<>(candidates);
        for (Map.Entry<String, Set<String>> e : conflicts.entrySet()) {
            LOGGER.info("API ownership: prioritizing {} for package(s) {}", e.getKey(), e.getValue());
        }

        final List<T> ordered = new ArrayList<>(n);
        final boolean[] emitted = new boolean[n];
        final PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < n; i++) if (indegree[i] == 0) ready.add(i);
        while (!ready.isEmpty()) {
            final int pick = ready.poll();
            ordered.add(candidates.get(pick));
            emitted[pick] = true;
            for (int s : successors.get(pick)) {
                if (--indegree[s] == 0) ready.add(s);
            }
        }
        if (ordered.size() < n) {
            LOGGER.warn("API ownership: cyclic API package ownership -- leaving remaining mods in resolved order");
            for (int i = 0; i < n; i++) if (!emitted[i]) ordered.add(candidates.get(i));
        }
        return ordered;
    }

    private void releaseEarlyScan() {
        for (T c : candidates) {
            if (c instanceof ModCandidateV2) ((ModCandidateV2) c).releaseEarlyScanData();
        }
    }

    private void handleDuplicates() {
        final Map<String, List<T>> idLookup = new HashMap<>(candidates.size());
        for (T candidate : candidates) {
            idLookup.computeIfAbsent(candidate.getId(), _id -> new ArrayList<>(1)).add(candidate);
        }
        // find and disable duplicates
        for (Map.Entry<String, List<T>> entry : idLookup.entrySet()) {
            final List<T> equalIdCandidates = entry.getValue();
            if (equalIdCandidates.size() < 2) {
                continue;
            }

            // Check if this is a coremod that should be exempt from duplicate detection
            boolean isExemptCoremod = false;
            // We only need to check one at this point since they're all the same
            if (equalIdCandidates.get(0) instanceof ModCandidateV2 firstCandidate) {
                if (firstCandidate.hasCoreMod()) {
                    String coremodClassName = firstCandidate.getCoreMod();
                    if (CoremodExemptions.isExempt(coremodClassName)) {
                        isExemptCoremod = true;
                        LOGGER.info("Skipping duplicate detection for exempt coremod: {} (found in {} mods)",
                                   coremodClassName, equalIdCandidates.size());
                    }
                }
            }

            if (isExemptCoremod) {
                // Skip duplicate detection for exempt coremods
                continue;
            }

            final Set<File> files = new HashSet<>();
            for (final T it : equalIdCandidates) {
                if (!files.add(it.getFile())) {
                    final String msg = String.format("Mod id %s found multiple times in the same jar %s - Likely a Multi-Release Jar issue", entry.getKey(), it.getFile());
                    LOGGER.error(msg);
                    throw new LoaderException(msg);
                }
            }

            T newest = null;
            for (final T it : equalIdCandidates) {
                if (disabled.contains(it)) {
                    continue;
                }
                if (newest == null) {
                    newest = it;
                } else {
                    if (newest.getVersion().compareTo(it.getVersion()) < 0) {
                        disabled.add(newest);
                        LOGGER.warn("Duplicate mod found: {}, disabling {} ({}) in favor of {} ({})", newest.getId(), newest.getVersion(), newest.getFile(), it.getVersion(), it.getFile());
                        newest = it;
                    } else {
                        LOGGER.warn("Duplicate mod found: {}, disabling {} ({}) in favor of {} ({})", it.getId(), it.getVersion(), it.getFile(), newest.getVersion(), newest.getFile());
                        disabled.add(it);
                    }
                }
            }
        }
        candidates.removeIf(disabled::contains);
    }

}
