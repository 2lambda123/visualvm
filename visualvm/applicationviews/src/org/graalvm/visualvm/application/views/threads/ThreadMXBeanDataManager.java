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

package org.graalvm.visualvm.application.views.threads;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.graalvm.visualvm.application.Application;
import org.graalvm.visualvm.application.views.ApplicationThreadsResponseProvider;
import org.graalvm.visualvm.application.views.ApplicationThreadsResponseProvider.ThreadMonitoredDataResponseProvider;
import org.graalvm.visualvm.core.VisualVM;
import org.graalvm.visualvm.lib.jfluid.client.MonitoredData;
import org.graalvm.visualvm.lib.jfluid.global.CommonConstants;
import org.graalvm.visualvm.lib.jfluid.wireprotocol.MonitoredNumbersResponse;
import org.openide.util.Lookup;

/**
 *
 * @author Tomas Hurka
 */
class ThreadMXBeanDataManager extends VisualVMThreadsDataManager {

    private static final long[] dummyLong = new long[0];
    private static final Logger LOGGER = Logger.getLogger(ThreadMXBeanDataManager.class.getName());
    static final String DEADLOCK_PROP = "Deadlock";   // NOI18N
    private static ApplicationThreadsResponseProvider appRespProvider = Lookup.getDefault().lookup(ApplicationThreadsResponseProvider.class);
    
    private ThreadMXBean threadBean;
    private Set<Long> threadIdSet = new HashSet<>();
    private boolean refreshRunning;
    private DeadlockDetector deadlockDetector;
    private PropertyChangeSupport changeSupport;
    private long[] deadlockThreadIds;
    private ThreadMonitoredDataResponseProvider threadMonProvider;
    private int threadCount, daemonThreadCount;
    
            
    ThreadMXBeanDataManager(Application app, ThreadMXBean tb) {
        this(tb);
        if (appRespProvider != null) {
            threadMonProvider = appRespProvider.getMonitoredDataResponseProvider(app, tb);
        }
    }

    ThreadMXBeanDataManager(ThreadMXBean tb) {
        threadBean = tb;
        deadlockDetector = new DeadlockDetector(tb);
        changeSupport = new PropertyChangeSupport(this);
    }

    // Non-blocking call for general usage
    void refreshThreadsAsync() {
        synchronized (this) {
            if (refreshRunning) return;
            refreshRunning = true;
        }
        VisualVM.getInstance().runTask(new Runnable() {
            public void run() {
                long[] oldDeadlockThreadIds = deadlockThreadIds;
                
                refreshThreadsSync();
                deadlockThreadIds = deadlockDetector.detectDeadlock();
                if (deadlockThreadIds != null && !Arrays.equals(oldDeadlockThreadIds,deadlockThreadIds)) {
                    changeSupport.firePropertyChange(DEADLOCK_PROP,oldDeadlockThreadIds,deadlockThreadIds);
                }
                synchronized (ThreadMXBeanDataManager.this) {
                   refreshRunning = false; 
                }
            }
        });
    }

