package DroneSwarmSim.ui;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.model.Zone;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.DefaultCaret;

public class RuntimeGUI extends GUI {
    private static final Color BACKGROUND_DARK = new Color(30, 32, 38);
    private static final Color BACKGROUND_MID = new Color(40, 44, 52);
    private static final Color BACKGROUND_LIGHT = new Color(50, 55, 65);
    private static final Color ACCENT_PRIMARY = new Color(86, 156, 214);
    private static final Color ACCENT_SUCCESS = new Color(78, 154, 106);
    private static final Color TEXT_PRIMARY = new Color(212, 212, 212);
    private static final Color TEXT_SECONDARY = new Color(150, 150, 150);
    private static final Color BORDER_COLOR = new Color(60, 63, 70);

    private static final String ICON_DRONE = "\u2708";
    private static final String ICON_WATER = "\u2614";
    private static final String ICON_LOCATION = "\u2316";
    private static final String ICON_FIRE = "\u2622";
    private static final String ICON_CLOCK = "\u23F1";
    private static final String ICON_FLEET = "\u2699";

    private final JComboBox<String> droneSelector = new JComboBox<>();
    private final Map<Integer, DroneState> droneStates = new HashMap<>();
    private final Map<Integer, Double> droneWaters = new HashMap<>();
    private final Map<Integer, Double> droneBatteries = new HashMap<>();
    private final Map<Integer, Double> droneFuels = new HashMap<>();
    private final Map<Integer, double[]> dronePositions = new HashMap<>();
    private Integer selectedDroneId = null;

    private final JLabel timerLabel = new JLabel("00:00");
    private final JLabel fleetStatusLabel = new JLabel("0 / 0");
    private Timer simulationTimer;
    private long simulationStartMs;

    private final JLabel droneStateLabel = new JLabel("N/A");
    private final JLabel waterLabel = new JLabel("N/A");
    private final JLabel positionLabel = new JLabel("N/A");
    private final JLabel activeFireLabel = new JLabel("0");
    private final JLabel batteryLabel = new JLabel("N/A");
    private final JLabel fuelLabel = new JLabel("N/A");
    private final JTextArea eventLog = new JTextArea();
    private final JTextArea telemetryLog = new JTextArea();
    private final JTextArea queueLog = new JTextArea();
    private final JTextArea historyLog = new JTextArea();
    private final JTextArea metricsLog = new JTextArea();
    private final JPanel faultPanel = new JPanel();
    private final Map<Integer, JLabel> faultLabels = new HashMap<>();
    private final Map<Integer, Timer> faultCountdownTimers = new HashMap<>();
    private int activeFireCount;

