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

import cpw.mods.fml.common.discovery.asm.ASMModParser;
import cpw.mods.fml.common.discovery.asm.ModClassVisitor;

public class ModClassVisitorV2 extends ModClassVisitor {

    private final ASMModParserV2 discovererV2;

    public ModClassVisitorV2(ASMModParser discoverer) {
        super(discoverer);
        if(discoverer instanceof ASMModParserV2 discovererV2) {
            this.discovererV2 = discovererV2;
        } else {
            this.discovererV2 = null;
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if(discovererV2 != null) {
            discovererV2.beginNewTypeName(name, version, superName, interfaces);
        } else {
            super.visit(version, access, name, signature, superName, interfaces);
        }
    }

}
