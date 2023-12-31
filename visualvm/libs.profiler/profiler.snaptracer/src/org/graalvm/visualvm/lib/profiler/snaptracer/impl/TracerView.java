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

package org.graalvm.visualvm.lib.profiler.snaptracer.impl;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import javax.swing.*;
import org.graalvm.visualvm.lib.jfluid.client.ClientUtils;
import org.graalvm.visualvm.lib.jfluid.results.cpu.CPUResultsSnapshot;
import org.graalvm.visualvm.lib.jfluid.results.cpu.PrestimeCPUCCTNode;
import org.graalvm.visualvm.lib.jfluid.utils.Wildcards;
import org.graalvm.visualvm.lib.profiler.LoadedSnapshot;
import org.graalvm.visualvm.lib.profiler.ResultsManager;
import org.graalvm.visualvm.lib.profiler.SampledCPUSnapshot;
import org.graalvm.visualvm.lib.profiler.actions.CompareSnapshotsAction;
import org.graalvm.visualvm.lib.profiler.api.GoToSource;
import org.graalvm.visualvm.lib.profiler.snaptracer.TracerPackage;
import org.graalvm.visualvm.lib.profiler.snaptracer.impl.timeline.TimelineSupport;
import org.graalvm.visualvm.lib.profiler.v2.ProfilerFeature;
import org.graalvm.visualvm.lib.profiler.v2.ProfilerSession;
import org.graalvm.visualvm.lib.ui.components.HTMLTextArea;
import org.graalvm.visualvm.lib.ui.components.HTMLTextAreaSearchUtils;
import org.graalvm.visualvm.lib.ui.cpu.SnapshotCPUView;
import org.graalvm.visualvm.lib.ui.results.DataView;
import org.graalvm.visualvm.lib.ui.swing.ExportUtils;
import org.graalvm.visualvm.lib.ui.swing.FilterUtils;
import org.graalvm.visualvm.lib.ui.swing.SearchUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Jiri Sedlacek
 * @author Tomas Hurka
 */
public final class TracerView {
    
    private final TracerModel model;
    private final TracerController controller;
    private LoadedSnapshot lsF;
    private TimelineView timelineView;
    private SnapshotView snapshotView;
    
    public TracerView(TracerModel model, TracerController controller) {
        this.model = model;
        this.controller = controller;
    }

    public JComponent createComponent() {
        
        final JPanel component = new JPanel(new BorderLayout());
        component.setOpaque(false);

        // create timeline support
        timelineView = new TimelineView(model);
        JPanel timelinePanel = new JPanel(new BorderLayout());
        timelinePanel.setOpaque(false);
        timelinePanel.add(timelineView.getView(), BorderLayout.CENTER);
        timelinePanel.add(new JSeparator(), BorderLayout.SOUTH);
        
        // add the timeline component to the UI
        final JPanel container = new JPanel(null) {
            public void doLayout() {
                Component[] components = getComponents();
                for (Component component : components)
                    component.setBounds(0, 0, getWidth(), getHeight());
            }
            public Dimension getPreferredSize() {
                return getComponent(getComponentCount() - 1).getPreferredSize();
            }
            public Dimension getMinimumSize() {
                return getComponent(getComponentCount() - 1).getMinimumSize();
            }
            public Dimension getMaximumSize() {
                return getComponent(getComponentCount() - 1).getMaximumSize();
            }
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }
        };
        JPanel glass = new JPanel(null);
        glass.setOpaque(false);
        glass.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        glass.addMouseListener(new MouseAdapter() {});
        glass.addMouseMotionListener(new MouseMotionAdapter() {});
        glass.addKeyListener(new KeyAdapter() {});
        container.add(glass); // Consumes event
        container.add(timelinePanel);
        
        component.add(container, BorderLayout.NORTH);

        TracerSupportImpl.getInstance().perform(new Runnable() {
            public void run() {
                // add all registered probes to the timeline
                initProbes();
                // setup the timeline - zoom according to snapshot data
                initTimeline();
                // load the probes data
                initData(component, container);
                // init required listeners - timeline selection
                initListeners(component);
            }
        });
        
        ActionMap map = component.getActionMap();
        
