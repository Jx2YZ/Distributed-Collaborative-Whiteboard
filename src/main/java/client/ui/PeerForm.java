/**
 * Main GUI window for the whiteboard peer: user or manager. Also handles UI initialization, RMI connection, user events, and session management.
 * Jx2
 * v.22/05/2026
 **/

package client.ui;

import base.Constants;
import base.SerializableImage;
import base.UserRole;
import base.primitives.PreviewPrimitive;
import base.primitives.Primitive;
import base.rmi.BoardService;
import base.rmi.SessionService;
import base.rmi.UserNotFoundException;
import client.SessionCallbackBase;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.IntStream;

class PresetColor extends ImmutablePair<String, Color> {
    public PresetColor(String left, Color right) { super(left, right); }
    public static PresetColor of(String left, Color right) { return new PresetColor(left, right); }
    public String getName() { return left; }
    public Color getColor() { return right; }
    @Override public String toString() { return left; }
}

public class PeerForm extends JFrame {

    private static final Logger logger = Logger.getLogger(PeerForm.class.getName());

    private static final List<PresetColor> presetColors = Arrays.asList(
            PresetColor.of("Black", Color.BLACK),
            PresetColor.of("Red", Color.RED),
            PresetColor.of("Green", Color.GREEN),
            PresetColor.of("Blue", Color.BLUE),
            PresetColor.of("Cyan", Color.CYAN),
            PresetColor.of("Magenta", Color.MAGENTA),
            PresetColor.of("Orange", Color.ORANGE),
            PresetColor.of("Yellow", Color.YELLOW),
            PresetColor.of("Pink", Color.PINK),
            PresetColor.of("Dark Gray", Color.DARK_GRAY),
            PresetColor.of("Gray", Color.GRAY),
            PresetColor.of("Light Gray", Color.LIGHT_GRAY),
            PresetColor.of("White", Color.WHITE),
            PresetColor.of("Brown", new Color(165, 42, 42)),
            PresetColor.of("Purple", new Color(128, 0, 128)),
            PresetColor.of("Teal", new Color(0, 128, 128))
    );

    // UI components initialized by $$$setupUI$$$
    private JPanel contentPane;
    private JButton chatSendButton;
    private JLabel userNameLabel;
    private JComboBox colorComboBox;
    private JList usersListView;
    private JRadioButton penRadioButton;
    private JRadioButton eraseRadioButton;
    private JRadioButton lineRadioButton;
    private JRadioButton triangleRadioButton;
    private JRadioButton circleRadioButton;
    private JRadioButton rectangleRadioButton;
    private JRadioButton textRadioButton;
    private JSpinner eraseSizeSpinner;
    private JTextField chatTextField;
    private JList chatHistoryListView;
    private JScrollPane canvasScrollPane;
    private WhiteBoardCanvasPanel canvasPanel;
    private JLabel statusBarLabel;

    private final JMenuBar menuBar = new JMenuBar();

    private DefaultListModel<Pair<UserRole, String>> usersListModel = new DefaultListModel<>();
    private DefaultListModel<Pair<String, String>> chatHistoryListModel = new DefaultListModel<>();

    private final boolean isManager;
    private final String rmiIpAddress;
    private final int rmiPort;
    private final String userName;
    private UUID peerId;
    private Registry registry;
    private SessionService sessionService;
    private BoardService boardService;

    // File path remembered across Save/Save As.
    private File savedCanvasFile = null;

    private ScheduledExecutorService heartbeatScheduler;

    // Tracks the highest preview sequence number seen per sender to drop stale deliveries.
    private final ConcurrentHashMap<UUID, Integer> previewLastSeq = new ConcurrentHashMap<>();
    // Maps username to sender UUID so previews can be cleared when a user leaves.
    private final ConcurrentHashMap<String, UUID> userNameToUuid = new ConcurrentHashMap<>();

