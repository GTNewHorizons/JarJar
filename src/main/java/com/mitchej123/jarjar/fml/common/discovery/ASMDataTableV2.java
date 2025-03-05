package com.mitchej123.jarjar.fml.common.discovery;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.discovery.ASMDataTable;
import org.apache.commons.lang3.tuple.Pair;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("unused")
public class ASMDataTableV2 extends ASMDataTable {

    @Override
    public SetMultimap<String, ASMData> getAnnotationsFor(ModContainer container) {
        if (containerAnnotationData == null) {
            //concurrently filter the values to speed this up
            containerAnnotationData = containers.parallelStream()
                .map(cont -> Pair.of(cont, ImmutableSetMultimap.copyOf(Multimaps.filterValues(globalAnnotationData, new ModContainerPredicate(cont)))))
                .collect(collectingAndThen(toMap(Pair::getKey, Pair::getValue), ImmutableMap::copyOf));
        }
        return containerAnnotationData.get(container);
    }

}

