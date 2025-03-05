/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.mitchej123.jarjar.fml.common.discovery.asm;

import com.google.common.collect.Sets;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ModCandidate;
import cpw.mods.fml.common.discovery.asm.ASMModParser;
import org.spongepowered.libraries.com.google.common.base.MoreObjects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

public class ASMModParserV2 extends ASMModParser {

    private final String classEntry;
    private Set<String> interfaces;


    public ASMModParserV2(InputStream stream, String classEntry) throws IOException {
        super(stream);
        this.classEntry = classEntry;
    }

    public String getClassEntry() {
        return classEntry;
    }


    public void beginNewTypeName(String typeQName, int classVersion, String superClassQName, String[] interfaces) {
        super.beginNewTypeName(typeQName, classVersion, superClassQName);
        if(this.interfaces == null) {
            this.interfaces = Sets.newHashSet();
        }
        Collections.addAll(this.interfaces, interfaces);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper("ASMAnnotationDiscoverer")
            .add("className", getASMType().getClassName())
            .add("classVersion", getClassVersion())
            .add("superName", getASMSuperType().getClassName())
            .add("annotations", getAnnotations()).toString();
    }


    @Override
    public void sendToTable(ASMDataTable table, ModCandidate candidate) {
        super.sendToTable(table, candidate);

        for (String intf : interfaces) {
            table.addASMData(candidate, intf, getASMType().getInternalName(), null, null);
        }
    }
}