    public PeerForm(String title, boolean isManager, String serverIpAddress, int serverPort, String userName) {
        super(title);
        this.isManager = isManager;
        this.rmiIpAddress = serverIpAddress;
        this.rmiPort = serverPort;
        this.userName = userName;

        $$$setupUI$$$();
        initializeUI();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() {
                        try {
                            SessionService.SessionCallback sessionCallback = new SessionCallbackBase() {
                                @Override
                                public boolean onIncomingRequest(String userName) throws RemoteException {
                                    var result = JOptionPane.showConfirmDialog(PeerForm.this,
                                            String.format("User %s wants to join. Allow?", userName),
                                            "Join Request", JOptionPane.YES_NO_OPTION);
                                    return switch (result) {
                                        case JOptionPane.YES_OPTION -> true;
                                        case JOptionPane.NO_OPTION, JOptionPane.CANCEL_OPTION,
                                             JOptionPane.CLOSED_OPTION -> false;
                                        default -> throw new UnexpectedException("Unexpected");
                                    };
                                }

                                @Override
                                public void onCanvasSwitched(String peerName) throws RemoteException {
                                    var boardSvc = boardService;
                                    if (boardSvc != null) {
                                        synchronized (canvasPanel) {
                                            canvasPanel.setBufferedImage(boardSvc.getCurrentCanvas().getImage());
                                        }
                                    }
                                }

                                @Override
                                public void onPrimitiveAdded(UUID peerId, String peerName, Primitive primitive) throws RemoteException {
                                    if (primitive instanceof PreviewPrimitive preview) {
                                        // Skip our own previews — the local canvas already renders them from strokePoints.
                                        if (peerId.equals(PeerForm.this.peerId)) return;
                                        int seq = preview.getSequenceNumber();
                                        Integer last = previewLastSeq.get(peerId);
                                        if (last != null && seq <= last) return; // stale, drop it
                                        previewLastSeq.put(peerId, seq);
                                        userNameToUuid.put(peerName, peerId);
                                        canvasPanel.setPreview(peerId, preview);
                                    } else {
                                        // Final commit: clear sender overlay then render to canvas.
                                        canvasPanel.clearPreview(peerId);
                                        canvasPanel.drawPrimitive(primitive);
                                    }
                                }

                                @Override
                                public void onChatMessageAdded(String peerName, String chatMessage) throws RemoteException {
                                    chatHistoryListModel.addElement(Pair.of(peerName, chatMessage));
                                }

                                @Override
                                public void onUsersChanged(Collection<Pair<UserRole, String>> peers) throws RemoteException {
                                    // Clear previews for users who have left.
                                    Set<String> newNames = new HashSet<>();
                                    for (var p : peers) newNames.add(p.getRight());
                                    var iter = userNameToUuid.entrySet().iterator();
                                    while (iter.hasNext()) {
                                        var entry = iter.next();
                                        if (!newNames.contains(entry.getKey())) {
                                            canvasPanel.clearPreview(entry.getValue());
                                            previewLastSeq.remove(entry.getValue());
                                            iter.remove();
                                        }
                                    }
                                    updateUsers(peers);
                                }

                                @Override
                                public void onKickedOut(String managerName) throws RemoteException {
                                    SwingUtilities.invokeLater(() -> {
                                        JOptionPane.showMessageDialog(PeerForm.this,
                                                "You have been kicked out by the manager.",
                                                "Kicked Out", JOptionPane.INFORMATION_MESSAGE);
                                        dispose();
                                    });
                                }

                                @Override
                                public void onTerminated() throws RemoteException {
                                    SwingUtilities.invokeLater(() -> {
                                        JOptionPane.showMessageDialog(PeerForm.this,
                                                "The session has ended.",
                                                "Session Ended", JOptionPane.INFORMATION_MESSAGE);
                                        dispose();
                                    });
                                }
                            };

                            publish("Connecting to " + rmiIpAddress + ":" + rmiPort);
                            registry = LocateRegistry.getRegistry(rmiIpAddress, rmiPort);
                            sessionService = (SessionService) registry.lookup("rmi:///SessionService");
                            boardService = (BoardService) registry.lookup("rmi:///BoardService");

                            UUID peerId;
                            try {
                                if (isManager) {
                                    publish("Connecting as manager");
                                    peerId = sessionService.create(userName, sessionCallback);
                                } else {
                                    publish("Waiting for manager approval");
                                    peerId = sessionService.join(userName, sessionCallback);
                                    if (peerId == null) {
                                        SwingUtilities.invokeLater(() -> {
                                            JOptionPane.showMessageDialog(PeerForm.this,
                                                    "Join request was rejected.", "Rejected",
                                                    JOptionPane.INFORMATION_MESSAGE);
                                            PeerForm.this.dispose();
                                        });
                                        return null;
                                    }
                                }
                                publish("Peer ID: " + peerId);
                            } catch (SessionService.UserAlreadyExistsException |
                                     SessionService.ManagerLimitExceededException |
                                     SessionService.NoManagerAvailableException ex) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(PeerForm.this, ex.getMessage(),
                                            ex.getClass().getSimpleName(), JOptionPane.ERROR_MESSAGE);
                                    PeerForm.this.dispose();
                                });
                                return null;
                            }

