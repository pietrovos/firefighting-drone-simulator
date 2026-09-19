package DroneSwarmSim.ui;
import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.model.Zone;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import javax.swing.border.Border;

/** Shared map and animation behavior for the runtime dashboard. */
public abstract class GUI {
    public static final int rows = 30;
    public static final int cols = 30;
    private static final Color WINDOW_BACKGROUND = new Color(30, 32, 38);
    private static final Color PANEL_BACKGROUND = new Color(40, 44, 52);
    private static final Color GRID_BACKGROUND = new Color(35, 38, 45);
    private static final Color CELL_BORDER = new Color(50, 54, 62);
    private static final Color ZONE_OUTLINE = new Color(86, 156, 214);
    private static final Color HOME_OUTLINE = new Color(120, 144, 156);
    private static final Color HOME_FILL = new Color(69, 90, 100);
    private static final Color FAULT_STUCK = new Color(233, 30, 99);
    private static final Color FAULT_NOZZLE_CLOSED = new Color(26, 188, 156);
    private static final Color FAULT_NOZZLE_OPEN = new Color(155, 89, 182);
    private static final Color FAULT_PACKET_LOSS = new Color(241, 196, 15);
    private static final Color FAULT_PACKET_CORRUPTION = new Color(230, 126, 34);
    private static final Color FAULT_OFFLINE = new Color(99, 110, 114);
    private static final Color TEXT_PRIMARY = new Color(212, 212, 212);
    private static final Color TEXT_SECONDARY = new Color(150, 150, 150);
    private static final Color BORDER_COLOR = new Color(60, 63, 70);

    private static final Color[] FIRE_COLORS = {
        new Color(255, 87, 34),
        new Color(255, 152, 0),
        new Color(255, 193, 7),
        new Color(244, 67, 54),
        new Color(255, 112, 67),
        new Color(255, 167, 38),
    };
    private static final int FIRE_ANIMATION_INTERVAL_MS = 150;

    JPanel[][] tile;
    private boolean[][] zoneLabelTiles = new boolean[rows][cols];

    JFrame mainWindow;
    JPanel grid;
    JPanel assignmentPanel;
    JPanel legendPanel;
    protected final Map<Integer, Zone> zones;
    private final Map<Integer, Point> droneCells = new ConcurrentHashMap<>();
    private final Map<Integer, Deque<Point>> droneMotionPaths = new ConcurrentHashMap<>();
    private final Map<Integer, Point> faultedDroneCells = new ConcurrentHashMap<>();
    private final Map<Integer, String> faultedDroneFaults = new ConcurrentHashMap<>();
    private final Map<Integer, Point> fireCells = new ConcurrentHashMap<>();
    private final Map<Integer, List<Point>> fireSpreadCells = new ConcurrentHashMap<>();
    private final Map<Integer, String> fireLabels = new ConcurrentHashMap<>();
    private final Map<Integer, Point> droppingDroneCells = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> droppingDroneZones = new ConcurrentHashMap<>();
    private final Map<Integer, Double> droneWaterLevels = new ConcurrentHashMap<>();
    private final Map<Integer, Double> droneBatteryLevels = new ConcurrentHashMap<>();
    private final Map<Integer, Double> droneFuelLevels = new ConcurrentHashMap<>();
    private javax.swing.Timer fireAnimationTimer;
    private javax.swing.Timer waterDropTimer;
    private javax.swing.Timer droneMotionTimer;
    private int fireAnimationFrame = 0;
    private int waterDropFrame = 0;

    private static final Color[] WATER_DROP_DRONE_COLORS = {
        new Color(52, 152, 219),
        new Color(41, 182, 246),
        new Color(0, 188, 212),
        new Color(41, 182, 246),
    };
    private static final Color[] WATER_SPLASH_COLORS = {
        new Color(100, 181, 246),
        new Color(79, 195, 247),
        new Color(128, 222, 234),
    };
    private static final int WATER_DROP_INTERVAL_MS = 200;
    private static final int DRONE_MOTION_INTERVAL_MS = 35;
    private static final Point HOME_CELL = new Point(0, 0);

    /**
     * Constructs a GUI with no pre-defined zones. Equivalent to {@code GUI(Collections.emptyMap())}.
     */
    public GUI() {
        this(Collections.emptyMap());
    }