    public RuntimeGUI(Map<Integer, Zone> zones) {
        super(zones);
        if (mainWindow == null) {
            return;
        }

        mainWindow.getContentPane().setBackground(BACKGROUND_DARK);

        droneSelector.addItem("Select Drone...");
        styleDroneSelector();
        droneSelector.addActionListener(e -> onDroneSelected());

        JPanel statusPanel = new JPanel(new BorderLayout(12, 0));
        statusPanel.setBackground(BACKGROUND_DARK);
        statusPanel.setBorder(new EmptyBorder(12, 12, 8, 12));

        JPanel droneInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        droneInfoPanel.setBackground(BACKGROUND_DARK);
        droneInfoPanel.add(createDroneSelectorCard());
        droneInfoPanel.add(createStatusCard(ICON_DRONE + "  State", droneStateLabel));
        droneInfoPanel.add(createStatusCard(ICON_WATER + "  Water", waterLabel));
        droneInfoPanel.add(createStatusCard("\uD83D\uDD0B  Battery", batteryLabel));
        droneInfoPanel.add(createStatusCard("\u26FD  Fuel", fuelLabel));
        droneInfoPanel.add(createStatusCard(ICON_LOCATION + "  Position", positionLabel));

        JPanel globalInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        globalInfoPanel.setBackground(BACKGROUND_DARK);
        globalInfoPanel.add(createStatusCard(ICON_CLOCK + "  Elapsed", timerLabel));
        globalInfoPanel.add(createStatusCard(ICON_FLEET + "  Fleet", fleetStatusLabel));
        globalInfoPanel.add(createStatusCard(ICON_FIRE + "  Fires", activeFireLabel));

        statusPanel.add(droneInfoPanel, BorderLayout.WEST);
        statusPanel.add(globalInfoPanel, BorderLayout.EAST);

        startSimulationTimer();

        assignmentPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
        assignmentPanel.setBackground(BACKGROUND_MID);
        assignmentPanel.setPreferredSize(new Dimension(320, 180));

        eventLog.setEditable(false);
        eventLog.setLineWrap(true);
        eventLog.setWrapStyleWord(true);
        styleLogArea(eventLog);
        telemetryLog.setEditable(false);
        telemetryLog.setLineWrap(true);
        telemetryLog.setWrapStyleWord(true);
        styleLogArea(telemetryLog);
        queueLog.setEditable(false);
        queueLog.setLineWrap(true);
        queueLog.setWrapStyleWord(true);
        styleLogArea(queueLog);
        historyLog.setEditable(false);
        historyLog.setLineWrap(true);
        historyLog.setWrapStyleWord(true);
        styleLogArea(historyLog);
        metricsLog.setEditable(false);
        metricsLog.setLineWrap(true);
        metricsLog.setWrapStyleWord(true);
        styleLogArea(metricsLog);

        JTabbedPane logTabs = createDarkTabbedPane();
        logTabs.addTab("\u2139 Events", createLogTab(eventLog));
        logTabs.addTab("\u21BB Queue", createLogTab(queueLog));
        logTabs.addTab("\u2691 Telemetry", createLogTab(telemetryLog));
        logTabs.addTab("\u2261 Metrics", createLogTab(metricsLog));
        logTabs.setPreferredSize(new Dimension(900, 200));

        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setBackground(BACKGROUND_DARK);
        rightPanel.setBorder(new EmptyBorder(8, 4, 0, 8));
        mainWindow.remove(legendPanel);
        legendPanel.setBackground(BACKGROUND_MID);
        JPanel sideInfoPanel = new JPanel(new BorderLayout(0, 8));
        sideInfoPanel.setBackground(BACKGROUND_DARK);
        sideInfoPanel.add(legendPanel, BorderLayout.NORTH);
        sideInfoPanel.add(createAssignmentsSection(), BorderLayout.CENTER);
        rightPanel.add(sideInfoPanel, BorderLayout.NORTH);

        JPanel lowerPanel = new JPanel(new BorderLayout());
        lowerPanel.setBackground(BACKGROUND_DARK);
        lowerPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
        lowerPanel.add(logTabs, BorderLayout.CENTER);

        JSplitPane centerSplit = createDarkSplitPane(JSplitPane.HORIZONTAL_SPLIT, grid, rightPanel);
        centerSplit.setResizeWeight(0.75);

        JSplitPane verticalSplit = createDarkSplitPane(JSplitPane.VERTICAL_SPLIT, centerSplit, lowerPanel);
        verticalSplit.setResizeWeight(0.70);

        mainWindow.getContentPane().removeAll();
        mainWindow.add(statusPanel, BorderLayout.NORTH);
        mainWindow.add(verticalSplit, BorderLayout.CENTER);
        mainWindow.getRootPane().setBackground(BACKGROUND_DARK);
        mainWindow.getRootPane().setBorder(new EmptyBorder(6, 6, 6, 6));
        mainWindow.revalidate();
        mainWindow.repaint();
    }

