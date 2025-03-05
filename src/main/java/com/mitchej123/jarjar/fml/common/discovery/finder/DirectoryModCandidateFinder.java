/*
 * Note: Adapted from Fabric's DirectoryModCandidateFinder and is dual licensed under the LGPL v3.0 and Apache 2.0 licenses.
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

package com.mitchej123.jarjar.fml.common.discovery.finder;
import com.mitchej123.jarjar.fml.common.LoaderUtil;
import com.mitchej123.jarjar.fml.relauncher.CoreModManagerV2;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FileListHelper;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DirectoryModCandidateFinder implements ModCandidateFinder {
    private static final Logger LOGGER = LogManager.getLogger("DirectoryModCandidateFinder");
	private final Path pathToSearch;
	private final boolean requiresRemap;
    private final File[] additionalMods;

    public DirectoryModCandidateFinder(Path path, boolean requiresRemap) {
        this(path, new File[0], requiresRemap);
    }

	public DirectoryModCandidateFinder(Path path, File[] additionalMods, boolean requiresRemap) {
		this.pathToSearch = LoaderUtil.normalizePath(path);
        this.additionalMods = FileListHelper.sortFileList(additionalMods);
		this.requiresRemap = requiresRemap;
	}

	@Override
	public void findCandidates(ModCandidateConsumer out) {
        LOGGER.info("Searching for mods in {}", pathToSearch);
		if (!Files.exists(pathToSearch)) {
			try {
				Files.createDirectory(pathToSearch);
				return;
			} catch (IOException e) {
				throw new RuntimeException("Could not create directory " + pathToSearch, e);
			}
		}

		if (!Files.isDirectory(pathToSearch)) {
			throw new RuntimeException(pathToSearch + " is not a directory!");
		}
        final List<Path> paths = new ArrayList<>();
        paths.add(pathToSearch);

        final Path versionSpecificModsDir = pathToSearch.resolve(FMLInjectionData.mccversion);
        if (versionSpecificModsDir.toFile().isDirectory()) {
            FMLLog.info("Also searching %s for mods", versionSpecificModsDir);
            paths.add(versionSpecificModsDir);
        }

        try {
            for (Path path : paths) {
                Files.walkFileTree(
                    path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<>() {

                        @Override
                        public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) {
                            if (isValidFile(file)) {
                                out.accept(file, requiresRemap);
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });
            }
            for(File additionalMod : additionalMods) {
                final Path additionalModPath = LoaderUtil.normalizePath(additionalMod.toPath());
                if (isValidFile(additionalModPath)) {
                    out.accept(LoaderUtil.normalizePath(additionalMod.toPath()), requiresRemap);
                }
            }
		} catch (IOException e) {
			throw new RuntimeException("Exception while searching for mods in '" + pathToSearch + "'!", e);
		}
	}

    private static Set<String> disabledFiles;

    @SuppressWarnings("unchecked")
    public static Set<String> getDisabledFiles() {
        if(disabledFiles == null) {
            disabledFiles = (Set<String>)Launch.blackboard.get(CoreModManagerV2.DISABLED_FILES);
        }
        return disabledFiles;
    }

	static boolean isValidFile(Path path) {
		/*
		 * Valid if:
		 *  - It's a jar file
		 *  - It wasn't disabled during coremod loading
		 *  - It's not hidden
		 */

		if (!Files.isRegularFile(path)) return false;

		try {
			if (Files.isHidden(path)) return false;
		} catch (IOException e) {
			return false;
		}

		final String fileName = path.getFileName().toString();

		return fileName.endsWith(".jar") && !fileName.startsWith(".") && !getDisabledFiles().contains(fileName);
	}
}