    /** Builds the dashboard when a graphical environment is available. */
    public GUI(Map<Integer, Zone> zones) {
        if (zones == null) zones = Collections.emptyMap();
        this.zones = new HashMap<>(zones);
        if (GraphicsEnvironment.isHeadless()) {
            tile = null;
            mainWindow = null;
            grid = null;
            assignmentPanel = null;
            return;
        }
        tile = new JPanel[rows][cols];
        mainWindow = new JFrame();
        grid = new JPanel();

        Color gradientTop = new Color(48, 52, 62);
        Color gradientBottom = new Color(35, 38, 46);
        assignmentPanel = new GradientPanel(gradientTop, gradientBottom);
        legendPanel = new GradientPanel(gradientTop, gradientBottom);

        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setTitle("Firefighting Drone Swarm Monitor");
        mainWindow.setSize(1280, 900);
        mainWindow.setMinimumSize(new Dimension(1180, 820));
        mainWindow.setLayout(new BorderLayout(14, 14));
        mainWindow.getContentPane().setBackground(WINDOW_BACKGROUND);

        grid.setLayout(new GridLayout(rows, cols, 0, 0));
        grid.setBackground(GRID_BACKGROUND);
        grid.setBorder(BorderFactory.createCompoundBorder(
                new ShadowBorder(4, new Color(0, 0, 0, 60), 8),
                new RoundedBorder(8, new Color(70, 75, 85), 1)
        ));
        for (int i = 0; i < rows; ++i) {
            for (int j = 0; j < cols; j++) {
                JPanel cell = new JPanel();
                cell.setBackground(TileTypes.NEUTRAL.getColor());
                cell.setBorder(BorderFactory.createLineBorder(CELL_BORDER));
                tile[i][j] = cell;
                grid.add(cell);
            }
        }

        for (Map.Entry<Integer, Zone> key : this.zones.entrySet()){
            Zone z = key.getValue();
            drawZone(z.zoneID(), z.xOrigin(),z.yOrigin(),z.xEnd(), z.yEnd(), ZONE_OUTLINE);
        }
        drawHomeSpot();

        assignmentPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
        assignmentPanel.setPreferredSize(new Dimension(600, 56));
        assignmentPanel.setBorder(createStyledBorder(4, 10, 8));

        legendPanel.setLayout(new GridBagLayout());
        legendPanel.setBorder(createStyledBorder(4, 10, 12));

        GridBagConstraints legendConstraints = new GridBagConstraints();

        legendConstraints.gridx = 0;
        legendConstraints.gridy = 0;
        legendConstraints.insets = new Insets(4, 6, 4, 8);
        legendConstraints.anchor = GridBagConstraints.WEST;

        addLegendItem(legendPanel, legendConstraints, TileTypes.DRONE_LOCATION.getColor(), "Drone");
        addLegendTextEntry(legendPanel, legendConstraints, "DX", "Drone Number X");
        addLegendItem(legendPanel, legendConstraints, HOME_FILL, "Drone Home");
        addLegendItem(legendPanel, legendConstraints, TileTypes.DRONE_DROPPING.getColor(), "Drone Dropping Water");
        addLegendItem(legendPanel, legendConstraints, TileTypes.NEUTRAL.getColor(), "Neutral");
        addLegendItem(legendPanel, legendConstraints, TileTypes.FIRE_EXTINGUISHED.getColor(), "Extinguished Fire");

        addLegendItem(legendPanel, legendConstraints, TileTypes.FIRE.getColor(), "Fire");
        addLegendTextEntry(legendPanel, legendConstraints, "L", "Low Intensity");
        addLegendTextEntry(legendPanel, legendConstraints, "M", "Moderate Intensity");
        addLegendTextEntry(legendPanel, legendConstraints, "H", "High Intensity");

        addLegendItem(legendPanel, legendConstraints, FAULT_STUCK, "Fault: Stuck / Timeout");
        addLegendItem(legendPanel, legendConstraints, FAULT_NOZZLE_CLOSED, "Fault: Nozzle Closed");
        addLegendItem(legendPanel, legendConstraints, FAULT_NOZZLE_OPEN, "Fault: Nozzle Open");
        addLegendItem(legendPanel, legendConstraints, FAULT_PACKET_LOSS, "Fault: Packet Loss");
        addLegendItem(legendPanel, legendConstraints, FAULT_PACKET_CORRUPTION, "Fault: Packet Corruption");
        addLegendItem(legendPanel, legendConstraints, FAULT_OFFLINE, "Drone Offline");





        mainWindow.add(grid, BorderLayout.CENTER);
        mainWindow.add(assignmentPanel, BorderLayout.SOUTH);
        mainWindow.add(legendPanel,BorderLayout.EAST);
        mainWindow.setLocationRelativeTo(null);
    }

    /**
     * Makes the main application window visible to the user.
     * <p>
     * This method is responsible for rendering the GUI window on the screen
     * after all necessary components have been initialized and added to the
     * main frame. Once invoked, the user can interact with the graphical
     * interface of the application. Has no effect in headless environments.
     */
    public void start() {
        if (mainWindow != null) mainWindow.setVisible(true);
    }

    public void updateTile(TileTypes t, int row, int col, String labelData) {
        if (tile == null) return;
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;
        SwingUtilities.invokeLater(() -> {
            if (t == null) {
                tile[row][col].setBackground(Color.BLACK);
                return;
            }
            switch (t) {
                case FIRE -> {
                    tile[row][col].removeAll();
                    tile[row][col].setBackground(TileTypes.FIRE.getColor());
                    JLabel sevLabel = new JLabel(labelData);
                    sevLabel.setForeground(Color.WHITE);
                    sevLabel.setFont(new Font("SansSerif" , Font.BOLD, 12));
                    tile[row][col].setLayout(new BorderLayout());
                    tile[row][col].add(sevLabel, BorderLayout.NORTH);
                }
                case FIRE_EXTINGUISHED -> {
                    tile[row][col].removeAll();
                    tile[row][col].setBackground(TileTypes.FIRE_EXTINGUISHED.getColor());
                }
                case NEUTRAL -> {
                    //only delete label if not a zone label
                    if(!zoneLabelTiles[row][col]){
                        tile[row][col].removeAll();
                    }
                    tile[row][col].setBackground(TileTypes.NEUTRAL.getColor());
                }
                case DRONE_LOCATION -> {
                    tile[row][col].setBackground(TileTypes.DRONE_LOCATION.getColor());
                    //only write label if not over a zone
                    if(!zoneLabelTiles[row][col]){
                        tile[row][col].removeAll();
                        JLabel idLabel = new JLabel(labelData);
                        idLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
                        tile[row][col].setLayout(new BorderLayout());
                        tile[row][col].add(idLabel, BorderLayout.NORTH);
                    }
                }
                case DRONE_FAULTED -> {
                    tile[row][col].setBackground(TileTypes.DRONE_FAULTED.getColor());
                    if (!zoneLabelTiles[row][col]) {
                        tile[row][col].removeAll();
                        JLabel idLabel = new JLabel(labelData);
                        idLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
                        idLabel.setForeground(Color.WHITE);
                        tile[row][col].setLayout(new BorderLayout());
                        tile[row][col].add(idLabel, BorderLayout.NORTH);
                    }
                }
                default -> tile[row][col].setBackground(Color.BLACK);
            }
            tile[row][col].revalidate();
            tile[row][col].repaint();
        });
    }

