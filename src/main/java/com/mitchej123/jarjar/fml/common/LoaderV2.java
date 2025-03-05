package com.mitchej123.jarjar.fml.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mitchej123.jarjar.discovery.ModCandidateV2Sorter;
import com.mitchej123.jarjar.discovery.ParallellModDiscoverer;
import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import cpw.mods.fml.common.CertificateHelper;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.InjectedModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderException;
import cpw.mods.fml.common.LoaderState;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModAPIManager;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ModDiscoverer;
import cpw.mods.fml.common.event.FMLLoadEvent;
import cpw.mods.fml.common.functions.ModIdFunction;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("unused")
public class LoaderV2 extends Loader {
    // Needed because people reflect looking for this
    private LoadController modController;

    public static final Comparator<@Nullable ModContainerWrapper> MOD_COMPARATOR = Comparator.nullsFirst(Comparator.comparing(
        ModContainerWrapper::getId,
        String.CASE_INSENSITIVE_ORDER).thenComparing(ModContainerWrapper::getVersion).thenComparing(ModContainerWrapper::getNestLevel));

    public LoaderV2() {
        super();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void loadMods() {
        progressBar = ProgressManager.push("Loading", 7);

        progressBar.step("Constructing Mods");
        initializeLoader();
        mods = Lists.newArrayList();
        namedMods = Maps.newHashMap();
        this.modController = super.modController = new LoadController(this);
        modController.transition(LoaderState.LOADING, false);

        long startTime = System.nanoTime();
        discoverer = identifyMods();
//        discoverer = super.identifyMods();
        long endTime = System.nanoTime();
        FMLLog.fine("Mod identification took %.2f seconds", (endTime - startTime) / 1e9);

        ModAPIManager.INSTANCE.manageAPI(modClassLoader, discoverer);
        disableRequestedMods(); // Loader V1
        modController.distributeStateMessage(FMLLoadEvent.class);
        sortModList();  // Loader V1
        ModAPIManager.INSTANCE.cleanupAPIContainers(modController.getActiveModList());
        ModAPIManager.INSTANCE.cleanupAPIContainers(mods);
        mods = ImmutableList.copyOf(mods);
        for (File nonMod : discoverer.getNonModLibs()) {
            if (nonMod.isFile()) {
                FMLLog.info(
                    "FML has found a non-mod file %s in your mods directory. It will now be injected into your classpath. This could severe stability issues, it should be removed if possible.",
                    nonMod.getName());
                try {
                    modClassLoader.addFile(nonMod);
                } catch (MalformedURLException e) {
                    FMLLog.log(Level.ERROR, e, "Encountered a weird problem with non-mod file injection : %s", nonMod.getName());
                }
            }
        }
        modController.transition(LoaderState.CONSTRUCTING, false);
        modController.distributeStateMessage(LoaderState.CONSTRUCTING, modClassLoader, discoverer.getASMTable(), reverseDependencies);

        List<ModContainer> modsCopy = Lists.newArrayList();
        modsCopy.addAll(getActiveModList());
        modsCopy.sort(Comparator.comparing(ModContainer::getModId));


        FMLLog.fine("Mod signature data");
        FMLLog.fine(" \tValid Signatures:");
        for (ModContainer mod : getActiveModList()) {
            if (mod.getSigningCertificate() != null) FMLLog.fine(
                "\t\t(%s) %s\t(%s\t%s)\t%s",
                CertificateHelper.getFingerprint(mod.getSigningCertificate()),
                mod.getModId(),
                mod.getName(),
                mod.getVersion(),
                mod.getSource().getName());
        }
        FMLLog.fine(" \tMissing Signatures:");
        for (ModContainer mod : getActiveModList()) {
            if (mod.getSigningCertificate() == null)
                FMLLog.fine("\t\t%s\t(%s\t%s)\t%s", mod.getModId(), mod.getName(), mod.getVersion(), mod.getSource().getName());
        }
        if (getActiveModList().isEmpty()) {
            FMLLog.fine("No user mod signature data found");
        }
        progressBar.step("Initializing mods Phase 1");
        modController.transition(LoaderState.PREINITIALIZATION, false);
    }

    /**
     * The primary loading code
     * <p>
     * The found resources are first loaded into the {@link #modClassLoader} (always) then scanned for class resources matching the specification above.
     * <p>
     * If they provide the {@link Mod} annotation, they will be loaded as "FML mods"
     * <p>
     * Finally, if they are successfully loaded as classes, they are then added to the available mod list.
     */
    @Override
    public ModDiscoverer identifyMods() {
        // Add in the MCP mod container
        mods.add(new InjectedModContainer(mcp, new File("minecraft.jar")));
        for (String cont : injectedContainers) {
            final ModContainer mc;
            try {
                mc = (ModContainer) Class.forName(cont, true, modClassLoader).getConstructor().newInstance();
            } catch (Exception e) {
                FMLLog.log(Level.ERROR, e, "A problem occurred instantiating the injected mod container %s", cont);
                throw new LoaderException(e);
            }
            mods.add(new InjectedModContainer(mc, mc.getSource()));
        }
        ParallellModDiscoverer discoverer = new ParallellModDiscoverer(canonicalModsDir, modClassLoader);
        discoverer.discoverMods();

        final ASMDataTable dataTable = discoverer.getASMTable();

        final List<ModCandidateV2> modCandidates = discoverer.getModCandidates();
        final List<File> nonModLibs = discoverer.getNonModLibs();

        modCandidates.sort(Comparator.comparing(ModCandidateV2::getID, String.CASE_INSENSITIVE_ORDER));
        // Build up the list of potential mods
        final List<ModContainerWrapper> modList = Lists.newArrayList();
        for (ModCandidateV2 candidate : modCandidates) {
            try {

                final List<ModContainerWrapper> mods = candidate.getWrappedMods();
                if (mods.isEmpty() && !candidate.isClasspath()) {
                    nonModLibs.add(candidate.getModContainer());
                } else {
                    modList.addAll(mods);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // TODO: Group mods by file and ensure that all mods in a file get ignored if one is marked as ignored
        // Maybe run the resolver on the candidates instead of the mods....
        final Optional<List<ModContainerWrapper>> resolvedMods = new ModCandidateV2Sorter<>(modList, null).resolve();
        if (!resolvedMods.isPresent()) {
            FMLRelaunchLog.log(Level.ERROR, "There was a critical error during mod resolution, check the log for details");
            throw new RuntimeException("There was a critical error during mod resolution");
        }

        final Set<ModCandidateV2> uniqueCandidates = new ReferenceOpenHashSet<>();
        for (ModContainerWrapper wrapper : resolvedMods.get()) {
            final ModContainer mod = wrapper.mod();
            uniqueCandidates.add(wrapper.candidate());
            dataTable.addContainer(mod);
            mods.add(mod);
        }
        for (ModCandidateV2 candidate : uniqueCandidates) {
            candidate.sendToTable(dataTable);
        }

        identifyDuplicates(mods);
        namedMods = Maps.uniqueIndex(mods, new ModIdFunction());
        FMLLog.info("Forge Mod Loader has identified %d mod%s to load", mods.size(), mods.size() != 1 ? "s" : "");
        return discoverer;

    }
}
