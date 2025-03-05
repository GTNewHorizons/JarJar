package com.mitchej123.jarjar.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * Modified/Adapted from RFB
 */
public class ModCandidateV2Sorter<T extends SortableCandidate> {

    private static final Logger LOGGER = LogManager.getLogger("ModCandidateV2Sorter");

    protected final List<T> candidates = new ArrayList<>();
    protected final Set<T> disabled = Collections.newSetFromMap(new IdentityHashMap<>());
    protected boolean criticalIssuesFound = false;

    public Set<String> getDisabledFiles() {
        return disabled.stream()
            .map(t -> t.getFile().getName())
            .collect(Collectors.toSet());
    }

    public ModCandidateV2Sorter(Collection<T> candidates, Comparator<T> comparator) {
        this.candidates.addAll(candidates);
        if(comparator != null) this.candidates.sort(comparator);
    }

    public Optional<List<T>> resolve() {
        handleDuplicates();
        return criticalIssuesFound ? Optional.empty() : Optional.of(candidates);
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
