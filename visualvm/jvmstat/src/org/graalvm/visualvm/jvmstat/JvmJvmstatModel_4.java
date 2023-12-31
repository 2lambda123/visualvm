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

import org.graalvm.visualvm.application.Application;
import org.graalvm.visualvm.tools.jvmstat.JvmJvmstatModel;
import org.graalvm.visualvm.tools.jvmstat.JvmstatModel;
import org.graalvm.visualvm.tools.jvmstat.MonitoredValue;

/**
 *
 * @author Tomas Hurka
 */
class JvmJvmstatModel_4 extends JvmJvmstatModel {
    private static final String PERM_GEN_PREFIX = "hotspot.gc.generation.2.";   // NOI18N

    JvmJvmstatModel_4(Application app,JvmstatModel stat) {
        super(app,stat);
        initMonitoredVales();
    }

    private void initMonitoredVales() {
      loadedClasses = jvmstat.findMonitoredValueByName("hotspot.rt.cl.classes.loaded"); // NOI18N
      unloadedClasses = jvmstat.findMonitoredValueByName("hotspot.rt.cl.classes.unloaded"); // NOI18N
      applicationTime = jvmstat.findMonitoredValueByName("sun.rt.applicationTime"); // NOI18N
      upTime = jvmstat.findMonitoredValueByName("hotspot.rt.hrt.ticks");    // NOI18N
      MonitoredValue osFrequencyMon = jvmstat.findMonitoredValueByName("hotspot.rt.hrt.frequency"); // NOI18N
      osFrequency = getLongValue(osFrequencyMon);
      genCapacity = jvmstat.findMonitoredValueByPattern("hotspot.gc.generation.[0-9]+.capacity.current");   // NOI18N
      genUsed = jvmstat.findMonitoredValueByPattern("hotspot.gc.generation.[0-9]+.space.[0-9]+.used");  // NOI18N
      genMaxCapacity = getGenerationSum(jvmstat.findMonitoredValueByPattern("hotspot.gc.generation.[0-9]+.capacity.max"));  // NOI18N
    }

    protected String getPermGenPrefix() {
        return PERM_GEN_PREFIX;
    }
    
}
