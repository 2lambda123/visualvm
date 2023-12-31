/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.graalvm.visualvm.lib.jfluid.heap;

import java.util.List;


/**
 *
 * @author Tomas Hurka
 */
class ObjectArrayDump extends ArrayDump implements ObjectArrayInstance {
    //~ Constructors -------------------------------------------------------------------------------------------------------------

    ObjectArrayDump(ClassDump cls, long offset) {
        super(cls, offset);
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public long getSize() {
        return dumpClass.classDumpSegment.getArraySize((byte)HprofHeap.OBJECT, getLength());
    }

    public List<Instance> getValues() {
        HprofByteBuffer dumpBuffer = dumpClass.getHprofBuffer();
        HprofHeap heap = dumpClass.getHprof();

        return new ObjectArrayLazyList(heap, dumpBuffer, getLength(), getOffset());
    }

    public List<ArrayItemValue> getItems() {
        return new ObjectArrayValuesLazyList(dumpClass, getLength(), fileOffset);
    }

    long getOffset() {
        int idSize = dumpClass.getHprofBuffer().getIDSize();

        return fileOffset + 1 + idSize + 4 + 4 + idSize;
    }
}
