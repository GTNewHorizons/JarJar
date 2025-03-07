package com.mitchej123.jarjar.fml.common.discovery;

import com.gtnewhorizons.retrofuturabootstrap.versioning.DefaultArtifactVersion;
import com.mitchej123.jarjar.discovery.SortableCandidate;
import com.mitchej123.jarjar.fml.common.ModContainerWrapper;
import com.mitchej123.jarjar.fml.common.discovery.asm.ASMModParserV2;
import com.mitchej123.jarjar.util.JarUtil;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ContainerType;
import cpw.mods.fml.common.discovery.ModCandidate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModCandidateV2 extends ModCandidate implements SortableCandidate {

    private Map<String, String> accessTransformers = null;
    private String cascadedTweaker = null;
    private String fmlCorePlugin = null;
    private boolean containsMod = false;

    private Collection<ModCandidateV2> nestedModcandidates = null;
    private Collection<ModCandidateV2> parentModcandidates;
    private int nestLevel = 0;

    private List<ModContainerWrapper> wrappedMods;
    private List<ASMModParserV2> asmDataCollection = new ArrayList<>();

    private List<JarUtil.NestedJar> nestedJars = null;

    private String hash = null;

    private String sourceFilename = null;

    private int sortOrder = 0;
    private MetadataCollection metadataCollection = null;

    private DefaultArtifactVersion version = new DefaultArtifactVersion("0.0.0");

    public ModCandidateV2(File classPathRoot, File modContainer, ContainerType sourceType) {
        super(classPathRoot, modContainer, sourceType);
    }

    public ModCandidateV2(File classPathRoot, File modContainer, ContainerType sourceType, boolean isMinecraft, boolean classpath) {
        super(classPathRoot, modContainer, sourceType, isMinecraft, classpath);
    }

    public boolean hasAccessTransformers() {
        return accessTransformers != null;
    }

    public Map<String, String> getAccessTransformers() {
        return accessTransformers;
    }

    public ModCandidateV2 setAccessTransformers(Map<String, String> modAccessTransformers) {
        this.accessTransformers = modAccessTransformers;
        return this;
    }

    public boolean hasTweaker() {
        return cascadedTweaker != null;
    }

    public ModCandidateV2 setTweaker(String cascadedTweaker) {
        this.cascadedTweaker = cascadedTweaker;
        return this;
    }

    public String getTweaker() {
        return cascadedTweaker;
    }

    public boolean hasCoreMod() {
        return fmlCorePlugin != null;
    }

    public String getCoreMod() {
        return fmlCorePlugin;
    }

    public ModCandidateV2 setCoreMod(String fmlCorePlugin) {
        this.fmlCorePlugin = fmlCorePlugin;
        return this;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public ModCandidateV2 setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public ModCandidateV2 setContainsMod(boolean containsMod) {
        this.containsMod = containsMod;
        return this;
    }

    public boolean containsMod() {
        return containsMod;
    }

    public boolean isNested() {
        return nestLevel > 0;
    }

    @Override
    public void addClassEntry(String name) {
        super.addClassEntry(name);
    }

    public void setNestedModcandidates(List<ModCandidateV2> nestedModcandidates) {
        this.nestedModcandidates = nestedModcandidates;
    }

    public Collection<ModCandidateV2> getNestedModcandidates() {
        return nestedModcandidates;
    }

    public boolean hasNestedMods() {
        return nestedModcandidates != null && nestedModcandidates.size() > 0;
    }

    public boolean addParent(ModCandidateV2 parent) {
        if (this.parentModcandidates == null) {
            this.parentModcandidates = new ArrayList<>();
        }
        if (this.parentModcandidates.contains(parent)) return false;
        this.parentModcandidates.add(parent);
        updateNestLevel(parent);

        return true;
    }

    @Override
    public String getId() {
        if(fmlCorePlugin != null) return fmlCorePlugin;
        if(sourceFilename != null) return sourceFilename;
        return modContainer.getName();
    }

    public List<ASMModParserV2> getAsmDataCollection() {
        return asmDataCollection;
    }

    private void updateNestLevel(ModCandidateV2 parent) {
        if (parent != null && parent.nestLevel + 1 > this.nestLevel) {
            this.nestLevel = parent.nestLevel + 1;
        }
    }

    public int getNestLevel() {
        return nestLevel;
    }

    public boolean hasNestedJars() {
        return nestedJars != null && nestedJars.size() > 0;
    }

    public void setNestedJars(List<JarUtil.NestedJar> nestedJars) {
        this.nestedJars = nestedJars;
    }

    public List<JarUtil.NestedJar> getNestedJars() {
        return nestedJars;
    }

    public void setMetadataCollection(MetadataCollection metadataCollection) {
        this.metadataCollection = metadataCollection;
    }

    public MetadataCollection getMetadataCollection() {
        return metadataCollection;
    }

    public void setMods(List<ModContainer> modList) {
        this.mods = modList;
    }

    public void setWrappedMods(List<ModContainerWrapper> mods) {
        this.wrappedMods = mods;
    }

    public List<ModContainerWrapper> getWrappedMods() {
        if (wrappedMods == null) return Collections.emptyList();
        return wrappedMods;
    }

    public ModCandidateV2 setVersion(DefaultArtifactVersion version) {
        this.version = version;
        return this;
    }

    public DefaultArtifactVersion getVersion() {
        return version;
    }

    @Override
    public File getFile() {
        return getModContainer();
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getFilename() {
        if (sourceFilename != null) return sourceFilename;
        return modContainer.getName();
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public void setTable(ASMDataTable table) {
        this.table = table;
    }

    public void sendToTable(ASMDataTable table) {
        setTable(table);
        for (ASMModParserV2 asmData : asmDataCollection) {
            addClassEntry(asmData.getClassEntry());
            asmData.sendToTable(table, this);
        }
    }
}
