package droneswarmsim.ui;

import droneswarmsim.drone.DroneState;
import droneswarmsim.model.Zone;
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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.nio.file.Files;
import java.io.IOException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeGUI extends GUI {
    // Dark theme colors
    private static final Color BACKGROUND_DARK = new Color(15, 23, 35);
    private static final Color BACKGROUND_MID = new Color(22, 33, 48);
    private static final Color BACKGROUND_LIGHT = new Color(30, 44, 62);
    private static final Color ACCENT_PRIMARY = new Color(91, 190, 237);
    private static final Color ACCENT_SUCCESS = new Color(95, 211, 160);
    private static final Color TEXT_PRIMARY = new Color(232, 240, 249);
    private static final Color TEXT_SECONDARY = new Color(153, 173, 194);
    private static final Color BORDER_COLOR = new Color(48, 65, 85);
    private static final Color CONTROL_BORDER = new Color(91, 116, 143);
    
    // Status icons (unicode)
    private static final String ICON_DRONE = "\u2708";
    private static final String ICON_WATER = "\u2614";
    private static final String ICON_LOCATION = "\u2316";
    private static final String ICON_FIRE = "\u2622";
    private static final String ICON_CLOCK = "\u23F1";
    private static final String ICON_FLEET = "\u2699";
    
    // Drone selector and per-drone data storage
    private final JComboBox<String> droneSelector = new JComboBox<>();
    private final Map<Integer, DroneState> droneStates = new ConcurrentHashMap<>();
    private final Map<Integer, Double> droneWaters = new ConcurrentHashMap<>();
    private final Map<Integer, Double> droneBatteries = new ConcurrentHashMap<>();
    private final Map<Integer, Double> droneFuels = new ConcurrentHashMap<>();
    private final Map<Integer, double[]> dronePositions = new ConcurrentHashMap<>();  // [x, y]
    private volatile Integer selectedDroneId = null;
    private final JProgressBar waterGauge = new JProgressBar(0, 150);
    private final JProgressBar batteryGauge = new JProgressBar(0, 100);
    private final JProgressBar fuelGauge = new JProgressBar(0, 100);
    private final DefaultTableModel fleetModel = new DefaultTableModel(
            new String[]{"Drone", "State", "Water", "Battery", "Fuel"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }
    };
    private final JTable fleetTable = new JTable(fleetModel);
    private final JLabel fleetSummary = new JLabel("Waiting for drones to register");
    private final JLabel faultEmptyLabel = new JLabel("No active faults");
    private final Map<JTextArea, JCheckBox> followControls = new HashMap<>();
    
    // Simulation timer
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

        // Apply dark theme to main window
        mainWindow.getContentPane().setBackground(BACKGROUND_DARK);

        // Initialize drone selector with placeholder
        droneSelector.addItem("Select Drone...");
        styleDroneSelector();
        droneSelector.addActionListener(e -> onDroneSelected());

        // Create top status bar with drone selector and global info
        JPanel statusPanel = new JPanel(new BorderLayout(12, 10));
        statusPanel.setBackground(BACKGROUND_DARK);
        statusPanel.setBorder(new EmptyBorder(12, 12, 8, 12));

        // Left side: drone selector + drone-specific cards
        JPanel droneInfoPanel = new JPanel(new GridLayout(1, 6, 8, 0));
        droneInfoPanel.setBackground(BACKGROUND_DARK);
        droneInfoPanel.add(createDroneSelectorCard());
        droneInfoPanel.add(createStatusCard("MISSION STATE", droneStateLabel));
        droneInfoPanel.add(createResourceCard("WATER / 15 L", waterLabel, waterGauge));
        droneInfoPanel.add(createResourceCard("BATTERY", batteryLabel, batteryGauge));
        droneInfoPanel.add(createResourceCard("FUEL", fuelLabel, fuelGauge));
        droneInfoPanel.add(createStatusCard("POSITION / METRES", positionLabel));
        
        // Right side: global info (Timer, Fleet Status, Active Fires)
        JPanel globalInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        globalInfoPanel.setBackground(BACKGROUND_DARK);
        globalInfoPanel.add(createStatusCard("ELAPSED", timerLabel));
        globalInfoPanel.add(createStatusCard("ONLINE / REGISTERED", fleetStatusLabel));
        globalInfoPanel.add(createStatusCard("ACTIVE FIRES", activeFireLabel));
        JLabel title = new JLabel("FIRE RESPONSE  /  Mission control");
        title.setForeground(TEXT_PRIMARY);
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        JLabel subtitle = new JLabel("Live fleet telemetry • UDP simulation • " + zones.size() + " response zones");
        subtitle.setForeground(TEXT_SECONDARY);
        JPanel branding = new JPanel(new GridLayout(2, 1, 0, 6));
        branding.setOpaque(false);
        branding.add(title);
        branding.add(subtitle);
        statusPanel.add(droneInfoPanel, BorderLayout.CENTER);
        statusPanel.add(globalInfoPanel, BorderLayout.EAST);
        
        // Start simulation timer
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
        logTabs.addTab("Events", createLogTab(eventLog));
        logTabs.addTab("Queue", createLogTab(queueLog));
        logTabs.addTab("Telemetry", createLogTab(telemetryLog));
        logTabs.addTab("Assignments", createLogTab(historyLog));
        metricsLog.setLineWrap(false);
        logTabs.addTab("Metrics", createLogTab(metricsLog));
        logTabs.setPreferredSize(new Dimension(900, 200));

        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setBackground(BACKGROUND_DARK);
        rightPanel.setBorder(new EmptyBorder(8, 4, 0, 8));
        mainWindow.remove(legendPanel);
        legendPanel.setBackground(BACKGROUND_MID);
        JTabbedPane sideTabs = createDarkTabbedPane();
        sideTabs.addTab("Fleet", createFleetPanel());
        sideTabs.addTab("Missions & faults", createAssignmentsSection());
        sideTabs.addTab("Map legend", createDarkScrollPane(legendPanel));
        rightPanel.add(sideTabs, BorderLayout.CENTER);
        rightPanel.setMinimumSize(new Dimension(320, 180));
        rightPanel.setPreferredSize(new Dimension(420, 500));

        JPanel lowerPanel = new JPanel(new BorderLayout());
        lowerPanel.setBackground(BACKGROUND_DARK);
        lowerPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
        lowerPanel.add(logTabs, BorderLayout.CENTER);

        JPanel mapPanel = new JPanel(new BorderLayout(0, 8));
        mapPanel.setBackground(BACKGROUND_DARK);
        mapPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel mapTitle = new JLabel("RESPONSE MAP     •     Each cell represents 100 × 100 metres");
        mapTitle.setForeground(TEXT_SECONDARY);
        mapTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        mapPanel.add(mapTitle, BorderLayout.NORTH);
        grid.setMinimumSize(new Dimension(300, 300));
        JPanel mapCanvas = new JPanel(new BorderLayout());
        mapCanvas.setBackground(BACKGROUND_DARK);
        mapCanvas.setMinimumSize(new Dimension(300, 300));
        mapCanvas.add(grid, BorderLayout.CENTER);
        mapPanel.add(mapCanvas, BorderLayout.CENTER);
        for (int row = 0; row < tile.length; row++) {
            for (int col = 0; col < tile[row].length; col++) {
                final int cellRow = row;
                final int cellCol = col;
                tile[row][col].setToolTipText(mapCellDescription(row, col));
                tile[row][col].addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        dronePositions.entrySet().stream()
                                .filter(entry -> {
                                    java.awt.Point cell = toGridCell(entry.getValue()[0], entry.getValue()[1]);
                                    return cell.x == cellCol && cell.y == cellRow;
                                })
                                .map(Map.Entry::getKey).sorted().findFirst()
                                .ifPresent(id -> droneSelector.setSelectedItem("Drone " + id));
                    }
                });
            }
        }
        JSplitPane centerSplit = createDarkSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapPanel, rightPanel);
        centerSplit.setResizeWeight(0.68);
        centerSplit.setDividerLocation(0.68);

        JSplitPane verticalSplit = createDarkSplitPane(JSplitPane.VERTICAL_SPLIT, centerSplit, lowerPanel);
        verticalSplit.setResizeWeight(0.70);
        verticalSplit.setTopComponent(null);
        JPanel dashboard = new JPanel(new BorderLayout());
        dashboard.setBackground(BACKGROUND_DARK);
        dashboard.add(centerSplit, BorderLayout.CENTER);
        JButton toggleLogs = toolbarButton("Show logs", "Show or hide events, queue, telemetry, assignments, and metrics");
        toggleLogs.addActionListener(e -> {
            boolean show = verticalSplit.getParent() != dashboard;
            dashboard.removeAll();
            if (show) {
                verticalSplit.setTopComponent(centerSplit);
                dashboard.add(verticalSplit, BorderLayout.CENTER);
            } else {
                verticalSplit.setTopComponent(null);
                dashboard.add(centerSplit, BorderLayout.CENTER);
            }
            toggleLogs.setText(show ? "Hide logs" : "Show logs");
            dashboard.revalidate();
            mainWindow.validate();
            if (show) verticalSplit.setDividerLocation(0.72);
            mainWindow.validate();
            int padding = mapPanel.getInsets().left + mapPanel.getInsets().right;
            centerSplit.setDividerLocation(Math.min(mapCanvas.getHeight() + padding,
                    centerSplit.getWidth() - centerSplit.getDividerSize() - rightPanel.getMinimumSize().width));
            dashboard.repaint();
        });
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(BACKGROUND_DARK);
        footer.setBorder(new EmptyBorder(8, 12, 8, 12));
        footer.add(branding, BorderLayout.WEST);
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footerActions.setOpaque(false);
        footerActions.add(toggleLogs);
        footer.add(footerActions, BorderLayout.EAST);

        mainWindow.getContentPane().removeAll();
        mainWindow.add(statusPanel, BorderLayout.NORTH);
        mainWindow.add(dashboard, BorderLayout.CENTER);
        mainWindow.add(footer, BorderLayout.SOUTH);
        mainWindow.getRootPane().setBackground(BACKGROUND_DARK);
        mainWindow.getRootPane().setBorder(new EmptyBorder(6, 6, 6, 6));
        mainWindow.setTitle("Fire Response • Drone Swarm Mission Control");
        mainWindow.setMinimumSize(new Dimension(1024, 720));
        mainWindow.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    mainWindow.validate();
                    // Reserve the sidebar's minimum width, then size the map's
                    // initial left pane to match its actual available height.
                    int horizontalPadding = mapPanel.getInsets().left + mapPanel.getInsets().right;
                    int maximumMapPaneWidth = centerSplit.getWidth() - centerSplit.getDividerSize()
                            - rightPanel.getMinimumSize().width;
                    centerSplit.setDividerLocation(Math.min(mapCanvas.getHeight() + horizontalPadding,
                            maximumMapPaneWidth));
                    mainWindow.validate();
                });
            }
            @Override public void windowClosing(WindowEvent e) {
                if (simulationTimer != null) simulationTimer.stop();
                faultCountdownTimers.values().forEach(Timer::stop);
            }
        });
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
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                tabInsets = new Insets(10, 14, 10, 14);
                contentBorderInsets = new Insets(1, 0, 0, 0);
            }
            @Override protected void paintTabBackground(java.awt.Graphics g, int placement, int index,
                    int x, int y, int width, int height, boolean selected) {
                g.setColor(selected ? BACKGROUND_LIGHT : BACKGROUND_MID);
                g.fillRect(x, y, width, height);
                if (selected) {
                    g.setColor(ACCENT_PRIMARY);
                    g.fillRect(x + 8, y + height - 3, width - 16, 3);
                }
            }
            @Override protected void paintTabBorder(java.awt.Graphics g, int placement, int index,
                    int x, int y, int width, int height, boolean selected) { }
            @Override protected void paintFocusIndicator(java.awt.Graphics g, int placement,
                    java.awt.Rectangle[] rectangles, int index, java.awt.Rectangle icon,
                    java.awt.Rectangle text, boolean selected) {
                if (selected && tabPane.hasFocus()) {
                    g.setColor(ACCENT_PRIMARY);
                    java.awt.Rectangle r = rectangles[index];
                    g.drawRect(r.x + 3, r.y + 3, r.width - 6, r.height - 6);
                }
            }
            @Override protected void paintContentBorder(java.awt.Graphics g, int placement, int selected) { }
        });
        
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
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        JCheckBox follow = new JCheckBox("Follow latest", true);
        follow.setOpaque(false);
        follow.setForeground(TEXT_SECONDARY);
        followControls.put(area, follow);
        follow.addActionListener(e -> {
            if (follow.isSelected()) pane.getVerticalScrollBar().setValue(pane.getVerticalScrollBar().getMaximum());
        });
        JButton copy = toolbarButton("Copy", "Copy this entire log to the clipboard");
        copy.addActionListener(e -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(area.getText()), null));
        JButton save = toolbarButton("Save…", "Export this log as a text file");
        save.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(mainWindow) == JFileChooser.APPROVE_OPTION) {
                try { Files.writeString(chooser.getSelectedFile().toPath(), area.getText()); }
                catch (IOException error) {
                    JOptionPane.showMessageDialog(mainWindow, error.getMessage(), "Could not save log", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JButton clear = toolbarButton("Clear view", "Clear displayed text; recorded log files are unaffected");
        clear.addActionListener(e -> area.setText(""));
        tools.add(follow);
        tools.add(copy);
        tools.add(save);
        tools.add(clear);
        panel.add(tools, BorderLayout.NORTH);
        return panel;
    }

    private JButton toolbarButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setBackground(BACKGROUND_LIGHT);
        button.setForeground(TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR), new EmptyBorder(5, 10, 5, 10)));
        return button;
    }

    private JPanel createResourceCard(String title, JLabel label, JProgressBar gauge) {
        JPanel card = createStatusCard(title, label);
        gauge.setForeground(ACCENT_SUCCESS);
        gauge.setBackground(BACKGROUND_DARK);
        gauge.setBorderPainted(false);
        gauge.setPreferredSize(new Dimension(100, 5));
        gauge.setToolTipText(title + " remaining");
        card.add(gauge, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createFleetPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BACKGROUND_DARK);
        panel.setBorder(new EmptyBorder(12, 8, 8, 8));
        fleetSummary.setForeground(TEXT_SECONDARY);
        fleetSummary.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel fleetHeader = new JPanel(new BorderLayout(0, 8));
        fleetHeader.setOpaque(false);
        fleetHeader.add(fleetSummary, BorderLayout.NORTH);
        javax.swing.JTextField filter = new javax.swing.JTextField();
        filter.setBackground(BACKGROUND_LIGHT);
        filter.setForeground(TEXT_PRIMARY);
        filter.setCaretColor(TEXT_PRIMARY);
        filter.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR), new EmptyBorder(7, 8, 7, 8)));
        filter.setToolTipText("Filter the fleet by drone number or mission state");
        filter.getAccessibleContext().setAccessibleName("Filter fleet by drone or state");
        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setOpaque(false);
        JLabel searchLabel = new JLabel("Filter");
        searchLabel.setForeground(TEXT_SECONDARY);
        search.add(searchLabel, BorderLayout.WEST);
        search.add(filter, BorderLayout.CENTER);
        fleetHeader.add(search, BorderLayout.CENTER);
        panel.add(fleetHeader, BorderLayout.NORTH);
        fleetTable.setBackground(BACKGROUND_MID);
        fleetTable.setForeground(TEXT_PRIMARY);
        fleetTable.setSelectionBackground(new Color(37, 80, 107));
        fleetTable.setSelectionForeground(TEXT_PRIMARY);
        fleetTable.setRowHeight(32);
        fleetTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fleetTable.setShowHorizontalLines(true);
        fleetTable.setShowVerticalLines(false);
        fleetTable.setGridColor(BORDER_COLOR);
        fleetTable.setIntercellSpacing(new Dimension(0, 1));
        fleetTable.setFillsViewportHeight(true);
        fleetTable.setAutoCreateRowSorter(true);
        @SuppressWarnings("unchecked")
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter =
                (javax.swing.table.TableRowSorter<DefaultTableModel>) fleetTable.getRowSorter();
        sorter.setSortKeys(java.util.List.of(new javax.swing.RowSorter.SortKey(0, javax.swing.SortOrder.ASCENDING)));
        for (int column = 2; column < 5; column++) {
            sorter.setComparator(column, java.util.Comparator.comparingDouble(value -> {
                String text = String.valueOf(value);
                return text.equals("—") ? -1 : Double.parseDouble(
                        text.replace(" L", "").replace("%", "").replace(',', '.'));
            }));
        }
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String query = filter.getText().trim();
                sorter.setRowFilter(query.isEmpty() ? null : javax.swing.RowFilter.regexFilter(
                        "(?i)" + java.util.regex.Pattern.quote(query), 0, 1));
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        fleetTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        fleetTable.getTableHeader().setBackground(BACKGROUND_LIGHT);
        fleetTable.getTableHeader().setForeground(TEXT_PRIMARY);
        fleetTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        fleetTable.getTableHeader().setReorderingAllowed(false);
        fleetTable.getTableHeader().setPreferredSize(new Dimension(0, 34));
        fleetTable.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setOpaque(true);
                setBackground(BACKGROUND_LIGHT);
                setForeground(TEXT_PRIMARY);
                setFont(table.getTableHeader().getFont());
                setHorizontalAlignment(JLabel.LEFT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, column < table.getColumnCount() - 1 ? 1 : 0,
                                CONTROL_BORDER), new EmptyBorder(0, 7, 0, 7)));
                String direction = "";
                int modelColumn = table.convertColumnIndexToModel(column);
                for (javax.swing.RowSorter.SortKey key : table.getRowSorter().getSortKeys()) {
                    if (key.getColumn() == modelColumn) {
                        direction = key.getSortOrder() == javax.swing.SortOrder.ASCENDING ? " ↑" : " ↓";
                        break;
                    }
                }
                setText(value + direction);
                setToolTipText("Sort by " + value);
                return this;
            }
        });
        int[] widths = {48, 120, 62, 64, 55};
        for (int i = 0; i < widths.length; i++) {
            fleetTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, selected && column == 0 ? 3 : 0, 0, 0, ACCENT_PRIMARY),
                        new EmptyBorder(0, selected && column == 0 ? 2 : 5, 0, 5)));
                if (!selected) {
                    setBackground(row % 2 == 0 ? BACKGROUND_MID : BACKGROUND_DARK);
                    setForeground(column == 1 ? stateColor(String.valueOf(value)) : TEXT_PRIMARY);
                }
                setToolTipText("Drone " + table.getValueAt(row, 0) + " • " + value + " — click to inspect");
                return this;
            }
        };
        fleetTable.setDefaultRenderer(String.class, renderer);
        fleetTable.setDefaultRenderer(Integer.class, renderer);
        fleetTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && fleetTable.getSelectedRow() >= 0) {
                int row = fleetTable.convertRowIndexToModel(fleetTable.getSelectedRow());
                droneSelector.setSelectedItem("Drone " + fleetModel.getValueAt(row, 0));
            }
        });
        JScrollPane fleetPane = createDarkScrollPane(fleetTable);
        fleetPane.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
        fleetPane.getViewport().setBackground(BACKGROUND_MID);
        JPanel tableCorner = new JPanel();
        tableCorner.setBackground(BACKGROUND_LIGHT);
        fleetPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, tableCorner);
        panel.add(fleetPane, BorderLayout.CENTER);
        JLabel hint = new JLabel("Select a drone to inspect • Click column headings to sort");
        hint.setForeground(TEXT_SECONDARY);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private Color stateColor(String state) {
        if (state.contains("FAULT") || state.contains("OFFLINE")) return new Color(255, 131, 145);
        if (state.equals("IDLE")) return ACCENT_SUCCESS;
        if (state.equals("REFILLING")) return new Color(244, 195, 112);
        return ACCENT_PRIMARY;
    }

    private void refreshFleetTable() {
        // Update in place so sorting and selection survive telemetry updates.
        droneStates.keySet().stream().sorted().forEach(id -> {
            int row = -1;
            for (int i = 0; i < fleetModel.getRowCount(); i++) {
                if (fleetModel.getValueAt(i, 0).equals(id)) { row = i; break; }
            }
            Object[] values = {id, droneStates.get(id).name().replace('_', ' '),
                    resourceText(droneWaters.get(id), " L"),
                    resourceText(droneBatteries.get(id), "%"), resourceText(droneFuels.get(id), "%")};
            if (row < 0) fleetModel.addRow(values);
            else for (int col = 1; col < values.length; col++) {
                if (!values[col].equals(fleetModel.getValueAt(row, col))) fleetModel.setValueAt(values[col], row, col);
            }
        });
        long idle = droneStates.values().stream().filter(s -> s == DroneState.IDLE).count();
        long faults = droneStates.values().stream().filter(s -> s == DroneState.FAULTED).count();
        fleetSummary.setText(idle + " ready   /   " + (droneStates.size() - idle - faults)
                + " on mission   /   " + faults + " faulted");
    }

    private String resourceText(Double value, String unit) {
        return value == null ? "—" : String.format(unit.equals(" L") ? "%.1f%s" : "%.0f%s", value, unit);
    }

    private JPanel createAssignmentsSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BACKGROUND_DARK);

        assignmentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 6, 6, 6)));
        faultPanel.setLayout(new BoxLayout(faultPanel, BoxLayout.Y_AXIS));
        faultPanel.setBackground(BACKGROUND_MID);
        faultEmptyLabel.setForeground(TEXT_SECONDARY);
        faultEmptyLabel.setBorder(new EmptyBorder(12, 12, 12, 12));
        faultPanel.add(faultEmptyLabel);

        JScrollPane faultPane = createDarkScrollPane(faultPanel);
        faultPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        faultPane.setPreferredSize(new Dimension(300, 120));
        faultPane.getViewport().setBackground(BACKGROUND_MID);

        assignmentPanel.setLayout(new GridLayout(0, 1, 0, 8));
        assignmentPanel.setPreferredSize(null);
        JScrollPane missions = createDarkScrollPane(assignmentPanel);
        panel.add(section("FAULT MONITOR", faultPane), BorderLayout.NORTH);
        panel.add(section("ACTIVE ASSIGNMENTS", missions), BorderLayout.CENTER);
        return panel;
    }

    private JPanel section(String title, java.awt.Component content) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 8, 8, 8));
        JLabel heading = new JLabel(title);
        heading.setForeground(TEXT_SECONDARY);
        heading.setFont(new Font("SansSerif", Font.BOLD, 11));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
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
        droneSelector.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override protected void paintComponent(java.awt.Graphics graphics) {
                        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(getModel().isRollover() || getModel().isPressed()
                                ? new Color(43, 67, 91) : BACKGROUND_LIGHT);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(CONTROL_BORDER);
                        g.drawLine(0, 4, 0, getHeight() - 5);
                        g.setColor(TEXT_PRIMARY);
                        g.setStroke(new java.awt.BasicStroke(1.8f));
                        int x = getWidth() / 2;
                        int y = getHeight() / 2;
                        g.drawLine(x - 4, y - 2, x, y + 2);
                        g.drawLine(x, y + 2, x + 4, y - 2);
                        g.dispose();
                    }
                };
                arrow.setBorder(BorderFactory.createEmptyBorder());
                arrow.setFocusable(false);
                arrow.setRolloverEnabled(true);
                arrow.setPreferredSize(new Dimension(28, 28));
                return arrow;
            }
            @Override public void paintCurrentValueBackground(java.awt.Graphics g,
                    java.awt.Rectangle bounds, boolean focused) {
                g.setColor(BACKGROUND_MID);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            @Override protected ComboPopup createPopup() {
                BasicComboPopup popup = new BasicComboPopup(comboBox) {
                    @Override protected void configureList() {
                        super.configureList();
                        list.setBackground(BACKGROUND_MID);
                        list.setForeground(TEXT_PRIMARY);
                        list.setSelectionBackground(new Color(37, 80, 107));
                        list.setSelectionForeground(TEXT_PRIMARY);
                        list.setFixedCellHeight(32);
                    }
                    @Override protected void configureScroller() {
                        super.configureScroller();
                        scroller.setBorder(BorderFactory.createEmptyBorder());
                        styleScrollBar(scroller.getVerticalScrollBar());
                    }
                };
                popup.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
                popup.setBackground(BACKGROUND_MID);
                return popup;
            }
        });
        droneSelector.setBackground(BACKGROUND_LIGHT);
        droneSelector.setForeground(TEXT_PRIMARY);
        droneSelector.setFont(new Font("SansSerif", Font.BOLD, 12));
        droneSelector.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER, 1));
        droneSelector.setMaximumRowCount(10);
        droneSelector.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                droneSelector.setBorder(BorderFactory.createLineBorder(ACCENT_PRIMARY));
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                droneSelector.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
            }
        });
        // Style the dropdown list
        droneSelector.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(index >= 0 && isSelected ? new Color(37, 80, 107) : BACKGROUND_MID);
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

        JLabel titleLabel = new JLabel("INSPECT DRONE");
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setForeground(TEXT_SECONDARY);

        droneSelector.setPreferredSize(new Dimension(120, 28));
        droneSelector.setFocusable(true);
        droneSelector.setToolTipText("Select a drone here or click its fleet row");

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
                droneStateLabel.setForeground(TEXT_SECONDARY);
                waterGauge.setValue(0);
                batteryGauge.setValue(0);
                fuelGauge.setValue(0);
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
            
            droneStateLabel.setText(state != null ? state.toString().replace('_', ' ') : "Waiting");
            droneStateLabel.setForeground(state != null ? stateColor(state.toString()) : TEXT_SECONDARY);
            waterLabel.setText(water != null ? String.format("%.1f L", water) : "N/A");
            batteryLabel.setText(battery != null ? String.format("%.0f%%", battery) : "N/A");
            fuelLabel.setText(fuel != null ? String.format("%.0f%%", fuel) : "N/A");
            positionLabel.setText(pos != null ? String.format("(%.1f, %.1f)", pos[0], pos[1]) : "N/A");
            updateGauge(waterGauge, water == null ? 0 : water * 10);
            updateGauge(batteryGauge, battery == null ? 0 : battery);
            updateGauge(fuelGauge, fuel == null ? 0 : fuel);
        });
    }

    private void updateGauge(JProgressBar gauge, double value) {
        gauge.setValue((int) Math.round(value));
        double ratio = value / gauge.getMaximum();
        gauge.setForeground(ratio <= 0.2 ? new Color(255, 131, 145)
                : ratio <= 0.4 ? new Color(244, 195, 112) : ACCENT_SUCCESS);
    }

    private void ensureDroneInSelector(int droneId) {
        if (mainWindow == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> ensureDroneInSelector(droneId));
            return;
        }
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
        if (selectedDroneId == null && droneSelector.getItemCount() == 2) droneSelector.setSelectedItem(droneItem);
    }

    private void startSimulationTimer() {
        simulationStartMs = System.currentTimeMillis();
        simulationTimer = new Timer(250, e -> {
            long elapsedMs = System.currentTimeMillis() - simulationStartMs;
            timerLabel.setText(formatElapsedTime(elapsedMs));
            refreshFleetTable();
            refreshSelectedDroneDisplay();
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
            // Check if user is near the bottom (within 50 pixels)
            int extent = verticalBar.getModel().getExtent();
            int maximum = verticalBar.getMaximum();
            int value = verticalBar.getValue();
            shouldScroll = (value + extent + 50 >= maximum);
        }
        
        JCheckBox follow = followControls.get(area);
        shouldScroll = shouldScroll && (follow == null || follow.isSelected());
        area.append(message + System.lineSeparator());
        // Bound the view's memory during long telemetry-heavy runs.
        int excess = area.getDocument().getLength() - 200_000;
        if (excess > 0) {
            try {
                String prefix = area.getDocument().getText(0, Math.min(excess + 4096, area.getDocument().getLength()));
                int boundary = prefix.indexOf('\n', excess);
                area.getDocument().remove(0, boundary >= 0 ? boundary + 1 : excess);
            } catch (javax.swing.text.BadLocationException ignored) { }
        }
        
        if (shouldScroll && verticalBar != null) {
            // Schedule scroll after the text has been rendered
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
        // Store data for this drone
        droneStates.put(droneId, state);
        ensureDroneInSelector(droneId);
        updateFleetStatus();
        
        // Only update display if this is the selected drone
        if (selectedDroneId != null && selectedDroneId == droneId) {
            refreshSelectedDroneDisplay();
        }
    }

    @Override
    public void updateDroneWater(int droneId, double water) {
        // Store data for this drone
        droneWaters.put(droneId, water);
        ensureDroneInSelector(droneId);
        
        // Only update display if this is the selected drone
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
        // Store data for this drone
        dronePositions.put(droneId, new double[]{x, y});
        ensureDroneInSelector(droneId);
        
        // Only update display if this is the selected drone
        if (selectedDroneId != null && selectedDroneId == droneId) {
            SwingUtilities.invokeLater(() -> positionLabel.setText(String.format("(%.1f, %.1f)", x, y)));
        }
    }

    @Override
    public void incrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            activeFireCount++;
            activeFireLabel.setText(String.valueOf(activeFireCount));
            activeFireLabel.setForeground(new Color(255, 167, 112));
        });
    }

    @Override
    public void decrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            activeFireCount = Math.max(0, activeFireCount - 1);
            activeFireLabel.setText(String.valueOf(activeFireCount));
            activeFireLabel.setForeground(activeFireCount == 0 ? ACCENT_SUCCESS : new Color(255, 167, 112));
        });
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
        updateDroneState(droneId, DroneState.FAULTED);
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
            faultEmptyLabel.setVisible(faultLabels.isEmpty());
        });
    }

    private void upsertFaultLabel(int droneId, String faultName, Color color) {
        SwingUtilities.invokeLater(() -> {
            faultEmptyLabel.setVisible(false);
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
            double luminance = color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114;
            label.setForeground(luminance > 155 ? BACKGROUND_DARK : Color.WHITE);
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
            return new Color(155, 89, 182);  // Purple default
        }
        return switch (faultName) {
            case "DRONE_STUCK", "DRONE_TIMEOUT" -> new Color(233, 30, 99);       // Pink/magenta (distinct from fire)
            case "NOZZLE_STUCK_CLOSED" -> new Color(26, 188, 156);               // Teal
            case "NOZZLE_STUCK_OPEN" -> new Color(155, 89, 182);                 // Purple
            case "PACKET_LOSS" -> new Color(241, 196, 15);                       // Yellow
            case "PACKET_CORRUPTION", "COMMUNICATION_ERROR" -> new Color(230, 126, 34);  // Orange
            case "OFFLINE", "MISSION_FAILURE" -> new Color(99, 110, 114);        // Gray
            default -> new Color(155, 89, 182);
        };
    }
}
