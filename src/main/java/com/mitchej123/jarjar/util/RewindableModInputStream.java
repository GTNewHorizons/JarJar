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

package com.mitchej123.jarjar.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class RewindableModInputStream extends InputStream {

    private final ByteBuffer buffer;
    private int pos;

    public RewindableModInputStream(InputStream parent) throws IOException { // no parent.close()
        buffer = readMod(parent);

        assert buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.position() == 0;
    }

    public static ByteBuffer readMod(InputStream is) throws IOException {
        int available = is.available();
        boolean availableGood = available > 1;
        byte[] buffer = new byte[availableGood ? available : 30_000];
        int offset = 0;
        int len;

        while ((len = is.read(buffer, offset, buffer.length - offset)) >= 0) {
            offset += len;

            if (offset == buffer.length) {
                if (availableGood) {
                    int val = is.read();
                    if (val < 0) break;

                    availableGood = false;
                    buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, 30_000));
                    buffer[offset++] = (byte) val;
                } else {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
            }
        }

        return ByteBuffer.wrap(buffer, 0, offset);
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void rewind() {
        pos = 0;
    }

    @Override
    public int read() throws IOException {
        if (pos >= buffer.limit()) {
            return -1;
        } else {
            return buffer.get(pos++) & 0xff;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int rem = buffer.limit() - pos;

        if (rem <= 0) {
            return -1;
        } else {
            len = Math.min(len, rem);
            System.arraycopy(buffer.array(), pos, b, off, len);
            pos += len;

            return len;
        }
    }
}
