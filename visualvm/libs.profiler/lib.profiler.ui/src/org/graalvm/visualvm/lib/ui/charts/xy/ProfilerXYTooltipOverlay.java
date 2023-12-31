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

package org.graalvm.visualvm.lib.ui.charts.xy;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import javax.swing.Timer;
import org.graalvm.visualvm.lib.charts.ChartComponent;
import org.graalvm.visualvm.lib.charts.ChartConfigurationListener;
import org.graalvm.visualvm.lib.charts.ChartContext;
import org.graalvm.visualvm.lib.charts.ChartItem;
import org.graalvm.visualvm.lib.charts.ChartOverlay;
import org.graalvm.visualvm.lib.charts.ChartSelectionListener;
import org.graalvm.visualvm.lib.charts.ItemPainter;
import org.graalvm.visualvm.lib.charts.ItemSelection;
import org.graalvm.visualvm.lib.charts.PaintersModel;
import org.graalvm.visualvm.lib.charts.swing.Utils;
import org.graalvm.visualvm.lib.charts.xy.XYItemSelection;

/**
 *
 * @author Jiri Sedlacek
 */
public class ProfilerXYTooltipOverlay extends ChartOverlay implements ActionListener {

    private static final int TOOLTIP_OFFSET = 15;
    private static final int TOOLTIP_MARGIN = 10;
    private static final int TOOLTIP_RESPONSE = 50;
    private static final int ANIMATION_STEPS = 5;

    private ProfilerXYTooltipPainter tooltipPainter;

    private Timer timer;
    private int currentStep;
    private Point mousePosition;
    private Point targetPosition;


    public ProfilerXYTooltipOverlay(final ChartComponent chart,
                                    ProfilerXYTooltipPainter tooltipPainter) {
        if (chart.getSelectionModel() == null)
            throw new NullPointerException("No ChartSelectionModel set for " + chart); // NOI18N

        if (!Utils.forceSpeed()) {
            timer = new Timer(TOOLTIP_RESPONSE / ANIMATION_STEPS, this);
            timer.setInitialDelay(0);
        }

        setLayout(null);

        this.tooltipPainter = tooltipPainter;
        add(tooltipPainter);
        tooltipPainter.setVisible(false);

        chart.getSelectionModel().addSelectionListener(new ChartSelectionListener() {

            public void selectionModeChanged(int newMode, int oldMode) {}

            public void selectionBoundsChanged(Rectangle newBounds, Rectangle oldBounds) {}

            public void highlightedItemsChanged(List<ItemSelection> currentItems,
                List<ItemSelection> addedItems, List<ItemSelection> removedItems) {
                updateTooltip(chart);
            }

            public void selectedItemsChanged(List<ItemSelection> currentItems,
                List<ItemSelection> addedItems, List<ItemSelection> removedItems) {}

        });

        chart.addConfigurationListener(new ChartConfigurationListener.Adapter() {

            public void contentsUpdated(long offsetX, long offsetY,
                                    double scaleX, double scaleY,
                                    long lastOffsetX, long lastOffsetY,
                                    double lastScaleX, double lastScaleY,
                                    int shiftX, int shiftY) {
                updateTooltip(chart);
            }

        });

        chart.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                mousePosition = e.getPoint();
                updateTooltip(chart);
            }
        });
    }

    public final void setPosition(Point p) {
        if (tooltipPainter != null) {
            if (p == null) {
                if (tooltipPainter.isVisible()) tooltipPainter.setVisible(false);
                if (timer != null) timer.stop();
            } else {
                if (!tooltipPainter.isVisible() || timer == null) {
                    tooltipPainter.setVisible(true);
                    tooltipPainter.setLocation(p);
                } else {
                    currentStep = 0;
                    targetPosition = p;
                    timer.restart();
                }
            }
        }
    }

    public final Point getPosition() {
        if (tooltipPainter == null) return null;
        return tooltipPainter.getLocation();
    }

    public void actionPerformed(ActionEvent e) {
        Point currentPosition = tooltipPainter.getLocation();

        currentPosition.x += (targetPosition.x - currentPosition.x) /
                             (ANIMATION_STEPS - currentStep);
        currentPosition.y += (targetPosition.y - currentPosition.y) /
                             (ANIMATION_STEPS - currentStep);
        tooltipPainter.setLocation(currentPosition);

        if (++currentStep == ANIMATION_STEPS) timer.stop();
    }


    private void updateTooltip(ChartComponent chart) {
        if (mousePosition == null) return;

        List<ItemSelection> highlightedItems =
                chart.getSelectionModel().getHighlightedItems();

        XYItemSelection selection = highlightedItems.isEmpty() ? null :
                                    (XYItemSelection)highlightedItems.get(0);

        if (selection == null ||
            selection.getItem().getValuesCount() <= selection.getValueIndex()) {
            setPosition(null);
        } else {
            tooltipPainter.update(highlightedItems);
            tooltipPainter.setSize(tooltipPainter.getPreferredSize());
            setPosition(highlightedItems, chart.getPaintersModel(), chart.getChartContext());
        }
    }

    private void setPosition(List<ItemSelection> selectedItems, PaintersModel paintersModel, ChartContext chartContext) {
        int tooltipX = -1;
        int tooltipY = mousePosition.y;
        for (ItemSelection selection : selectedItems) {
            ChartItem item = selection.getItem();
            ItemPainter painter = paintersModel.getPainter(item);
            Rectangle bounds = Utils.checkedRectangle(
                               painter.getSelectionBounds(selection,
                               chartContext));
            if (tooltipX == -1) tooltipX += bounds.x + bounds.width / 2;
        }

        setPosition(normalizePosition(new Point(tooltipX, tooltipY)));
    }

    private Point normalizePosition(Point basePoint) {
        int w = getWidth();
        int h = getHeight();
        int cw = tooltipPainter.getWidth();
        int ch = tooltipPainter.getHeight();

        basePoint.x += TOOLTIP_OFFSET;
        if (basePoint.x + cw + TOOLTIP_MARGIN > w)
            basePoint.x -= TOOLTIP_OFFSET + cw + TOOLTIP_MARGIN;
        if (basePoint.x < TOOLTIP_OFFSET)
            basePoint.x = TOOLTIP_OFFSET;

        basePoint.y -= ch + TOOLTIP_MARGIN;
        if (basePoint.y + ch + TOOLTIP_MARGIN > h)
            basePoint.y = h - ch - TOOLTIP_MARGIN;
        if (basePoint.y < TOOLTIP_MARGIN)
            basePoint.y = TOOLTIP_MARGIN;

        return basePoint;
    }


    public void paint(Graphics g) {
        if (tooltipPainter == null) return;

        Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
        Rectangle clip = g.getClipBounds();
        if (clip == null) g.setClip(bounds);
        else g.setClip(clip.intersection(bounds));

        super.paint(g);
    }

}
