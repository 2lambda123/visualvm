/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualvm.jvmstat;

import org.graalvm.visualvm.jvmstat.application.JvmstatApplicationProvider;
import org.graalvm.visualvm.jvmstat.application.PropertiesImpl;
import org.graalvm.visualvm.tools.jvmstat.JvmJvmstatModelFactory;
import org.graalvm.visualvm.tools.jvmstat.JvmstatModelFactory;
import org.openide.modules.ModuleInstall;

/**
 * Manages a module's lifecycle. Remember that an installer is optional and
 * often not needed at all.
 */
public class Installer extends ModuleInstall {
    
    public void restored() {
        JvmJvmstatModelFactory factory = JvmJvmstatModelFactory.getDefault();
        
        JvmstatModelFactory.getDefault().registerProvider(new JvmstatModelProvider());
        factory.registerProvider(new JvmJvmstatModelProvider());
        factory.registerProvider(new JRockitJvmJvmstatModelProvider());
        factory.registerProvider(new OracleJRockitJvmJvmstatModelProvider());
        JvmstatApplicationProvider.register();
        PropertiesImpl.initialize();
    }
    
}