    private JTabbedPane createDarkTabbedPane() {
        // Set UI defaults for dark tabs before creating
        UIManager.put("TabbedPane.selected", BACKGROUND_LIGHT);
        UIManager.put("TabbedPane.background", BACKGROUND_MID);
        UIManager.put("TabbedPane.foreground", TEXT_PRIMARY);
        UIManager.put("TabbedPane.selectedForeground", TEXT_PRIMARY);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(2, 2, 0, 2));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BACKGROUND_MID);
        tabs.setForeground(TEXT_PRIMARY);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        tabs.setOpaque(true);
        tabs.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        // Use a change listener to style tabs after they're added
        tabs.addChangeListener(e -> {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                tabs.setBackgroundAt(i, i == tabs.getSelectedIndex() ? BACKGROUND_LIGHT : BACKGROUND_MID);
                tabs.setForegroundAt(i, TEXT_PRIMARY);
            }
        });

        return tabs;
    }

    private JSplitPane createDarkSplitPane(int orientation, java.awt.Component left, java.awt.Component right) {
        JSplitPane split = new JSplitPane(orientation, left, right);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(BACKGROUND_DARK);
        split.setDividerSize(6);
        split.setContinuousLayout(true);
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(java.awt.Graphics g) {
                        g.setColor(BORDER_COLOR);
                        g.fillRect(0, 0, getSize().width, getSize().height);
                    }
                };
            }
        });
        return split;
    }

    private JScrollPane createDarkScrollPane(java.awt.Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(BACKGROUND_DARK);
        pane.getViewport().setBackground(BACKGROUND_DARK);
        styleScrollBar(pane.getVerticalScrollBar());
        styleScrollBar(pane.getHorizontalScrollBar());
        return pane;
    }

    private void styleScrollBar(JScrollBar scrollBar) {
        scrollBar.setBackground(BACKGROUND_MID);
        scrollBar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = BORDER_COLOR;
                this.trackColor = BACKGROUND_MID;
            }
            @Override
            protected javax.swing.JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }
            @Override
            protected javax.swing.JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }
            private javax.swing.JButton createZeroButton() {
                javax.swing.JButton button = new javax.swing.JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }
        });
    }

    private JPanel createStatusCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(BACKGROUND_LIGHT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 12, 10, 12)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setForeground(TEXT_SECONDARY);

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        valueLabel.setForeground(TEXT_PRIMARY);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createLogTab(JTextArea area) {
        JScrollPane pane = createDarkScrollPane(area);
        pane.getVerticalScrollBar().setUnitIncrement(14);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_DARK);
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));
        panel.add(pane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAssignmentsSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BACKGROUND_DARK);

        assignmentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 6, 6, 6)));
        faultPanel.setLayout(new BoxLayout(faultPanel, BoxLayout.Y_AXIS));
        faultPanel.setBackground(BACKGROUND_MID);

        JScrollPane faultPane = createDarkScrollPane(faultPanel);
        faultPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        faultPane.setPreferredSize(new Dimension(300, 120));
        faultPane.getViewport().setBackground(BACKGROUND_MID);

        JScrollPane historyPane = createDarkScrollPane(historyLog);
        historyPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        historyPane.setPreferredSize(new Dimension(300, 130));

        panel.add(faultPane, BorderLayout.NORTH);
        panel.add(historyPane, BorderLayout.CENTER);
        return panel;
    }

    private void styleLogArea(JTextArea area) {
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setForeground(TEXT_PRIMARY);
        area.setBackground(BACKGROUND_DARK);
        area.setBorder(new EmptyBorder(6, 6, 6, 6));
        area.setMargin(new Insets(4, 4, 4, 4));
        area.setCaretColor(TEXT_PRIMARY);
        // Disable automatic scroll-on-update so users can read history
        DefaultCaret caret = (DefaultCaret) area.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    }

    private void styleDroneSelector() {
        droneSelector.setBackground(BACKGROUND_LIGHT);
        droneSelector.setForeground(TEXT_PRIMARY);
        droneSelector.setFont(new Font("SansSerif", Font.BOLD, 12));
        droneSelector.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        droneSelector.setFocusable(false);
        // Style the dropdown list
        droneSelector.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? ACCENT_PRIMARY : BACKGROUND_LIGHT);
                setForeground(TEXT_PRIMARY);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
    }

    private JPanel createDroneSelectorCard() {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(BACKGROUND_LIGHT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 12, 8, 12)));

        JLabel titleLabel = new JLabel(ICON_DRONE + "  Select Drone");
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setForeground(TEXT_SECONDARY);

        droneSelector.setPreferredSize(new Dimension(120, 24));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(droneSelector, BorderLayout.CENTER);
        return card;
    }

    private void onDroneSelected() {
        int selectedIndex = droneSelector.getSelectedIndex();
        if (selectedIndex <= 0) {
            // "Select Drone..." placeholder or nothing selected
            selectedDroneId = null;
            SwingUtilities.invokeLater(() -> {
                droneStateLabel.setText("N/A");
                waterLabel.setText("N/A");
                batteryLabel.setText("N/A");
                fuelLabel.setText("N/A");
                positionLabel.setText("N/A");
            });
            return;
        }

        // Parse drone ID from "Drone X" string
        String selected = (String) droneSelector.getSelectedItem();
        if (selected != null && selected.startsWith("Drone ")) {
            try {
                selectedDroneId = Integer.parseInt(selected.substring(6));
                refreshSelectedDroneDisplay();
            } catch (NumberFormatException e) {
                selectedDroneId = null;
            }
        }
    }

    private void refreshSelectedDroneDisplay() {
        if (selectedDroneId == null) return;

        SwingUtilities.invokeLater(() -> {
            DroneState state = droneStates.get(selectedDroneId);
            Double water = droneWaters.get(selectedDroneId);
            Double battery = droneBatteries.get(selectedDroneId);
            Double fuel = droneFuels.get(selectedDroneId);
            double[] pos = dronePositions.get(selectedDroneId);

            droneStateLabel.setText(state != null ? state.toString() : "N/A");
            waterLabel.setText(water != null ? String.format("%.1f L", water) : "N/A");
            batteryLabel.setText(battery != null ? String.format("%.0f%%", battery) : "N/A");
            fuelLabel.setText(fuel != null ? String.format("%.0f%%", fuel) : "N/A");
            positionLabel.setText(pos != null ? String.format("(%.1f, %.1f)", pos[0], pos[1]) : "N/A");
        });
    }

    private void ensureDroneInSelector(int droneId) {
        String droneItem = "Drone " + droneId;
        for (int i = 0; i < droneSelector.getItemCount(); i++) {
            if (droneItem.equals(droneSelector.getItemAt(i))) {
                return;  // Already exists
            }
        }
        // Add in sorted order (after placeholder)
        int insertIndex = 1;
        for (int i = 1; i < droneSelector.getItemCount(); i++) {
            String item = droneSelector.getItemAt(i);
            if (item.startsWith("Drone ")) {
                int existingId = Integer.parseInt(item.substring(6));
                if (droneId < existingId) {
                    break;
                }
                insertIndex = i + 1;
            }
        }
        droneSelector.insertItemAt(droneItem, insertIndex);
        // NOTE: Do NOT auto-select - let user choose
    }

    private void startSimulationTimer() {
        simulationStartMs = System.currentTimeMillis();
        simulationTimer = new Timer(1000, e -> {
            long elapsedMs = System.currentTimeMillis() - simulationStartMs;
            timerLabel.setText(formatElapsedTime(elapsedMs));
        });
        simulationTimer.setInitialDelay(0);
        simulationTimer.start();
    }

    private String formatElapsedTime(long elapsedMs) {
        long totalSeconds = elapsedMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateFleetStatus() {
        int total = droneStates.size();
        int active = 0;
        for (DroneState state : droneStates.values()) {
            if (state != DroneState.FAULTED) {
                active++;
            }
        }
        int finalActive = active;
        int finalTotal = total;
        SwingUtilities.invokeLater(() -> fleetStatusLabel.setText(finalActive + " / " + finalTotal));
    }

    /**
     * Appends text to a log area with smart scrolling behavior.
     * Only auto-scrolls if the user was already at the bottom of the log.
     */
    private void appendWithSmartScroll(JTextArea area, String message) {
        // Find the scroll pane containing this text area
        java.awt.Container parent = area.getParent();
        while (parent != null && !(parent instanceof JScrollPane)) {
            parent = parent.getParent();
        }

        boolean shouldScroll = true;
        JScrollBar verticalBar = null;

        if (parent instanceof JScrollPane scrollPane) {
            verticalBar = scrollPane.getVerticalScrollBar();
            int extent = verticalBar.getModel().getExtent();
            int maximum = verticalBar.getMaximum();
            int value = verticalBar.getValue();
            shouldScroll = (value + extent + 50 >= maximum);
        }

        area.append(message + System.lineSeparator());

        if (shouldScroll && verticalBar != null) {
            final JScrollBar bar = verticalBar;
            SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
        }
    }

    @Override
    public void clearFireTile(int row, int col) {
        logEvent("Fire cleared at grid (" + row + ", " + col + ")");
    }

    @Override
    public void updateDroneState(int droneId, DroneState state) {
        droneStates.put(droneId, state);
        ensureDroneInSelector(droneId);
        updateFleetStatus();

        if (selectedDroneId != null && selectedDroneId == droneId) {
            SwingUtilities.invokeLater(() -> droneStateLabel.setText(state.toString()));
        }
    }

    @Override
    public void updateDroneWater(int droneId, double water) {
        droneWaters.put(droneId, water);
        ensureDroneInSelector(droneId);

        if (selectedDroneId != null && selectedDroneId == droneId) {
            SwingUtilities.invokeLater(() -> waterLabel.setText(String.format("%.1f L", water)));
        }
    }

    @Override
    public void updateDroneBatteryFuel(int droneId, double batteryPct, double fuelPct) {
        super.updateDroneBatteryFuel(droneId, batteryPct, fuelPct);
        droneBatteries.put(droneId, batteryPct);
        droneFuels.put(droneId, fuelPct);
        ensureDroneInSelector(droneId);

        if (selectedDroneId != null && selectedDroneId == droneId) {
            SwingUtilities.invokeLater(() -> {
                batteryLabel.setText(String.format("%.0f%%", batteryPct));
                fuelLabel.setText(String.format("%.0f%%", fuelPct));
            });
        }
    }

    @Override
    public void updateDronePosition(int droneId, double x, double y) {
        dronePositions.put(droneId, new double[]{x, y});
        ensureDroneInSelector(droneId);

        if (selectedDroneId != null && selectedDroneId == droneId) {
            SwingUtilities.invokeLater(() -> positionLabel.setText(String.format("(%.1f, %.1f)", x, y)));
        }
    }

    @Override
    public void incrementActiveFires() {
        activeFireCount++;
        SwingUtilities.invokeLater(() -> activeFireLabel.setText(String.valueOf(activeFireCount)));
    }

    @Override
    public void decrementActiveFires() {
        activeFireCount = Math.max(0, activeFireCount - 1);
        SwingUtilities.invokeLater(() -> activeFireLabel.setText(String.valueOf(activeFireCount)));
    }

    @Override
    public void logEvent(String message) {
        SwingUtilities.invokeLater(() -> appendWithSmartScroll(eventLog, message));
    }

    @Override
    public void logTelemetry(String message) {
        SwingUtilities.invokeLater(() -> appendWithSmartScroll(telemetryLog, message));
    }

    @Override
    public void logQueueEvent(String message) {
        SwingUtilities.invokeLater(() -> appendWithSmartScroll(queueLog, message));
    }

    @Override
    public void logAssignmentHistory(String message) {
        SwingUtilities.invokeLater(() -> appendWithSmartScroll(historyLog, message));
    }

    /**
     * Updates the metrics display with formatted performance data.
     * Call periodically from the Scheduler to show live metrics.
     */
    public void updateMetrics(String metricsText) {
        SwingUtilities.invokeLater(() -> {
            metricsLog.setText(metricsText);
            metricsLog.setCaretPosition(0);
        });
    }

    @Override
    public void showDroneOffline(int droneId) {
        super.showDroneOffline(droneId);
        stopFaultCountdown(droneId);
        upsertFaultLabel(droneId, "OFFLINE", new Color(73, 73, 73));
        logEvent("Drone " + droneId + " is offline for the rest of the run");
    }

    @Override
    public void showDroneFault(int droneId, String faultName) {
        super.showDroneFault(droneId, faultName);
        upsertFaultLabel(droneId, faultName, faultColorFor(faultName));
    }

    @Override
    public void startDroneFaultCountdown(int droneId, String faultName, int secondsRemaining) {
        SwingUtilities.invokeLater(() -> {
            Timer existing = faultCountdownTimers.remove(droneId);
            if (existing != null) { existing.stop(); }
            final int[] remaining = {Math.max(0, secondsRemaining)};
            upsertFaultLabel(droneId, faultName + "|" + remaining[0], faultColorFor(faultName));

            Timer timer = new Timer(1000, e -> {
                remaining[0] = Math.max(0, remaining[0] - 1);
                upsertFaultLabel(droneId, faultName + "|" + remaining[0], faultColorFor(faultName));
                if (remaining[0] <= 0) {
                    ((Timer) e.getSource()).stop();
                    faultCountdownTimers.remove(droneId);
                }
            });
            timer.setInitialDelay(1000);
            timer.start();
            faultCountdownTimers.put(droneId, timer);
        });
    }

    @Override
    public void clearDroneFault(int droneId) {
        super.clearDroneFault(droneId);
        stopFaultCountdown(droneId);
        SwingUtilities.invokeLater(() -> {
            JLabel label = faultLabels.remove(droneId);
            if (label != null) {
                faultPanel.remove(label);
                faultPanel.revalidate();
                faultPanel.repaint();
            }
        });
    }

    private void upsertFaultLabel(int droneId, String faultName, Color color) {
        SwingUtilities.invokeLater(() -> {
            String shortName = shortFaultName(faultName);
            JLabel label = faultLabels.computeIfAbsent(droneId, id -> {
                JLabel created = new JLabel();
                created.setOpaque(true);
                created.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(25, 28, 35), 1),
                        new EmptyBorder(8, 12, 8, 12)));
                created.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                created.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
                created.setVerticalAlignment(JLabel.TOP);
                faultPanel.add(created);
                return created;
            });
            String fullText = "\u26A0 Drone " + droneId + " - " + shortName;
            label.setText(wrapLabelText(fullText, 240));
            label.setToolTipText(fullText);
            label.setForeground(Color.WHITE);
            label.setBackground(color);
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
            faultPanel.revalidate();
            faultPanel.repaint();
        });
    }

    private String shortFaultName(String faultName) {
        if (faultName == null || faultName.isBlank()) {
            return "FAULT";
        }
        String[] parts = faultName.split("\\|", 2);
        String baseName = parts[0];
        String suffix = parts.length > 1 ? parts[1] : null;

        String label = switch (baseName) {
            case "DRONE_STUCK", "DRONE_TIMEOUT" -> "\u23F1 STUCK / TIMEOUT";
            case "NOZZLE_STUCK_CLOSED" -> "\u2716 NOZZLE CLOSED";
            case "NOZZLE_STUCK_OPEN" -> "\u2714 NOZZLE OPEN";
            case "PACKET_LOSS" -> "\u2709 PACKET LOSS";
            case "PACKET_CORRUPTION", "COMMUNICATION_ERROR" -> "\u26A1 PACKET CORRUPTION";
            case "OFFLINE", "MISSION_FAILURE" -> "\u2620 OFFLINE";
            default -> baseName.replace('_', ' ');
        };

        if (suffix != null && !suffix.isBlank()) {
            return label + " \u23F3 " + suffix + "s";
        }
        return label;
    }

    private void stopFaultCountdown(int droneId) {
        SwingUtilities.invokeLater(() -> {
            Timer timer = faultCountdownTimers.remove(droneId);
            if (timer != null) {
                timer.stop();
            }
        });
    }

    private Color faultColorFor(String faultName) {
        if (faultName == null) {
            return new Color(155, 89, 182);
        }
        return switch (faultName) {
            case "DRONE_STUCK", "DRONE_TIMEOUT" -> new Color(233, 30, 99);
            case "NOZZLE_STUCK_CLOSED" -> new Color(26, 188, 156);
            case "NOZZLE_STUCK_OPEN" -> new Color(155, 89, 182);
            case "PACKET_LOSS" -> new Color(241, 196, 15);
            case "PACKET_CORRUPTION", "COMMUNICATION_ERROR" -> new Color(230, 126, 34);
            case "OFFLINE", "MISSION_FAILURE" -> new Color(99, 110, 114);
            default -> new Color(155, 89, 182);
        };
    }
}
