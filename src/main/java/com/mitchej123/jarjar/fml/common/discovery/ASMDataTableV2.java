package com.mitchej123.jarjar.fml.common.discovery;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.gtnewhorizons.retrofuturabootstrap.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ModCandidate;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
public class ASMDataTableV2 extends ASMDataTable {
    private static final String API_ANNOTATION = "cpw.mods.fml.common.API";

    public ASMDataTableV2() {
        super();
        this.globalAnnotationData = LinkedHashMultimap.create();
    }

    @Override
    public Set<ASMData> getAll(String annotation) {
        final Set<ASMData> all = super.getAll(annotation);
        if (!API_ANNOTATION.equals(annotation) || all == null || all.size() < 2) {
            return all;
        }
        final List<ASMData> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(ASMDataTableV2::apiVersionOf).reversed());
        return new LinkedHashSet<>(sorted);
    }

    private static DefaultArtifactVersion apiVersionOf(ASMData data) {
        String v = null;
        if (data != null && data.getAnnotationInfo() != null) {
            final Object raw = data.getAnnotationInfo().get("apiVersion");
            if (raw instanceof String) {
                v = (String) raw;
            }
        }
        if (v == null || v.isEmpty()) {
            v = "0.0.0";
        }
        return new DefaultArtifactVersion(v);
    }

    @Override
    public SetMultimap<String, ASMData> getAnnotationsFor(ModContainer container) {
        if (containerAnnotationData == null) {
            final Map<File, ImmutableSetMultimap.Builder<String, ASMData>> builders = new HashMap<>();
            for (Map.Entry<String, ASMData> e : globalAnnotationData.entries()) {
                final ASMData data = e.getValue();
                final ModCandidate cand = data.getCandidate();
                if (cand == null) continue;
                final File src = cand.getModContainer();
                if (src == null) continue;
                builders.computeIfAbsent(src, k -> ImmutableSetMultimap.builder()).put(e.getKey(), data);
            }
            final Map<File, ImmutableSetMultimap<String, ASMData>> bySource = new HashMap<>(builders.size());
            for (Map.Entry<File, ImmutableSetMultimap.Builder<String, ASMData>> e : builders.entrySet()) {
                bySource.put(e.getKey(), e.getValue().build());
            }
            final ImmutableMap.Builder<ModContainer, SetMultimap<String, ASMData>> out = ImmutableMap.builder();
            for (ModContainer cont : containers) {
                final File src = cont.getSource();
                final ImmutableSetMultimap<String, ASMData> mm = src == null ? null : bySource.get(src);
                out.put(cont, mm == null ? ImmutableSetMultimap.of() : mm);
            }
            containerAnnotationData = out.build();
        }
        return containerAnnotationData.get(container);
    }

}
