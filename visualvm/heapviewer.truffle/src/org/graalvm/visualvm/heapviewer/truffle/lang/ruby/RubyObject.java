/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualvm.heapviewer.truffle.lang.ruby;

import org.graalvm.visualvm.heapviewer.truffle.dynamicobject.DynamicObject;
import org.graalvm.visualvm.heapviewer.truffle.lang.javascript.JavaScriptLanguage;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.Instance;
import org.graalvm.visualvm.lib.jfluid.heap.JavaClass;
import org.graalvm.visualvm.lib.profiler.heapwalk.details.spi.DetailsUtils;

/**
 *
 * @author Jiri Sedlacek
 */
class RubyObject extends DynamicObject {
    
    RubyObject(Instance instance) {
        super(instance);
    }
    
    RubyObject(String type, Instance instance) {
        super(type, instance);
    }
    
    boolean isRubyObject() {
        return isRubyLangId(getLanguageId());
    }

    @Override
    protected String computeType() {
        Instance metaClass = (Instance) getInstance().getValueOfField("metaClass");
        if (metaClass == null) {
            return super.computeType();
        }
        return DetailsUtils.getInstanceFieldString(metaClass, "nonSingletonClass");
    }
    
    static boolean isRubyObject(Instance instance) {
        return DynamicObject.isDynamicObject(instance) &&
               isRubyLangId(DynamicObject.getLanguageId(instance));
    }
    
    private static boolean isRubyLangId(JavaClass langIdClass) {
        String className = langIdClass.getName();

        return RubyHeapFragment.RUBY_LANG_ID.equals(className) || RubyHeapFragment.RUBY_LANG_ID1.equals(className);
    }

}