        map.put(FilterUtils.FILTER_ACTION_KEY, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (snapshotView != null && snapshotView.isShowing())
                    snapshotView.getActionMap().get(FilterUtils.FILTER_ACTION_KEY).actionPerformed(e);
            }
        });
        
        map.put(SearchUtils.FIND_ACTION_KEY, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (snapshotView != null && snapshotView.isShowing())
                    snapshotView.getActionMap().get(SearchUtils.FIND_ACTION_KEY).actionPerformed(e);
            }
        });

        return component;
    }
    
    private void initProbes() {
        List<TracerPackage> packages =
            TracerSupportImpl.getInstance().getPackages(model.getSnapshot());
        for (TracerPackage p : packages)
            model.addDescriptors(p, p.getProbeDescriptors());
    }

    private void initTimeline() {
        TimelineSupport support = model.getTimelineSupport();
        long start = model.firstTimestamp();
        if (start == -1) return;
        long end = model.lastTimestamp();
        if (end == -1) return;
        support.dataLoadingStarted(end - start);
    }

    @NbBundle.Messages("MSG_LoadingSnapshot=Loading snapshot...")
    private void initData(final JPanel component, final JPanel container) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JLabel progress = new JLabel(Bundle.MSG_LoadingSnapshot(), JLabel.CENTER);
                progress.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                addContents(component, progress);

                TracerSupportImpl.getInstance().perform(new Runnable() {
                    public void run() {
                        controller.performSession();
                        controller.performAfterSession(new Runnable() {
                            public void run() {
                                TimelineSupport support = model.getTimelineSupport();
                                support.dataLoadingFinished();
                                support.selectAll();

                                SwingUtilities.invokeLater(new Runnable() {
                                    public void run() {
                                        timelineView.updateActions();
                                    }
                                });

                                 // Enable events for timeline
                                component.remove(container);
                                component.add(container.getComponent(1), BorderLayout.NORTH);
                                component.revalidate();
                                component.repaint();
                            }
                        });
                    }
                });
            }
        });
    }

    @NbBundle.Messages("MSG_ProcessingSelection=Processing selection...")
    private void initListeners(final JPanel component) {
        final TimelineSupport support = model.getTimelineSupport();
        support.addSelectionListener(
                new TimelineSupport.SelectionListener() {
            public void intervalsSelectionChanged() {}
            public void indexSelectionChanged() {
                final int startIndex = Math.min(support.getStartIndex(), support.getEndIndex());
                final int endIndex = Math.max(support.getStartIndex(), support.getEndIndex());
                JLabel progress = new JLabel(Bundle.MSG_ProcessingSelection(), JLabel.CENTER); // NOI18N
                addContents(component, progress);

                controller.performAfterSession(new Runnable() {
                    public void run() {
                        if (startIndex == endIndex) displayThreadDump(component, startIndex);
                        else displaySnapshot(component, startIndex, endIndex);
                    }
                });
            }

            public void timeSelectionChanged(boolean timestampsSelected,
                                             boolean justHovering) {}
        });
    }
    
    private void displaySnapshot(final JPanel p, final int s1, final int s2) {
        LoadedSnapshot ls = null;
        try {
            ls = model.getSnapshot().getCPUSnapshot(s1, s2);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        lsF = ls;

        if (lsF != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    CPUResultsSnapshot s = (CPUResultsSnapshot)lsF.getSnapshot();
                    if (snapshotView == null) {
                        CompareSnapshotsAction aCompare = new CompareSnapshotsAction(lsF);
                        ResultsManager.SnapshotHandle handle = new ResultsManager.SnapshotHandle() {
                            public LoadedSnapshot getSnapshot() { return lsF; }
                        };
                        ExportUtils.Exportable exporter = ResultsManager.getDefault().createSnapshotExporter(handle);
                        snapshotView = new SnapshotView(s, aCompare, exporter);
                        aCompare.setPerformer(new CompareSnapshotsAction.Performer() {
                            public void compare(LoadedSnapshot snapshot) {
                                snapshotView.setRefSnapshot((CPUResultsSnapshot)snapshot.getSnapshot());
                            }
                        });
                    } else {
                        snapshotView.setData(s);
                    }
                    addContents(p, snapshotView);
                }
            });
        }
    }

    private void displayThreadDump(final JPanel p, final int s) {
        String td = null;
        try {
            td = model.getSnapshot().getThreadDump(s);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        final String tdF = td;

        if (tdF != null) {
            lsF = null;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    HTMLTextArea a = new HTMLTextArea(tdF) {
                        protected void showURL(URL url) {
                            if (url == null) return;
                            String urls = url.toString();
                            TracerView.this.showURL(urls);
                        }
                    };
                    a.setCaretPosition(0);
                    JScrollPane sp = new JScrollPane(a);
                    sp.setBorder(BorderFactory.createEmptyBorder());
                    sp.setViewportBorder(BorderFactory.createEmptyBorder());
                    JPanel pp = new JPanel(new BorderLayout());
                    pp.add(sp, BorderLayout.CENTER);
                    pp.add(HTMLTextAreaSearchUtils.createSearchPanel(a), BorderLayout.SOUTH);
                    addContents(p, pp);
                }
            });
        }
    }

    private void addContents(JComponent container, JComponent contents) {
        BorderLayout layout = (BorderLayout)container.getLayout();
        Component oldContents = layout.getLayoutComponent(BorderLayout.CENTER);
        if (oldContents != contents) {
            if (oldContents != null) container.remove(oldContents);
            container.add(contents, BorderLayout.CENTER);
            contents.requestFocusInWindow();
            container.revalidate();
            container.repaint();
        }
    }

    void showURL(String urls) {
        if (urls.startsWith(SampledCPUSnapshot.OPEN_THREADS_URL)) {
            urls = urls.substring(SampledCPUSnapshot.OPEN_THREADS_URL.length());
            String parts[] = urls.split("\\|"); // NOI18N
            String className = parts[0];
            String method = parts[1];
            int linenumber = Integer.parseInt(parts[2]);
            GoToSource.openSource(null, className, method, linenumber);
        }
    }
    
    private final class SnapshotView extends SnapshotCPUView {
        
        SnapshotView(CPUResultsSnapshot snapshot, Action compare, ExportUtils.Exportable exporter) {
            super(snapshot, true, null, compare, null, exporter);
        }
        
        void setData(CPUResultsSnapshot snapshot) {
            super.setSnapshot(snapshot, true);
        }
        
        protected boolean profileMethodEnabled() {
            return false;
        }
        
        protected boolean profileMethodSupported() {
            return ProfilerFeature.Registry.hasProviders();
        }
    
        protected boolean profileClassSupported() {
            return ProfilerFeature.Registry.hasProviders();
        }

        protected boolean showSourceSupported() {
            return GoToSource.isAvailable();
        }
        
        protected void showSource(ClientUtils.SourceCodeSelection value) {
            String className = value.getClassName();
            String methodName = value.getMethodName();
            String methodSig = value.getMethodSignature();
            GoToSource.openSource(null, className, methodName, methodSig);
        }
        
        @NbBundle.Messages({
            "LBL_ProfileClass=Profile Class",
            "LBL_ProfileMethod=Profile Method"                
        })
        protected void selectForProfiling(final ClientUtils.SourceCodeSelection value) {
            RequestProcessor.getDefault().post(new Runnable() {
                public void run() {
                    String name = Wildcards.ALLWILDCARD.equals(value.getMethodName()) ?
                                  Bundle.LBL_ProfileClass() : Bundle.LBL_ProfileMethod();
                    ProfilerSession.findAndConfigure(Lookups.fixed(value), null, name);
                }
            });
        }
        
        protected void customizeNodePopup(DataView invoker, JPopupMenu popup, Object value, ClientUtils.SourceCodeSelection userValue) {
            if (value instanceof PrestimeCPUCCTNode) {
                popup.add(new FindMethodAction((PrestimeCPUCCTNode)value));
                popup.addSeparator();
            }
        }
        
    }
    
    private class FindMethodAction extends AbstractAction {
        
        private final PrestimeCPUCCTNode node;
        
        @NbBundle.Messages("LBL_FindMethod=Select Intervals")
        private FindMethodAction(PrestimeCPUCCTNode node) {
            super(Bundle.LBL_FindMethod());
            this.node = node;
            setEnabled(isRegular(node));
        }
        
        @NbBundle.Messages("LBL_SelectingIntervals=Selecting method intervals...")
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            RequestProcessor.getDefault().post(new Runnable() {
                public void run() {
                    ProgressHandle pHandle = null;
                    try {
                        pHandle = ProgressHandle.createHandle(Bundle.LBL_SelectingIntervals());
                        pHandle.setInitialDelay(0);
                        pHandle.start();
                        
                        List<Integer> ints = model.getIntervals(node);
                        assert ints.size() % 2 == 0;
                        final Iterator<Integer> iter = ints.iterator();
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                TimelineSupport support = model.getTimelineSupport();
                                support.resetSelectedIntervals();
                                while (iter.hasNext()) {
                                    int start = iter.next();
                                    int stop  = iter.next();
                                    support.selectInterval(start, stop);
                                }
                                support.selectedIntervalsChanged();
                            }
                        });
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    } finally {
                        if (pHandle != null) pHandle.finish();
                    }
                }
            });
        }

        private boolean isRegular(PrestimeCPUCCTNode n) {
            return n.getThreadId() != -1 && n.getMethodId() != 0 && !n.isFiltered();
        }

    }
}