                            if (peerId != null) {
                                sessionService.heartbeat(peerId);
                                if (isManager) boardService.clear(peerId);
                            }
                            PeerForm.this.peerId = peerId;
                            canvasPanel.setPeerId(peerId);
                            canvasPanel.setBoardService(boardService);
                            publish("Ready");

                            // Send a heartbeat every HEARTBEAT_INTERVAL_MS so the server knows this peer is alive.
                            final UUID registeredId = peerId;
                            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                                Thread t = new Thread(r, "heartbeat");
                                t.setDaemon(true);
                                return t;
                            });
                            heartbeatScheduler.scheduleAtFixedRate(() -> {
                                try {
                                    var svc = sessionService;
                                    if (svc != null) svc.heartbeat(registeredId);
                                } catch (RemoteException e) {
                                    logger.fine("heartbeat: " + e.getMessage());
                                }
                            }, Constants.HEARTBEAT_INTERVAL_MS, Constants.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

                        } catch (RemoteException | NotBoundException | UserNotFoundException ex) {
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(PeerForm.this, ex.getMessage(),
                                        ex.getClass().getSimpleName(), JOptionPane.ERROR_MESSAGE);
                                PeerForm.this.dispose();
                            });
                        }
                        return null;
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        for (var chunk : chunks) statusBarLabel.setText(chunk);
                    }

                    @Override
                    protected void done() {
                        if (peerId == null) return;
                        try {
                            var sessionSvc = sessionService;
                            if (sessionSvc != null) {
                                updateUsers(sessionSvc.getUsers());
                                updateChatMessages(sessionSvc.getChatMessages());
                                var boardSvc = boardService;
                                if (boardSvc != null) {
                                    synchronized (canvasPanel) {
                                        canvasPanel.setBufferedImage(boardSvc.getCurrentCanvas().getImage());
                                    }
                                }
                            }
                        } catch (RemoteException ex) {
                            logger.warning("%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()));
                        }
                    }
                }.execute();

                super.windowOpened(e);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                if (heartbeatScheduler != null) {
                    heartbeatScheduler.shutdownNow();
                    heartbeatScheduler = null;
                }
                if (PeerForm.this.sessionService != null && PeerForm.this.peerId != null) {
                    try {
                        PeerForm.this.sessionService.leave(peerId);
                    } catch (RemoteException | UserNotFoundException ex) {
                        logger.warning("leave(%s): %s".formatted(peerId, ex.getMessage()));
                    }
                }
                PeerForm.this.sessionService = null;
                PeerForm.this.boardService = null;
                canvasPanel.setPeerId(null);
                canvasPanel.setBoardService(null);
                System.gc();
                super.windowClosed(e);
                System.exit(0);
            }
        });
    }

    private void initializeUI() {
        if (isManager) userNameLabel.setForeground(Color.RED);
        userNameLabel.setText(userName);

        chatSendButton.addActionListener(e -> {
            if (sessionService != null && peerId != null) {
                try {
                    sessionService.sendChatMessage(peerId, chatTextField.getText());
                    chatTextField.setText("");
                } catch (RemoteException | UserNotFoundException ex) {
                    logger.warning("sendChatMessage: " + ex.getMessage());
                }
            }
        });

        usersListView.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                var v = (Pair<UserRole, String>) value;
                if (v.getRight().equals(userName)) setFont(getFont().deriveFont(Font.BOLD));
                if (v.getLeft() == UserRole.MANAGER) setForeground(Color.RED);
                setText(v.getRight());
                return this;
            }
        });

        chatHistoryListView.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                var v = (Pair<String, String>) value;
                setText("<html><font color='blue'>%s</font> %s</html>".formatted(v.getLeft(), v.getRight()));
                return this;
            }
        });

        usersListView.setModel(usersListModel);
        chatHistoryListView.setModel(chatHistoryListModel);

        // Manager right-click to kick out users.
        if (isManager) {
            JPopupMenu kickMenu = new JPopupMenu();
            JMenuItem kickItem = new JMenuItem("Kick out");
            kickMenu.add(kickItem);

            usersListView.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!SwingUtilities.isRightMouseButton(e)) return;
                    int idx = usersListView.locationToIndex(e.getPoint());
                    if (idx < 0) return;
                    usersListView.setSelectedIndex(idx);
                    var selected = (Pair<UserRole, String>) usersListView.getSelectedValue();
                    if (selected == null || selected.getRight().equals(userName)) return;
                    kickItem.setActionCommand(selected.getRight());
                    kickMenu.show(usersListView, e.getX(), e.getY());
                }
            });

            kickItem.addActionListener(e -> {
                String target = e.getActionCommand();
                new Thread(() -> {
                    try {
                        if (sessionService != null && peerId != null) {
                            sessionService.kickOut(peerId, target);
                        }
                    } catch (RemoteException | UserNotFoundException ex) {
                        logger.warning("kickOut(%s): %s".formatted(target, ex.getMessage()));
                    }
                }).start();
            });
        }

        // Tool selection radio buttons.
        ButtonGroup group = new ButtonGroup();
        for (var pair : Arrays.asList(
                Pair.of(penRadioButton,       WhiteBoardCanvasPanel.Mode.PEN),
                Pair.of(eraseRadioButton,     WhiteBoardCanvasPanel.Mode.ERASE),
                Pair.of(lineRadioButton,      WhiteBoardCanvasPanel.Mode.LINE),
                Pair.of(triangleRadioButton,  WhiteBoardCanvasPanel.Mode.TRIANGLE),
                Pair.of(circleRadioButton,    WhiteBoardCanvasPanel.Mode.CIRCLE),
                Pair.of(rectangleRadioButton, WhiteBoardCanvasPanel.Mode.RECTANGLE),
                Pair.of(textRadioButton,      WhiteBoardCanvasPanel.Mode.TEXT)
        )) {
            var btn = pair.getLeft();
            group.add(btn);
            btn.setSelected(canvasPanel.getMode() == pair.getRight());
            btn.addItemListener(ev -> {
                if (ev.getStateChange() == ItemEvent.SELECTED) canvasPanel.setMode(pair.getRight());
            });
        }

        eraseSizeSpinner.setModel(new SpinnerNumberModel(canvasPanel.getEraseSize(), 1, 100, 5));
        for (var pc : presetColors) colorComboBox.addItem(pc);
        colorComboBox.setSelectedIndex(IntStream
                .range(0, presetColors.size())
                .filter(i -> presetColors.get(i).getColor().equals(canvasPanel.getColor()))
                .findFirst().orElse(0));

        eraseSizeSpinner.addChangeListener(e -> canvasPanel.setEraseSize((int) eraseSizeSpinner.getValue()));
        colorComboBox.addActionListener(e -> {
            PresetColor v = (PresetColor) colorComboBox.getSelectedItem();
            if (v != null) canvasPanel.setColor(v.getColor());
        });

        initializeMenu();

        setJMenuBar(menuBar);
        setContentPane(contentPane);
        getRootPane().setDefaultButton(chatSendButton);
    }

    private void initializeMenu() {
        var fileMenu = new JMenu("File");

        if (isManager) {
            var newItem = new JMenuItem("New");
            newItem.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(this,
                        "Clear the canvas? This will affect all peers.", "New Canvas",
                        JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION && boardService != null && peerId != null) {
                    new Thread(() -> {
                        try {
                            boardService.clear(peerId);
                            savedCanvasFile = null;
                        } catch (RemoteException | UserNotFoundException ex) {
                            logger.warning("clear: " + ex.getMessage());
                        }
                    }).start();
                }
            });

            var openItem = new JMenuItem("Open");
            openItem.addActionListener(e -> {
                var chooser = new JFileChooser();
                // Start in the same directory as the last saved/opened file so the user can find it easily.
                if (savedCanvasFile != null) chooser.setCurrentDirectory(savedCanvasFile.getParentFile());
                chooser.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                File file = chooser.getSelectedFile();
                new Thread(() -> {
                    try {
                        BufferedImage img = ImageIO.read(file);
                        if (img == null) {
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Unsupported image format."));
                            return;
                        }
                        if (boardService != null && peerId != null) {
                            boardService.setCurrentCanvas(peerId, new SerializableImage(img));
                            savedCanvasFile = file;
                        }
                    } catch (IOException | UserNotFoundException ex) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "Open failed: " + ex.getMessage()));
                    }
                }).start();
            });

            var saveItem = new JMenuItem("Save");
            saveItem.addActionListener(e -> {
                if (savedCanvasFile != null) saveToFile(savedCanvasFile);
                else doSaveAs();
            });

            var saveAsItem = new JMenuItem("Save As");
            saveAsItem.addActionListener(e -> doSaveAs());

            // Close ends the session for all peers also same as manager closing the window, yet with a prompt.
            var closeItem = new JMenuItem("Close");
            closeItem.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(this,
                        "Close the session? All connected peers will be disconnected.", "Close Session",
                        JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) dispose();
            });

            fileMenu.add(newItem);
            fileMenu.add(openItem);
            fileMenu.add(saveItem);
            fileMenu.add(saveAsItem);
            fileMenu.addSeparator();
            fileMenu.add(closeItem);
            fileMenu.addSeparator();
        }

        var exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispose());
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
    }

    // Writes the current canvas to file as PNG.
    private void saveToFile(File file) {
        BufferedImage img = canvasPanel.getBufferedImage();
        if (img == null) return;
        try {
            boolean ok = ImageIO.write(img, "png", file);
            if (!ok) {
                JOptionPane.showMessageDialog(this, "Save failed: no PNG writer available.");
                return;
            }
            savedCanvasFile = file;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    private void doSaveAs() {
        var chooser = new JFileChooser();
        if (savedCanvasFile != null) chooser.setSelectedFile(savedCanvasFile);
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().contains(".")) file = new File(file.getParentFile(), file.getName() + ".png");
        saveToFile(file);
    }

    private void updateUsers(Collection<Pair<UserRole, String>> users) {
        usersListModel.clear();
        for (var user : users) usersListModel.addElement(user);
    }

    private void updateChatMessages(Collection<Pair<String, String>> chatMessages) {
        chatHistoryListModel.clear();
        for (var pair : chatMessages) chatHistoryListModel.addElement(pair);
    }

    // Builds all Swing components from scratch; called from instance initializer before constructor body.
    private void $$$setupUI$$$() {
        // Leaf components
        statusBarLabel   = new JLabel("Ready");
        userNameLabel    = new JLabel("User Name");
        usersListView    = new JList<>();
        chatHistoryListView = new JList<>();
        chatTextField    = new JTextField();
        chatSendButton   = new JButton("Send");
        penRadioButton       = new JRadioButton("Pen");
        eraseRadioButton     = new JRadioButton("Erase");
        lineRadioButton      = new JRadioButton("Line");
        triangleRadioButton  = new JRadioButton("Triangle");
        circleRadioButton    = new JRadioButton("Circle");
        rectangleRadioButton = new JRadioButton("Rectangle");
        textRadioButton      = new JRadioButton("Text");
        eraseSizeSpinner = new JSpinner();
        colorComboBox    = new JComboBox<>();
        canvasPanel      = new WhiteBoardCanvasPanel();

        // Canvas scroll pane
        canvasScrollPane = new JScrollPane(canvasPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Toolbar: row 1 got drawing mode buttons
        JPanel radioRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (JRadioButton btn : new JRadioButton[]{penRadioButton, eraseRadioButton, lineRadioButton,
                triangleRadioButton, circleRadioButton, rectangleRadioButton, textRadioButton}) {
            radioRow.add(btn);
        }

        // Toolbar: row 2 got eraser size & colour picker
        JPanel eraseSizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        eraseSizePanel.add(new JLabel("Erase Size"));
        eraseSizePanel.add(eraseSizeSpinner);

        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        colorPanel.add(new JLabel("Color"));
        colorPanel.add(colorComboBox);

        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        controlRow.add(eraseSizePanel);
        controlRow.add(colorPanel);

        JPanel toolbarInner = new JPanel();
        toolbarInner.setLayout(new BoxLayout(toolbarInner, BoxLayout.Y_AXIS));
        toolbarInner.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        toolbarInner.add(radioRow);
        toolbarInner.add(controlRow);

        JPanel toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.setBorder(BorderFactory.createEtchedBorder());
        toolbarPanel.add(toolbarInner, BorderLayout.CENTER);

        // Right panel: toolbar above, canvas below
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(toolbarPanel, BorderLayout.NORTH);
        rightPanel.add(canvasScrollPane, BorderLayout.CENTER);

        // Chat area
        JPanel chatInputPanel = new JPanel(new BorderLayout(4, 0));
        chatInputPanel.add(chatTextField, BorderLayout.CENTER);
        chatInputPanel.add(chatSendButton, BorderLayout.EAST);

        JPanel chatPanel = new JPanel(new BorderLayout(0, 4));
        chatPanel.add(new JScrollPane(chatHistoryListView), BorderLayout.CENTER);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        // Left panel: user list & chat
        JScrollPane usersScrollPane = new JScrollPane(usersListView);
        usersScrollPane.setPreferredSize(new Dimension(120, 0));

        JSplitPane innerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, usersScrollPane, chatPanel);
        innerSplit.setResizeWeight(0.3);
        innerSplit.setBorder(null);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftPanel.setBorder(BorderFactory.createEtchedBorder());
        leftPanel.add(userNameLabel, BorderLayout.NORTH);
        leftPanel.add(innerSplit, BorderLayout.CENTER);

        // Outer split: left panel vs right panel
        JSplitPane outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        outerSplit.setResizeWeight(0.25);
        outerSplit.setBorder(null);

        // Root content pane
        contentPane = new JPanel(new BorderLayout(0, 4));
        contentPane.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contentPane.setPreferredSize(new Dimension(1024, 768));
        contentPane.add(outerSplit, BorderLayout.CENTER);
        contentPane.add(statusBarLabel, BorderLayout.SOUTH);
    }

    public JComponent $$$getRootComponent$$$() { return contentPane; }
}