    public void updateTile(TileTypes t, int row, int col){
        updateTile(t,row,col,"");
    }

    /**
     * Updates the assignment panel to display the assigned zone for a specific drone.
     * If an assignment already exists for the given drone, it updates the label;
     * otherwise, it creates a new label for the assignment.
     *
     * @param droneId The unique identifier of the drone whose assignment is being displayed.
     * @param zoneId  The unique identifier of the zone to which the drone is assigned.
     */
    public void showAssignment(int droneId, int zoneId) {
        showAssignment(droneId, zoneId, null);
    }

    public void showAssignment(int droneId, int zoneId, String severityLabel) {
        showAssignment(droneId, zoneId, severityLabel, -1, "en route");
    }

    public void showAssignment(int droneId, int zoneId, String severityLabel, int remainingWater, String status) {
        if (assignmentPanel == null) return;
        SwingUtilities.invokeLater(() -> {
            String assignmentText = formatAssignmentText(droneId, zoneId, severityLabel, remainingWater, status);
            String wrappedAssignmentText = wrapLabelText(assignmentText, 210);
            for (Component c : assignmentPanel.getComponents()) {
                if (c instanceof JPanel panel && panel.getName() != null
                        && panel.getName().equals("drone-" + droneId)) {
                    // Update existing card
                    for (Component inner : panel.getComponents()) {
                        if (inner instanceof JLabel lbl) {
                            lbl.setText(wrappedAssignmentText);
                            lbl.setToolTipText(assignmentText);
                            break;
                        }
                    }
                    return;
                }
            }
            // Create a styled card panel for each assignment
            JPanel card = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Gradient background
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(60, 65, 78),
                            0, getHeight(), new Color(50, 54, 65)
                    );
                    g2d.setPaint(gp);
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2d.dispose();
                }
            };
            card.setName("drone-" + droneId);
            card.setOpaque(false);
            card.setLayout(new BorderLayout());
            card.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(8, new Color(80, 85, 95), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));

            JLabel label = new JLabel(assignmentText);
            label.setText(wrappedAssignmentText);
            label.setForeground(TEXT_PRIMARY);
            label.setFont(new Font("SansSerif", Font.BOLD, 11));
            label.setVerticalAlignment(SwingConstants.TOP);
            label.setToolTipText(assignmentText);
            card.add(label, BorderLayout.CENTER);

            assignmentPanel.add(card);
            assignmentPanel.revalidate();
            assignmentPanel.repaint();
        });
    }

    protected String wrapLabelText(String text, int widthPx) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return "<html><div style='width:" + widthPx + "px;'>" + escapeHtml(text) + "</div></html>";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public void updateAssignmentProgress(int droneId, int zoneId, String severityLabel, int remainingWater, String status) {
        showAssignment(droneId, zoneId, severityLabel, remainingWater, status);
    }

    public void clearAssignment(int droneId) {
        if (assignmentPanel == null) return;
        SwingUtilities.invokeLater(() -> {
            for (Component c : assignmentPanel.getComponents()) {
                if (c instanceof JPanel panel && panel.getName() != null
                        && panel.getName().equals("drone-" + droneId)) {
                    assignmentPanel.remove(panel);
                    assignmentPanel.revalidate();
                    assignmentPanel.repaint();
                    return;
                }
            }
        });
    }

    public void showFireIncident(int zoneId, String severityLabel) {
        Zone zone = zones.get(zoneId);
        if (zone == null) return;
        Point center = toGridCell(zone.centerX(), zone.centerY());
        boolean isNewFire = !fireCells.containsKey(zoneId);
        fireCells.put(zoneId, center);
        fireLabels.put(zoneId, severityLabel == null ? "" : severityLabel);

        // Calculate fire spread based on severity
        List<Point> spreadTiles = calculateFireSpread(center, severityLabel, zone);
        fireSpreadCells.put(zoneId, spreadTiles);

        // Draw all fire tiles
        for (int i = 0; i < spreadTiles.size(); i++) {
            Point p = spreadTiles.get(i);
            // Only show label on center tile
            String label = (p.equals(center)) ? fireLabels.get(zoneId) : "";
            updateFireTile(p.y, p.x, label, getFireColor(i));
        }

        if (isNewFire) {
            incrementActiveFires();
            startFireAnimation();
        }
    }

    private List<Point> calculateFireSpread(Point center, String severity, Zone zone) {
        List<Point> tiles = new ArrayList<>();
        tiles.add(center);

        if (severity == null) return tiles;

        // Determine spread size based on severity
        int spread = switch (severity.toUpperCase()) {
            case "H", "HIGH" -> 1;      // 3x3 = 9 tiles
            case "M", "MODERATE" -> 1;  // 3x3 but sparser
            case "L", "LOW" -> 0;       // just center
            default -> 0;
        };

        if (spread == 0) return tiles;

        // Add surrounding tiles within zone bounds
        int zoneStartCol = zone.xOrigin() / 100;
        int zoneEndCol = zone.xEnd() / 100;
        int zoneStartRow = zone.yOrigin() / 100;
        int zoneEndRow = zone.yEnd() / 100;

        for (int dy = -spread; dy <= spread; dy++) {
            for (int dx = -spread; dx <= spread; dx++) {
                if (dx == 0 && dy == 0) continue; // skip center, already added
                int newRow = center.y + dy;
                int newCol = center.x + dx;
                // Keep within grid and zone bounds
                if (newRow >= 0 && newRow < rows && newCol >= 0 && newCol < cols
                        && newRow >= zoneStartRow && newRow < zoneEndRow
                        && newCol >= zoneStartCol && newCol < zoneEndCol) {
                    // For MODERATE, skip corners to make it look like a + shape
                    if ("M".equalsIgnoreCase(severity) || "MODERATE".equalsIgnoreCase(severity)) {
                        if (Math.abs(dx) == 1 && Math.abs(dy) == 1) continue;
                    }
                    tiles.add(new Point(newCol, newRow));
                }
            }
        }
        return tiles;
    }

    private void startFireAnimation() {
        if (fireAnimationTimer != null && fireAnimationTimer.isRunning()) return;
        if (GraphicsEnvironment.isHeadless()) return;

        fireAnimationTimer = new javax.swing.Timer(FIRE_ANIMATION_INTERVAL_MS, e -> {
            fireAnimationFrame = (fireAnimationFrame + 1) % FIRE_COLORS.length;
            animateAllFires();
        });
        fireAnimationTimer.start();
    }

    private void stopFireAnimationIfNoFires() {
        if (fireSpreadCells.isEmpty() && fireAnimationTimer != null) {
            fireAnimationTimer.stop();
            fireAnimationTimer = null;
        }
    }

    private void animateAllFires() {
        if (tile == null) return;
        for (Map.Entry<Integer, List<Point>> entry : fireSpreadCells.entrySet()) {
            int zoneId = entry.getKey();
            if (droppingDroneZones.containsValue(zoneId)) continue;
            List<Point> tiles = entry.getValue();
            Point center = fireCells.get(zoneId);
            String label = fireLabels.getOrDefault(zoneId, "");

            for (int i = 0; i < tiles.size(); i++) {
                Point p = tiles.get(i);
                // Skip if there's a drone on this tile
                if (isDroneAtCell(p.x, p.y)) continue;
                // Each tile gets a slightly offset color for variety
                int colorIndex = (fireAnimationFrame + i) % FIRE_COLORS.length;
                String tileLabel = p.equals(center) ? label : "";
                updateFireTile(p.y, p.x, tileLabel, FIRE_COLORS[colorIndex]);
            }
        }
    }

    private boolean isDroneAtCell(int col, int row) {
        for (Point dronePos : droneCells.values()) {
            if (dronePos.x == col && dronePos.y == row) return true;
        }
        for (Point faultedPos : faultedDroneCells.values()) {
            if (faultedPos.x == col && faultedPos.y == row) return true;
        }
        return false;
    }

    private Color getFireColor(int offset) {
        return FIRE_COLORS[(fireAnimationFrame + offset) % FIRE_COLORS.length];
    }

    private void drawHomeSpot() {
        if (tile == null) return;
        int row = HOME_CELL.y;
        int col = HOME_CELL.x;
        tile[row][col].setBackground(HOME_FILL);
        tile[row][col].removeAll();
        JLabel homeLabel = new JLabel("HOME");
        homeLabel.setForeground(TEXT_PRIMARY);
        homeLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
        tile[row][col].setLayout(new BorderLayout());
        tile[row][col].add(homeLabel, BorderLayout.CENTER);
        Border gridBorder = BorderFactory.createLineBorder(CELL_BORDER, 1);
        Border outline = BorderFactory.createMatteBorder(2, 2, 2, 2, HOME_OUTLINE);
        tile[row][col].setBorder(BorderFactory.createCompoundBorder(outline, gridBorder));
        tile[row][col].revalidate();
        tile[row][col].repaint();
    }

    private void updateFireTile(int row, int col, String labelData, Color color) {
        if (tile == null) return;
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;
        SwingUtilities.invokeLater(() -> {
            tile[row][col].setBackground(color);
            if (!zoneLabelTiles[row][col]) {
                tile[row][col].removeAll();
                if (labelData != null && !labelData.isEmpty()) {
                    JLabel label = new JLabel(labelData);
                    label.setForeground(Color.WHITE);
                    label.setFont(new Font("SansSerif", Font.BOLD, 11));
                    tile[row][col].setLayout(new BorderLayout());
                    tile[row][col].add(label, BorderLayout.CENTER);
                }
            }
            tile[row][col].revalidate();
            tile[row][col].repaint();
        });
    }

    public void clearFireIncident(int zoneId) {
        Point center = fireCells.remove(zoneId);
        List<Point> spreadTiles = fireSpreadCells.remove(zoneId);
        fireLabels.remove(zoneId);

        if (center == null) return;

        // Clear the fire immediately. Do not leave a lingering completed marker.
        if (spreadTiles != null) {
            for (Point p : spreadTiles) {
                restoreCell(p.y, p.x);
            }
        }

        restoreCell(center.y, center.x);
        clearFireTile(center.y, center.x);
        decrementActiveFires();
        stopFireAnimationIfNoFires();
    }

    /**
     * Shrinks the fire spread for a zone based on how much water has been dropped.
     * Removes outer tiles first, keeping the center tile until fully extinguished.
     *
     * @param zoneId         the zone with the fire
     * @param remainingWater how many litres still needed to extinguish
     * @param totalWater     the original total litres needed for this fire
     */
    public void shrinkFireSpread(int zoneId, int remainingWater, int totalWater) {
        List<Point> spreadTiles = fireSpreadCells.get(zoneId);
        Point center = fireCells.get(zoneId);
        if (spreadTiles == null || center == null || spreadTiles.size() <= 1) return;

        // Calculate how many tiles should remain based on water percentage
        double percentRemaining = (double) remainingWater / totalWater;
        int tilesToKeep = Math.max(1, (int) Math.ceil(spreadTiles.size() * percentRemaining));

        // Remove tiles from the end of the list (outer tiles added last)
        while (spreadTiles.size() > tilesToKeep) {
            Point removed = spreadTiles.remove(spreadTiles.size() - 1);
            // Don't remove the center tile
            if (removed.equals(center)) {
                spreadTiles.add(0, removed); // put it back at the front
                if (spreadTiles.size() <= tilesToKeep) break;
                continue;
            }
            updateTile(TileTypes.NEUTRAL, removed.y, removed.x);
        }
    }

    /**
     * Updates the tracked water level for a drone. Used to display water on drone tiles.
     */
    public void updateDroneWaterLevel(int droneId, double water) {
        droneWaterLevels.put(droneId, water);
    }

    /**
     * Updates the tracked battery and fuel levels for a drone. Updates the drone tile label
     * to reflect current consumable levels. Subclasses may override to display these values
     * in a dedicated status panel.
     *
     * @param droneId    unique drone identifier
     * @param batteryPct battery level in percent (0.0 – 100.0)
     * @param fuelPct    fuel level in percent (0.0 – 100.0)
     */
    public void updateDroneBatteryFuel(int droneId, double batteryPct, double fuelPct) {
        droneBatteryLevels.put(droneId, batteryPct);
        droneFuelLevels.put(droneId, fuelPct);
    }

    /**
     * Formats the label shown on a drone tile using a compact stacked layout so
     * water, battery, and fuel data stay readable inside a single grid cell.
     */
    private String formatDroneLabel(int droneId) {
        Double water   = droneWaterLevels.get(droneId);
        Double battery = droneBatteryLevels.get(droneId);
        Double fuel    = droneFuelLevels.get(droneId);
        if (water != null && battery != null && fuel != null) {
            return "<html><center>D" + droneId
                    + "<br>" + Math.round(water) + "L B" + Math.round(battery) + " F" + Math.round(fuel)
                    + "</center></html>";
        }
        if (water != null) {
            return "<html><center>D" + droneId + "<br>" + Math.round(water) + "L</center></html>";
        }
        return "D" + droneId;
    }

    public void showDronePosition(int droneId, double xMeters, double yMeters) {
        Point nextCell = toGridCell(xMeters, yMeters);
        Point currentCell = droneCells.get(droneId);
        if (currentCell == null) {
            droneCells.put(droneId, nextCell);
            updateTile(TileTypes.DRONE_LOCATION, nextCell.y, nextCell.x, formatDroneLabel(droneId));
            return;
        }
        if (currentCell.equals(nextCell)) {
            // Still update the tile to refresh water level display
            updateTile(TileTypes.DRONE_LOCATION, nextCell.y, nextCell.x, formatDroneLabel(droneId));
            return;
        }

        droneMotionPaths.put(droneId, buildPath(currentCell, nextCell));
        startDroneMotionAnimation();
    }

    private void startDroneMotionAnimation() {
        if (droneMotionTimer != null && droneMotionTimer.isRunning()) return;
        if (GraphicsEnvironment.isHeadless()) return;

        droneMotionTimer = new javax.swing.Timer(DRONE_MOTION_INTERVAL_MS, e -> {
            animateDroneMotions();
        });
        droneMotionTimer.start();
    }

    private void stopDroneMotionAnimationIfIdle() {
        if (droneMotionPaths.isEmpty() && droneMotionTimer != null) {
            droneMotionTimer.stop();
            droneMotionTimer = null;
        }
    }

    private void animateDroneMotions() {
        List<Integer> finished = new ArrayList<>();

        for (Map.Entry<Integer, Deque<Point>> entry : droneMotionPaths.entrySet()) {
            int droneId = entry.getKey();
            Deque<Point> path = entry.getValue();
            Point currentCell = droneCells.get(droneId);
            if (currentCell == null) {
                finished.add(droneId);
                continue;
            }

            while (!path.isEmpty()) {
                Point nextCell = path.pollFirst();
                if (nextCell == null || nextCell.equals(currentCell)) {
                    continue;
                }
                restoreCell(currentCell.y, currentCell.x);
                droneCells.put(droneId, nextCell);
                if (droppingDroneCells.containsKey(droneId)) {
                    droppingDroneCells.put(droneId, nextCell);
                    updateDroppingDroneTile(nextCell.y, nextCell.x, formatDroneLabel(droneId),
                            WATER_DROP_DRONE_COLORS[waterDropFrame]);
                } else {
                    updateTile(TileTypes.DRONE_LOCATION, nextCell.y, nextCell.x, formatDroneLabel(droneId));
                }
                currentCell = nextCell;
                break;
            }

            if (path.isEmpty()) {
                finished.add(droneId);
            }
        }

        for (Integer droneId : finished) {
            droneMotionPaths.remove(droneId);
        }
        stopDroneMotionAnimationIfIdle();
    }

    private Deque<Point> buildPath(Point start, Point end) {
        Deque<Point> path = new ArrayDeque<>();
        int dx = end.x - start.x;
        int dy = end.y - start.y;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            return path;
        }

        for (int i = 1; i <= steps; i++) {
            int col = (int) Math.round(start.x + (dx * (i / (double) steps)));
            int row = (int) Math.round(start.y + (dy * (i / (double) steps)));
            Point step = new Point(col, row);
            if (path.isEmpty() || !path.peekLast().equals(step)) {
                path.addLast(step);
            }
        }
        return path;
    }

    /**
     * Marks the current grid cell of a drone with the {@link TileTypes#DRONE_FAULTED} colour,
     * indicating that the drone has experienced a fault.
     * If the drone has no known position, this method does nothing.
     *
     * @param droneId   the ID of the faulted drone
     * @param faultName a short fault-type label shown on the tile (e.g., "STUCK")
     */
    public void showDroneFault(int droneId, String faultName) {
        Point cell = droneCells.get(droneId);
        if (cell == null) return;
        droneMotionPaths.remove(droneId);
        droppingDroneCells.remove(droneId);
        droppingDroneZones.remove(droneId);
        // Track this faulted drone's position so other drones don't overwrite it
        faultedDroneCells.put(droneId, cell);
        faultedDroneFaults.put(droneId, faultName);
        String label = "D" + droneId;
        if (faultName != null && !faultName.isBlank()) {
            label = label + ":" + faultName;
        }
        updateFaultTile(cell.y, cell.x, label, resolveFaultColor(faultName));
    }

    public void showDroneOffline(int droneId) {
        showDroneFault(droneId, "OFFLINE");
    }

    /**
     * Shows a drone actively dropping water on a fire. The drone tile pulses
     * between blue and cyan, and surrounding fire tiles show water splash effects.
     *
     * @param droneId the ID of the drone dropping water
     * @param zoneId  the zone where the drone is dropping water
     */
    public void showDroneDropping(int droneId, int zoneId) {
        Point cell = droneCells.get(droneId);
        if (cell == null) return;
        droppingDroneCells.put(droneId, cell);
        droppingDroneZones.put(droneId, zoneId);
        startWaterDropAnimation();
    }

    /**
     * Clears the water dropping effect for a drone that finished dropping.
     *
     * @param droneId the drone that stopped dropping water
     */
    public void clearDroneDropping(int droneId) {
        droppingDroneCells.remove(droneId);
        droppingDroneZones.remove(droneId);
        stopWaterDropAnimationIfNone();
    }

    private void startWaterDropAnimation() {
        if (waterDropTimer != null && waterDropTimer.isRunning()) return;
        if (GraphicsEnvironment.isHeadless()) return;

        waterDropTimer = new javax.swing.Timer(WATER_DROP_INTERVAL_MS, e -> {
            waterDropFrame = (waterDropFrame + 1) % WATER_DROP_DRONE_COLORS.length;
            animateAllWaterDrops();
        });
        waterDropTimer.start();
    }

    private void stopWaterDropAnimationIfNone() {
        if (droppingDroneCells.isEmpty() && waterDropTimer != null) {
            waterDropTimer.stop();
            waterDropTimer = null;
        }
    }

    private void animateAllWaterDrops() {
        if (tile == null) return;
        for (Map.Entry<Integer, Point> entry : droppingDroneCells.entrySet()) {
            int droneId = entry.getKey();
            Point dronePos = entry.getValue();
            Integer zoneId = droppingDroneZones.get(droneId);

            // Animate the drone tile with pulsing blue/cyan
            Color droneColor = WATER_DROP_DRONE_COLORS[waterDropFrame];
            updateDroppingDroneTile(dronePos.y, dronePos.x, formatDroneLabel(droneId), droneColor);

            // Animate surrounding fire tiles with water splash only (no fire colors)
            if (zoneId != null) {
                List<Point> fireTiles = fireSpreadCells.get(zoneId);
                if (fireTiles != null) {
                    for (int i = 0; i < fireTiles.size(); i++) {
                        Point firePos = fireTiles.get(i);
                        // Skip the cell where the drone is
                        if (firePos.x == dronePos.x && firePos.y == dronePos.y) continue;
                        // Cycle through water splash colors only
                        int splashIndex = (waterDropFrame + i) % WATER_SPLASH_COLORS.length;
                        updateFireTile(firePos.y, firePos.x, "", WATER_SPLASH_COLORS[splashIndex]);
                    }
                }
            }
        }
    }

    private void updateDroppingDroneTile(int row, int col, String labelData, Color color) {
        if (tile == null) return;
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;
        SwingUtilities.invokeLater(() -> {
            tile[row][col].setBackground(color);
            if (!zoneLabelTiles[row][col]) {
                tile[row][col].removeAll();
                JLabel label = new JLabel(labelData);
                label.setFont(new Font("SansSerif", Font.BOLD, 10));
                label.setForeground(Color.WHITE);
                tile[row][col].setLayout(new BorderLayout());
                tile[row][col].add(label, BorderLayout.NORTH);
            }
            tile[row][col].revalidate();
            tile[row][col].repaint();
        });
    }

    public void clearDroneFault(int droneId) {
        droneMotionPaths.remove(droneId);
        faultedDroneCells.remove(droneId);
        faultedDroneFaults.remove(droneId);
    }

    public void startDroneFaultCountdown(int droneId, String faultName, int secondsRemaining) {
        // Default no-op; richer GUI implementations can show countdowns.
    }

    private void updateFaultTile(int row, int col, String labelData, Color faultColor) {
        if (tile == null) return;
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;
        SwingUtilities.invokeLater(() -> {
            tile[row][col].setBackground(faultColor);
            if (!zoneLabelTiles[row][col]) {
                tile[row][col].removeAll();
                JLabel idLabel = new JLabel(labelData);
                idLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
                idLabel.setForeground(Color.WHITE);
                tile[row][col].setLayout(new BorderLayout());
                tile[row][col].add(idLabel, BorderLayout.NORTH);
            }
            tile[row][col].revalidate();
            tile[row][col].repaint();
        });
    }

    private Color resolveFaultColor(String faultName) {
        if (faultName == null) {
            return TileTypes.DRONE_FAULTED.getColor();
        }
        return switch (faultName) {
            case "DRONE_STUCK", "DRONE_TIMEOUT" -> FAULT_STUCK;
            case "NOZZLE_STUCK_CLOSED" -> FAULT_NOZZLE_CLOSED;
            case "NOZZLE_STUCK_OPEN" -> FAULT_NOZZLE_OPEN;
            case "PACKET_LOSS" -> FAULT_PACKET_LOSS;
            case "PACKET_CORRUPTION", "COMMUNICATION_ERROR" -> FAULT_PACKET_CORRUPTION;
            case "OFFLINE", "MISSION_FAILURE" -> FAULT_OFFLINE;
            default -> TileTypes.DRONE_FAULTED.getColor();
        };
    }

    public void clearDronePosition(int droneId) {
        droneMotionPaths.remove(droneId);
        droppingDroneCells.remove(droneId);
        Point cell = droneCells.remove(droneId);
        if (cell != null) {
            restoreCell(cell.y, cell.x);
        }
        stopDroneMotionAnimationIfIdle();
    }

    protected Point toGridCell(double xMeters, double yMeters) {
        int col = Math.max(0, Math.min(cols - 1, (int) (xMeters / 100.0)));
        int row = Math.max(0, Math.min(rows - 1, (int) (yMeters / 100.0)));
        return new Point(col, row);
    }

    private void restoreCell(int row, int col) {
        // Check if there's a faulted drone at this cell - don't overwrite it
        for (Map.Entry<Integer, Point> entry : faultedDroneCells.entrySet()) {
            if (entry.getValue().x == col && entry.getValue().y == row) {
                String faultName = faultedDroneFaults.get(entry.getKey());
                String label = "D" + entry.getKey();
                if (faultName != null && !faultName.isBlank()) {
                    label = label + ":" + faultName;
                }
                updateFaultTile(row, col, label, resolveFaultColor(faultName));
                return;
            }
        }
        // Check if there's a fire at this cell (including spread tiles)
        for (Map.Entry<Integer, List<Point>> entry : fireSpreadCells.entrySet()) {
            int zoneId = entry.getKey();
            for (Point p : entry.getValue()) {
                if (p.x == col && p.y == row) {
                    Point center = fireCells.get(zoneId);
                    String label = (center != null && center.x == col && center.y == row)
                            ? fireLabels.getOrDefault(zoneId, "") : "";
                    updateFireTile(row, col, label, getFireColor(0));
                    return;
                }
            }
        }
        if (HOME_CELL.x == col && HOME_CELL.y == row) {
            drawHomeSpot();
            return;
        }
        updateTile(TileTypes.NEUTRAL, row, col);;
    }

    public void drawZone(int zoneID, int startX, int startY, int endXCoord, int endYCoord, Color color) {
        if (tile == null) return; // headless guard
        int x = startX / 100;
        int y = startY / 100;
        int endXExclusive = endXCoord / 100;
        int endYExclusive = endYCoord / 100;

        if (endXExclusive <= x || endYExclusive <= y) return;

        int endX = endXExclusive - 1;
        int endY = endYExclusive - 1;

        for (int row = y; row <= endY; row++) {
            for (int col = x; col <= endX; col++) {
                if (row < 0 || row >= rows || col < 0 || col >= cols) continue;

                boolean topEdge = row == y;
                boolean bottomEdge = row == endY;
                boolean leftEdge = col == x;
                boolean rightEdge = col == endX;

                // Only edge cells need a special border
                if (topEdge || bottomEdge || leftEdge || rightEdge) {
                    Border blackGrid = BorderFactory.createLineBorder(CELL_BORDER, 1);

                    Border colouredOutline = BorderFactory.createMatteBorder(
                            topEdge ? 2 : 0,
                            leftEdge ? 2 : 0,
                            bottomEdge ? 2 : 0,
                            rightEdge ? 2 : 0,
                            color
                    );

                    tile[row][col].setBorder(BorderFactory.createCompoundBorder(colouredOutline, blackGrid));
                }
            }
        }

        //putting the label in the top left
        if (y >= 0 && y < rows && x >= 0 && x < cols) {
            String s = "Z"+zoneID;
            tile[y][x].removeAll();
            JLabel zoneIdentifier = new JLabel(s);
            zoneIdentifier.setFont(new Font("SansSerif" , Font.BOLD, 11));
            zoneIdentifier.setForeground(TEXT_PRIMARY);
            tile[y][x].setLayout(new BorderLayout());
            tile[y][x].add(zoneIdentifier, BorderLayout.NORTH);
            tile[y][x].revalidate();
            tile[y][x].repaint();
            zoneLabelTiles[y][x] = true;
        }
    }

    private void addLegendItem(JPanel panel, GridBagConstraints legendConstraints, Color color, String text) {
        // Rounded color swatch
        JPanel colorBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(color);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2d.setColor(new Color(80, 85, 95));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                g2d.dispose();
            }
        };
        colorBox.setOpaque(false);
        colorBox.setPreferredSize(new Dimension(18, 18));
        colorBox.setMinimumSize(new Dimension(18, 18));
        colorBox.setMaximumSize(new Dimension(18, 18));

        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(TEXT_SECONDARY);

        legendConstraints.gridx = 0;
        panel.add(colorBox, legendConstraints);

        legendConstraints.gridx = 1;
        panel.add(label, legendConstraints);

        legendConstraints.gridy++;
    }

    private void addLegendTextEntry(JPanel panel, GridBagConstraints legendConstraints, String symbol, String text) {
        // Rounded symbol box
        JPanel box = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(55, 60, 70));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2d.setColor(new Color(80, 85, 95));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2d.dispose();
            }
        };
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(28, 22));
        box.setMinimumSize(new Dimension(28, 22));
        box.setMaximumSize(new Dimension(28, 22));

        JLabel symbolLabel = new JLabel(symbol);
        symbolLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        symbolLabel.setForeground(TEXT_PRIMARY);
        box.add(symbolLabel);

        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(TEXT_SECONDARY);

        legendConstraints.gridx = 0;
        panel.add(box, legendConstraints);

        legendConstraints.gridx = 1;
        panel.add(label, legendConstraints);

        legendConstraints.gridy++;
    }

    /** ABSTRACT METHODS FOR GUI TESTING **/

    public abstract void clearFireTile(int row, int col);
    public abstract void updateDroneState(int droneId, DroneState state);
    public abstract void updateDroneWater(int droneId, double water);
    public abstract void updateDronePosition(int droneId, double x, double y);
    public abstract void incrementActiveFires();
    public abstract void decrementActiveFires();
    public abstract void logEvent(String message);

    public void logTelemetry(String message) {
        logEvent(message);
    }

    public void logQueueEvent(String message) {
        logEvent(message);
    }

    public void logAssignmentHistory(String message) {
        logEvent(message);
    }

    /**
     * Updates the metrics display. Default does nothing; RuntimeGUI overrides.
     */
    public void updateMetrics(String metricsText) {
        // Default implementation does nothing
    }

    private String formatAssignmentText(int droneId, int zoneId, String severityLabel, int remainingWater, String status) {
        String severity = severityLabel == null || severityLabel.isBlank() ? "?" : severityLabel;
        String waterText = remainingWater >= 0 ? remainingWater + "L left" : "pending";
        String statusText = status == null || status.isBlank() ? "active" : status;
        return "D" + droneId + " | Z" + zoneId + " | " + severity + " | " + waterText + " | " + statusText;
    }

    /**
     * Formats the assignment text for a drone, including battery and fuel levels.
     */
    private String formatAssignmentText(int droneId, int zoneId, String severityLabel,
                                        int remainingWater, String status,
                                        double batteryPct, double fuelPct) {
        String base = formatAssignmentText(droneId, zoneId, severityLabel, remainingWater, status);
        return base + " | B:" + String.format("%.0f", batteryPct) + "% F:" + String.format("%.0f", fuelPct) + "%";
    }

    // ========== CUSTOM UI COMPONENTS FOR VISUAL POLISH ==========

    /**
     * A JPanel that paints a vertical gradient background.
     */
    static class GradientPanel extends JPanel {
        private final Color topColor;
        private final Color bottomColor;

        GradientPanel(Color top, Color bottom) {
            this.topColor = top;
            this.bottomColor = bottom;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            GradientPaint gradient = new GradientPaint(
                    0, 0, topColor,
                    0, getHeight(), bottomColor
            );
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * A border that draws a subtle drop shadow around the component.
     */
    static class ShadowBorder implements Border {
        private final int shadowSize;
        private final Color shadowColor;
        private final int cornerRadius;

        ShadowBorder(int shadowSize, Color shadowColor, int cornerRadius) {
            this.shadowSize = shadowSize;
            this.shadowColor = shadowColor;
            this.cornerRadius = cornerRadius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw shadow layers (getting more transparent further out)
            for (int i = 0; i < shadowSize; i++) {
                float alpha = (float) (shadowSize - i) / (shadowSize * 3f);
                g2d.setColor(new Color(
                        shadowColor.getRed(),
                        shadowColor.getGreen(),
                        shadowColor.getBlue(),
                        (int) (alpha * 255)
                ));
                g2d.drawRoundRect(
                        x + i, y + i,
                        width - 1 - (i * 2), height - 1 - (i * 2),
                        cornerRadius, cornerRadius
                );
            }
            g2d.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(shadowSize, shadowSize, shadowSize, shadowSize);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    /**
     * A border with rounded corners.
     */
    static class RoundedBorder implements Border {
        private final int radius;
        private final Color color;
        private final int thickness;

        RoundedBorder(int radius, Color color, int thickness) {
            this.radius = radius;
            this.color = color;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(thickness));
            g2d.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2d.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 2, thickness + 2, thickness + 2, thickness + 2);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    /**
     * Creates a compound border with shadow, rounded corners, and padding.
     */
    static Border createStyledBorder(int shadowSize, int cornerRadius, int padding) {
        return BorderFactory.createCompoundBorder(
                new ShadowBorder(shadowSize, new Color(0, 0, 0, 80), cornerRadius),
                BorderFactory.createCompoundBorder(
                        new RoundedBorder(cornerRadius, new Color(70, 75, 85), 1),
                        BorderFactory.createEmptyBorder(padding, padding, padding, padding)
                )
        );
    }
}