    // Blocking call used to save application snapshot for not opened application
    void refreshThreadsSync() {
        try {
            MonitoredNumbersResponse resp = null;
            if (threadMonProvider != null) {
                resp = threadMonProvider.createThreadMonitoredDataResponse();
            }
            if (resp == null) {
                ThreadMonitoredDataResponse tresp = new ThreadMonitoredDataResponse();
                tresp.fillInThreadData();
                resp = tresp;
            }
            threadCount = threadBean.getThreadCount();
            daemonThreadCount = threadBean.getDaemonThreadCount();
            final MonitoredData monitoredData = MonitoredData.getMonitoredData(resp);
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    // must run in AWT
                    processData(monitoredData);
                }
            });
        } catch (Exception ex) {
            LOGGER.throwing(ThreadMXBeanDataManager.class.getName(), "refreshThreads", ex); // NOI18N
        }
    }

    int getDaemonThreadCount() {
        return daemonThreadCount;
    }

    int getThreadCount() {
        return threadCount;
    }
    
    void addPropertyChangeListener(PropertyChangeListener l) {
         changeSupport.addPropertyChangeListener(l);
    }
    
    void removePropertyChangeListener(PropertyChangeListener l) {
        changeSupport.removePropertyChangeListener(l);
    }
    
    void cleanup() {
        if (threadMonProvider != null) threadMonProvider.cleanup();
    }

    class ThreadMonitoredDataResponse extends MonitoredNumbersResponse {

        ThreadMonitoredDataResponse() {
            super(dummyLong, CommonConstants.SERVER_RUNNING, CommonConstants.SERVER_PROGRESS_INDETERMINATE);
            setGCstartFinishData(dummyLong, dummyLong);
        }

        private void fillInThreadData() {
            long[] currentThreadIds = threadBean.getAllThreadIds();
            ThreadInfo[] threadInfos = threadBean.getThreadInfo(currentThreadIds, 1);
            Set<Long> currentIdSet = new HashSet<>(currentThreadIds.length * 4 / 3);
            int nThreads = 0;
            long timeStamps[] = {System.currentTimeMillis()};
            int maxThreads = currentThreadIds.length + threadIdSet.size();
            int tids[] = new int[maxThreads];
            byte states[] = new byte[maxThreads];

            int nNewThreads = 0;
            int newThreadsId[] = new int[currentThreadIds.length];
            String[] newThreadsNames = new String[currentThreadIds.length];
            String[] newThreadsClasses = new String[currentThreadIds.length];

            for (int i = 0; i < currentThreadIds.length; i++) {
                ThreadInfo tinfo = threadInfos[i];
                long threadId = currentThreadIds[i];
                Long threadIdLong;

                if (tinfo == null) {
                    continue;
                }
                threadIdLong = Long.valueOf(threadId);
                currentIdSet.add(threadIdLong);
                tids[nThreads] = (int) threadId;
                states[nThreads] = getState(tinfo);
                nThreads++;

                if (!threadIdSet.remove(threadIdLong)) { // New Thread
                    newThreadsId[nNewThreads] = (int) threadId;
                    newThreadsNames[nNewThreads] = tinfo.getThreadName();
                    newThreadsClasses[nNewThreads] = "";
                    nNewThreads++;
                }
            }
            // set remaining threads as terminated
            for (Long elem : threadIdSet) {
                tids[nThreads] = elem.intValue();
                states[nThreads] = CommonConstants.THREAD_STATUS_ZOMBIE;
                nThreads++;
            }
            threadIdSet = currentIdSet;
            setDataOnNewThreads(nNewThreads, newThreadsId, newThreadsNames, newThreadsClasses);
            setDataOnThreads(nThreads, timeStamps.length, tids, timeStamps, states);
        }

        byte getState(ThreadInfo threadInfo) {
            Thread.State state = threadInfo.getThreadState();
            switch (state) {
                case BLOCKED:
                    return CommonConstants.THREAD_STATUS_MONITOR;
                case RUNNABLE:
                    return CommonConstants.THREAD_STATUS_RUNNING;
                case TIMED_WAITING:
                case WAITING:
                    StackTraceElement[] stack = threadInfo.getStackTrace();
                    if (stack.length>0) {
                        StackTraceElement el = stack[0];
                        if (isSleeping(el)) return CommonConstants.THREAD_STATUS_SLEEPING;
                        if (isParked(el)) return CommonConstants.THREAD_STATUS_PARK;
                    }
                    return CommonConstants.THREAD_STATUS_WAIT;
                case TERMINATED:
                case NEW:
                    return CommonConstants.THREAD_STATUS_ZOMBIE;
            }
            return CommonConstants.THREAD_STATUS_UNKNOWN;
        }

        boolean isSleeping(StackTraceElement element) {
            return Thread.class.getName().equals(element.getClassName()) &&
                    "sleep".equals(element.getMethodName());    // NOI18N
        }

        boolean isParked(StackTraceElement element) {
            String className = element.getClassName();

            if ("jdk.internal.misc.Unsafe".equals(className) || "sun.misc.Unsafe".equals(className)) { // NOI18N
                return "park".equals(element.getMethodName());          // NOI18N
            }
            return false;
        }
    }
}
