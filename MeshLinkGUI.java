import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * MeshLink GUI  —  Swing front-end for the MeshLink v0.2 backend.
 *
 * Entry point:
 *   java MeshLinkGUI &lt;userId&gt; &lt;tcpPort&gt;
 *
 * This class is the ONLY new file. It replaces DiscoveryTest as the
 * interactive entry point but wires to exactly the same backend objects:
 *   PeerBroadcaster          – UDP announce
 *   PeerListener             – UDP discovery
 *   PeerConnectionManager    – TCP framing
 *   MessageService           – encode / decode / store / lifecycle
 *
 * Nothing in the networking or message layers is modified.
 */
public class MeshLinkGUI extends JFrame {

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final Color BG_DEEP        = new Color(0x10, 0x10, 0x0F);
    private static final Color BG_PANEL       = new Color(0x1C, 0x1B, 0x19);
    private static final Color BG_SIDEBAR     = new Color(0x10, 0x10, 0x0F);
    private static final Color BG_INPUT       = new Color(0x29, 0x27, 0x23);
    private static final Color BG_CARD        = new Color(0x29, 0x27, 0x23);
    private static final Color BG_CARD_ALT    = new Color(0x51, 0x42, 0x2D);
    private static final Color BG_BUBBLE_SENT = new Color(0x51, 0x42, 0x2D);
    private static final Color BG_BUBBLE_RECV = new Color(0x29, 0x27, 0x23);
    private static final Color BG_HOVER       = new Color(0x36, 0x32, 0x2A);
    private static final Color BG_SEL         = new Color(0x89, 0x70, 0x44, 150);
    private static final Color ACCENT         = new Color(0x89, 0x70, 0x44);
    private static final Color ACCENT_GLOW    = new Color(0xC5, 0xA6, 0x6D);
    private static final Color ACCENT_CYAN    = new Color(0xC5, 0xA6, 0x6D);
    private static final Color TEXT_PRI       = new Color(0xF1, 0xED, 0xE4);
    private static final Color TEXT_SEC       = new Color(0xC5, 0xB9, 0xA6);
    private static final Color TEXT_MUTED     = new Color(0x9C, 0x91, 0x81);
    private static final Color ONLINE_DOT     = new Color(0xC5, 0xA6, 0x6D);
    private static final Color BORDER_SUB     = new Color(0x4A, 0x42, 0x36);

    // ── Fonts ────────────────────────────────────────────────────────────────
    private static final Font FONT_BODY  = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BOLD  = new Font("Segoe UI", Font.BOLD,  14);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_TINY  = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD,  16);

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");

    // ── Backend ──────────────────────────────────────────────────────────────
    private final String               myUserId;
    private final MessageService       messageService;
    private final PeerConnectionManager tcp;
    private final PeerListener         discovery;

    // ── UI ───────────────────────────────────────────────────────────────────
    private final DefaultListModel<PeerEntry> peerModel = new DefaultListModel<>();
    private final Map<String, ChatPane>       chatPanes = new LinkedHashMap<>();
    private final JPanel                      chatStack;
    private final CardLayout                  cardLayout = new CardLayout();
    private       String                      activePeer = null;

    private final JList<PeerEntry> peerList;
    private final JLabel chatHeaderLabel;
    private final JLabel chatStatusLabel;
    private final JTextField inputField;
    private final JButton    sendBtn;
    private final JButton    fileBtn;
    private final JLabel     statusLabel;
    private final JLabel     peerCountLabel;

    // ─────────────────────────────────────────────────────────────────────────

    public MeshLinkGUI(String userId, int tcpPort,
                       MessageService svc,
                       PeerConnectionManager tcpMgr,
                       PeerListener disc) {
        super("MeshLink — " + userId);
        this.myUserId       = userId;
        this.messageService = svc;
        this.tcp            = tcpMgr;
        this.discovery      = disc;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 560));
        setPreferredSize(new Dimension(1060, 680));
        setBackground(BG_DEEP);

        // ── Root ─────────────────────────────────────────────────────────
        JPanel root = new GradientPanel(BG_DEEP, new Color(0x1C, 0x1A, 0x16), true);
        root.setLayout(new BorderLayout());
        root.setBackground(BG_DEEP);
        setContentPane(root);

        // ══════════════════════════════════════════════════════════════════
        //  SIDEBAR
        // ══════════════════════════════════════════════════════════════════
        JPanel sidebar = new GradientPanel(BG_SIDEBAR, new Color(0x1C, 0x1A, 0x16), false);
        sidebar.setLayout(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(250, 0));
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_SUB));

        // header
        JPanel sideHeader = new JPanel(new BorderLayout(12, 0));
        sideHeader.setLayout(new BoxLayout(sideHeader, BoxLayout.Y_AXIS));
        sideHeader.setBackground(BG_SIDEBAR);
        sideHeader.setBorder(new EmptyBorder(18, 16, 16, 16));

        JPanel brandRow = new JPanel(new BorderLayout(10, 0));
        brandRow.setOpaque(false);

        JLabel logo = new JLabel("M", SwingConstants.CENTER);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logo.setForeground(Color.WHITE);
        logo.setBackground(ACCENT);
        logo.setOpaque(true);
        logo.setPreferredSize(new Dimension(38, 38));

        JPanel brandText = new JPanel();
        brandText.setOpaque(false);
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));

        JLabel appTitle = new JLabel("MeshLink");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 19));
        appTitle.setForeground(TEXT_PRI);

        JLabel myUser = new JLabel("@" + userId);
        myUser.setFont(FONT_SMALL);
        myUser.setForeground(TEXT_SEC);

        brandText.add(appTitle);
        brandText.add(Box.createVerticalStrut(1));
        brandText.add(myUser);
        brandRow.add(logo, BorderLayout.WEST);
        brandRow.add(brandText, BorderLayout.CENTER);

        JPanel networkCard = new JPanel(new BorderLayout());
        networkCard.setBackground(BG_CARD);
        networkCard.setBorder(new CompoundBorder(
                new LineBorder(BORDER_SUB, 1, true),
                new EmptyBorder(9, 11, 9, 11)));

        JLabel networkIcon = new JLabel("●");
        networkIcon.setForeground(ONLINE_DOT);
        networkIcon.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        peerCountLabel = new JLabel("  LAN discovery active");
        peerCountLabel.setFont(FONT_SMALL);
        peerCountLabel.setForeground(TEXT_SEC);
        networkCard.add(networkIcon, BorderLayout.WEST);
        networkCard.add(peerCountLabel, BorderLayout.CENTER);

        sideHeader.add(brandRow);
        sideHeader.add(Box.createVerticalStrut(16));
        sideHeader.add(networkCard);
        sidebar.add(sideHeader, BorderLayout.NORTH);

        // section label
        JPanel peersSect = new JPanel(new BorderLayout());
        peersSect.setBackground(BG_SIDEBAR);
        peersSect.setBorder(new EmptyBorder(0, 16, 6, 16));
        JLabel peersTitle = new JLabel("YOUR NETWORK  /  DISCOVERED");
        peersTitle.setFont(FONT_TINY);
        peersTitle.setForeground(TEXT_MUTED);
        peersSect.add(peersTitle, BorderLayout.WEST);

        // peer list
        peerList = new JList<>(peerModel);
        peerList.setBackground(BG_SIDEBAR);
        peerList.setSelectionBackground(BG_SEL);
        peerList.setSelectionForeground(TEXT_PRI);
        peerList.setBorder(null);
        peerList.setFixedCellHeight(64);
        peerList.setCellRenderer(new PeerCellRenderer());
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane peerScroll = new JScrollPane(peerList);
        peerScroll.setBorder(null);
        peerScroll.getViewport().setBackground(BG_SIDEBAR);
        styleScrollBar(peerScroll.getVerticalScrollBar());

        statusLabel = new JLabel("Discovering peers…");
        statusLabel.setFont(FONT_TINY);
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setBorder(new EmptyBorder(6, 16, 10, 16));

        JPanel sideBody = new JPanel(new BorderLayout());
        sideBody.setBackground(BG_SIDEBAR);
        sideBody.add(peersSect,  BorderLayout.NORTH);
        sideBody.add(peerScroll, BorderLayout.CENTER);
        sideBody.add(statusLabel, BorderLayout.SOUTH);
        sidebar.add(sideBody, BorderLayout.CENTER);

        // ══════════════════════════════════════════════════════════════════
        //  CHAT AREA
        // ══════════════════════════════════════════════════════════════════
        JPanel chatArea = new GradientPanel(BG_PANEL, new Color(0x14, 0x13, 0x11), false);
        chatArea.setLayout(new BorderLayout());
        chatArea.setBackground(BG_PANEL);

        // chat header bar
        JPanel chatHeader = new GradientPanel(BG_PANEL, new Color(0x29, 0x27, 0x23), true);
        chatHeader.setLayout(new BorderLayout(14, 0));
        chatHeader.setBackground(BG_PANEL);
        chatHeader.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_SUB),
                new EmptyBorder(14, 20, 14, 20)));

        chatHeaderLabel = new JLabel("Select a peer");
        chatHeaderLabel.setFont(FONT_BOLD);
        chatHeaderLabel.setForeground(TEXT_PRI);

        chatStatusLabel = new JLabel("");
        chatStatusLabel.setFont(FONT_SMALL);
        chatStatusLabel.setForeground(TEXT_SEC);

        JPanel chatHeaderText = new JPanel();
        chatHeaderText.setLayout(new BoxLayout(chatHeaderText, BoxLayout.Y_AXIS));
        chatHeaderText.setBackground(BG_PANEL);
        chatHeaderText.add(chatHeaderLabel);
        chatHeaderText.add(Box.createVerticalStrut(2));
        chatHeaderText.add(chatStatusLabel);
        JLabel chatIcon = new JLabel("⌁", SwingConstants.CENTER);
        chatIcon.setFont(new Font("Segoe UI", Font.BOLD, 24));
        chatIcon.setForeground(ACCENT_CYAN);
        chatIcon.setPreferredSize(new Dimension(34, 34));
        chatIcon.setBackground(BG_CARD_ALT);
        chatIcon.setOpaque(true);
        chatHeader.add(chatIcon, BorderLayout.WEST);
        chatHeader.add(chatHeaderText, BorderLayout.CENTER);
        chatArea.add(chatHeader, BorderLayout.NORTH);

        // card stack
        chatStack = new JPanel(cardLayout);
        chatStack.setBackground(BG_PANEL);
        chatStack.add(buildPlaceholder(), "__empty__");
        cardLayout.show(chatStack, "__empty__");
        chatArea.add(chatStack, BorderLayout.CENTER);

        // input bar
        JPanel inputBar = new GradientPanel(BG_PANEL, new Color(0x14, 0x13, 0x11), true);
        inputBar.setLayout(new BorderLayout(8, 0));
        inputBar.setBackground(BG_PANEL);
        inputBar.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER_SUB),
                new EmptyBorder(12, 16, 12, 16)));

        inputField = new JTextField();
        styleInputField(inputField);
        inputField.setEnabled(false);

        fileBtn = buildIconButton("📎", "Send File");
        fileBtn.setEnabled(false);

        sendBtn = buildSendButton();
        sendBtn.setEnabled(false);

        inputBar.add(fileBtn,    BorderLayout.WEST);
        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendBtn,    BorderLayout.EAST);
        chatArea.add(inputBar, BorderLayout.SOUTH);

        // ── Split pane ────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, chatArea);
        split.setDividerLocation(250);
        split.setDividerSize(1);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);

        // ── Events ────────────────────────────────────────────────────────
        peerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onPeerSelected();
        });
        inputField.addActionListener(e -> sendMessage());
        sendBtn.addActionListener(e -> sendMessage());
        fileBtn.addActionListener(e -> sendFile());

        // ── Peer discovery poll ───────────────────────────────────────────
        new javax.swing.Timer(1500, e -> refreshPeerList()).start();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Peer list refresh ─────────────────────────────────────────────────────

    private void refreshPeerList() {
        List<PeerListener.PeerInfo> discovered = discovery.getKnownPeers();
        Set<String> known = new HashSet<>();
        for (int i = 0; i < peerModel.size(); i++) known.add(peerModel.get(i).userId);

        for (PeerListener.PeerInfo info : discovered) {
            if (!known.contains(info.userId)) {
                peerModel.addElement(new PeerEntry(info));
                ensureChatPane(info.userId);
            } else {
                for (int i = 0; i < peerModel.size(); i++) {
                    PeerEntry pe = peerModel.get(i);
                    if (pe.userId.equals(info.userId)) { pe.info = info; break; }
                }
            }
        }
        for (int i = 0; i < peerModel.size(); i++) {
            peerModel.get(i).connected = tcp.isConnected(peerModel.get(i).userId);
        }
        peerList.repaint();

        long conn = 0;
        for (int i = 0; i < peerModel.size(); i++) if (peerModel.get(i).connected) conn++;
        int total = peerModel.size();
        statusLabel.setText(total == 0 ? "Discovering peers…"
                : total + " discovered, " + conn + " connected");
        peerCountLabel.setText(total == 0
                ? "  Waiting for nearby peers"
                : "  " + conn + " connected  ·  " + total + " discovered");
    }

    // ── Peer selection ────────────────────────────────────────────────────────

    private void onPeerSelected() {
        PeerEntry sel = peerList.getSelectedValue();
        if (sel == null) return;
        activePeer = sel.userId;
        sel.unread = 0;
        ensureChatPane(activePeer);
        cardLayout.show(chatStack, activePeer);
        chatHeaderLabel.setText(sel.userId);
        boolean conn = sel.connected;
        chatStatusLabel.setText(conn
                ? "● Connected  •  " + sel.info.address.getHostAddress() + ":" + sel.info.tcpPort
                : "○ Discovered  •  " + sel.info.address.getHostAddress() + ":" + sel.info.tcpPort);
        chatStatusLabel.setForeground(conn ? ONLINE_DOT : TEXT_SEC);
        inputField.setEnabled(conn);
        sendBtn.setEnabled(conn);
        fileBtn.setEnabled(conn);
        if (conn) inputField.requestFocusInWindow();
    }

    // ── Send chat message ─────────────────────────────────────────────────────

    private void sendMessage() {
        if (activePeer == null) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");

        ChatPane pane = chatPanes.get(activePeer);
        Message  msg  = messageService.createChatMessage(myUserId, activePeer, text);
        String   wire = messageService.encodeForTransport(msg);

        new Thread(() -> {
            try {
                tcp.sendText(activePeer, wire);
                messageService.markSent(msg);
                SwingUtilities.invokeLater(() -> { if (pane != null) pane.addBubble(msg, true); });
            } catch (IOException ex) {
                messageService.markFailed(msg);
                SwingUtilities.invokeLater(() -> { if (pane != null) pane.addError("Send failed: " + ex.getMessage()); });
            }
        }, "meshlink-send").start();
    }

    // ── Send file ─────────────────────────────────────────────────────────────

    private void sendFile() {
        if (activePeer == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Send File to " + activePeer);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        ChatPane pane = chatPanes.get(activePeer);

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> { if (pane != null) pane.addNotice("Sending " + file.getFileName() + "…"); });
            try {
                tcp.sendFile(activePeer, file);
                SwingUtilities.invokeLater(() -> { if (pane != null) pane.addNotice("✓ File sent: " + file.getFileName()); });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> { if (pane != null) pane.addError("File failed: " + ex.getMessage()); });
            }
        }, "meshlink-file").start();
    }

    // ── Callbacks from networking layer ───────────────────────────────────────

    void onIncomingText(String peerId, String wire) {
        Message msg = messageService.receive(wire);
        SwingUtilities.invokeLater(() -> {
            ensureChatPane(peerId);
            ChatPane pane = chatPanes.get(peerId);
            if (msg != null && pane != null) {
                pane.addBubble(msg, false);
                if (!peerId.equals(activePeer)) {
                    for (int i = 0; i < peerModel.size(); i++) {
                        if (peerModel.get(i).userId.equals(peerId)) { peerModel.get(i).unread++; break; }
                    }
                    peerList.repaint();
                }
            } else if (msg == null && wire != null && !wire.startsWith("MESHMSG|")) {
                if (pane != null) pane.addNotice("[RAW] " + wire);
            }
        });
    }

    void onConnected(String peerId) {
        SwingUtilities.invokeLater(() -> {
            ensureChatPane(peerId);
            ChatPane pane = chatPanes.get(peerId);
            if (pane != null) pane.addNotice("● Connected to " + peerId);
            for (int i = 0; i < peerModel.size(); i++) {
                if (peerModel.get(i).userId.equals(peerId)) { peerModel.get(i).connected = true; break; }
            }
            peerList.repaint();
            if (peerId.equals(activePeer)) {
                inputField.setEnabled(true); sendBtn.setEnabled(true); fileBtn.setEnabled(true);
                chatStatusLabel.setText("● Connected"); chatStatusLabel.setForeground(ONLINE_DOT);
            }
        });
    }

    void onDisconnected(String peerId) {
        SwingUtilities.invokeLater(() -> {
            ChatPane pane = chatPanes.get(peerId);
            if (pane != null) pane.addNotice("○ Disconnected from " + peerId);
            for (int i = 0; i < peerModel.size(); i++) {
                if (peerModel.get(i).userId.equals(peerId)) { peerModel.get(i).connected = false; break; }
            }
            peerList.repaint();
            if (peerId.equals(activePeer)) {
                inputField.setEnabled(false); sendBtn.setEnabled(false); fileBtn.setEnabled(false);
                chatStatusLabel.setText("○ Disconnected"); chatStatusLabel.setForeground(TEXT_SEC);
            }
        });
    }

    void onFileReceived(String peerId, Path file) {
        SwingUtilities.invokeLater(() -> {
            ensureChatPane(peerId);
            ChatPane pane = chatPanes.get(peerId);
            if (pane != null) pane.addFileReceived(peerId, file);
        });
    }

    // ── Chat pane ─────────────────────────────────────────────────────────────

    private void ensureChatPane(String peerId) {
        if (!chatPanes.containsKey(peerId)) {
            ChatPane p = new ChatPane();
            chatPanes.put(peerId, p);
            chatStack.add(p, peerId);
        }
    }

    private class ChatPane extends JPanel {
        private final JPanel      bubbles;
        private final JScrollPane scroll;

        ChatPane() {
            setLayout(new BorderLayout());
            setBackground(BG_PANEL);

            bubbles = new JPanel();
            bubbles.setLayout(new BoxLayout(bubbles, BoxLayout.Y_AXIS));
            bubbles.setBackground(BG_PANEL);
            bubbles.setBorder(new EmptyBorder(16, 16, 8, 16));

            scroll = new JScrollPane(bubbles);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(BG_PANEL);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            styleScrollBar(scroll.getVerticalScrollBar());
            add(scroll, BorderLayout.CENTER);
        }

        void addBubble(Message msg, boolean sent) {
            boolean isSent   = sent || msg.getSenderId().equals(myUserId);
            String  time     = TIME_FMT.format(new Date(msg.getTimestamp()));
            Color   bgColor  = isSent ? BG_BUBBLE_SENT : BG_BUBBLE_RECV;
            Color   txtColor = isSent ? Color.WHITE : TEXT_PRI;

            JTextArea text = new JTextArea(msg.getPayload());
            text.setFont(FONT_BODY);
            text.setForeground(txtColor);
            text.setBackground(bgColor);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setBorder(null);

            JLabel timeLbl = new JLabel(time + (isSent ? "  ✓" : ""));
            timeLbl.setFont(FONT_TINY);
            timeLbl.setForeground(isSent ? new Color(0xE3, 0xD4, 0xB8) : TEXT_MUTED);
            timeLbl.setAlignmentX(isSent ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

            JPanel inner = new JPanel();
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setOpaque(false);
            inner.add(text);
            inner.add(Box.createVerticalStrut(4));
            inner.add(timeLbl);

            RoundedPanel bubble = new RoundedPanel(14, bgColor);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(new EmptyBorder(9, 13, 7, 13));
            bubble.add(inner);
            bubble.setMaximumSize(new Dimension(480, Integer.MAX_VALUE));

            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(BG_PANEL);
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            if (isSent) {
                row.add(Box.createHorizontalGlue(), BorderLayout.WEST);
                row.add(bubble, BorderLayout.EAST);
            } else {
                row.add(bubble, BorderLayout.WEST);
                row.add(Box.createHorizontalGlue(), BorderLayout.EAST);
            }

            bubbles.add(row);
            bubbles.revalidate();
            scrollBottom();
        }

        void addNotice(String txt) {
            JLabel lbl = new JLabel(txt, SwingConstants.CENTER);
            lbl.setFont(FONT_TINY);
            lbl.setForeground(TEXT_MUTED);
            lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(BG_PANEL);
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            row.add(lbl, BorderLayout.CENTER);
            bubbles.add(row);
            bubbles.revalidate();
            scrollBottom();
        }

        void addError(String txt) {
            JLabel lbl = new JLabel("⚠  " + txt, SwingConstants.CENTER);
            lbl.setFont(FONT_SMALL);
            lbl.setForeground(new Color(0xFF, 0x6B, 0x6B));
            lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(BG_PANEL);
            row.add(lbl, BorderLayout.CENTER);
            bubbles.add(row);
            bubbles.revalidate();
            scrollBottom();
        }

        void addFileReceived(String peerId, Path file) {
            RoundedPanel card = new RoundedPanel(14, BG_BUBBLE_RECV);
            card.setLayout(new BorderLayout(10, 0));
            card.setBorder(new EmptyBorder(10, 14, 10, 14));

            JLabel icon = new JLabel("📄");
            icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));

            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.setOpaque(false);

            JLabel name = new JLabel(file.getFileName().toString());
            name.setFont(FONT_BOLD);
            name.setForeground(TEXT_PRI);

            JLabel sub = new JLabel("From " + peerId + "  •  saved to received/");
            sub.setFont(FONT_TINY);
            sub.setForeground(TEXT_MUTED);

            JLabel openLink = new JLabel("<html><u>Open folder</u></html>");
            openLink.setFont(FONT_TINY);
            openLink.setForeground(ACCENT_GLOW);
            openLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            openLink.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    try { Desktop.getDesktop().open(file.getParent().toFile()); }
                    catch (Exception ex) { JOptionPane.showMessageDialog(MeshLinkGUI.this, ex.getMessage()); }
                }
            });

            info.add(name);
            info.add(Box.createVerticalStrut(2));
            info.add(sub);
            info.add(Box.createVerticalStrut(2));
            info.add(openLink);

            card.add(icon, BorderLayout.WEST);
            card.add(info, BorderLayout.CENTER);
            card.setMaximumSize(new Dimension(400, Integer.MAX_VALUE));

            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(BG_PANEL);
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            row.add(card, BorderLayout.WEST);
            row.add(Box.createHorizontalGlue(), BorderLayout.EAST);

            bubbles.add(row);
            bubbles.revalidate();
            scrollBottom();
        }

        private void scrollBottom() {
            SwingUtilities.invokeLater(() -> {
                JScrollBar bar = scroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            });
        }
    }

    // ── Peer cell renderer ────────────────────────────────────────────────────

    private class PeerCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean hasFocus) {

            PeerEntry pe = (PeerEntry) value;

            JPanel panel = new JPanel(new BorderLayout(10, 0));
            panel.setBorder(new EmptyBorder(10, 14, 10, 14));
            panel.setBackground(isSelected ? BG_SEL : BG_SIDEBAR);
            panel.setOpaque(true);

            // avatar circle
            String initial = String.valueOf(pe.userId.charAt(0)).toUpperCase();
            JLabel avatar = new JLabel(initial, SwingConstants.CENTER);
            avatar.setFont(FONT_BOLD);
            avatar.setForeground(Color.WHITE);
            avatar.setPreferredSize(new Dimension(38, 38));
            avatar.setOpaque(true);
            avatar.setBackground(avatarColor(pe.userId));

            // text
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            JLabel nameLbl = new JLabel(pe.userId);
            nameLbl.setFont(FONT_BOLD);
            nameLbl.setForeground(TEXT_PRI);

            JLabel statusLbl = new JLabel(
                    (pe.connected ? "● Connected" : "○ Discovered"));
            statusLbl.setFont(FONT_SMALL);
            statusLbl.setForeground(pe.connected ? ONLINE_DOT : TEXT_MUTED);

            textPanel.add(nameLbl);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(statusLbl);

            panel.add(avatar,    BorderLayout.WEST);
            panel.add(textPanel, BorderLayout.CENTER);

            // unread badge
            if (pe.unread > 0 && !pe.userId.equals(activePeer)) {
                JLabel badge = new JLabel(String.valueOf(pe.unread), SwingConstants.CENTER);
                badge.setFont(FONT_TINY);
                badge.setForeground(Color.WHITE);
                badge.setBackground(ACCENT);
                badge.setOpaque(true);
                badge.setPreferredSize(new Dimension(20, 18));
                panel.add(badge, BorderLayout.EAST);
            }

            return panel;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JPanel buildPlaceholder() {
        JPanel p = new JPanel();
        p.setBackground(BG_PANEL);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Box.createVerticalGlue());

        RoundedPanel welcomeCard = new RoundedPanel(22, BG_CARD);
        welcomeCard.setLayout(new BoxLayout(welcomeCard, BoxLayout.Y_AXIS));
        welcomeCard.setBorder(new CompoundBorder(
                new LineBorder(BORDER_SUB, 1, true),
                new EmptyBorder(28, 44, 28, 44)));
        welcomeCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        welcomeCard.setMaximumSize(new Dimension(520, 230));

        JLabel icon = new JLabel("✦", SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI", Font.BOLD, 42));
        icon.setForeground(ACCENT_CYAN);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel msg = new JLabel("Your private mesh, connected");
        msg.setFont(FONT_BOLD);
        msg.setForeground(TEXT_PRI);
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Choose a peer from your LAN to start a conversation");
        sub.setFont(FONT_SMALL);
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        welcomeCard.add(icon);
        welcomeCard.add(Box.createVerticalStrut(12));
        welcomeCard.add(msg);
        welcomeCard.add(Box.createVerticalStrut(6));
        welcomeCard.add(sub);
        p.add(welcomeCard);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private void styleInputField(JTextField field) {
        field.setFont(FONT_BODY);
        field.setForeground(TEXT_PRI);
        field.setBackground(BG_INPUT);
        field.setCaretColor(ACCENT_GLOW);
        field.setBorder(new CompoundBorder(
                new LineBorder(BORDER_SUB, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
    }

    private JButton buildSendButton() {
        JButton btn = new JButton("Send  ➤");
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(ACCENT);
        btn.setBorder(new CompoundBorder(
                new LineBorder(ACCENT_GLOW, 1, true),
                new EmptyBorder(8, 18, 8, 18)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (btn.isEnabled()) btn.setBackground(ACCENT_GLOW); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(ACCENT); }
        });
        return btn;
    }

    private JButton buildIconButton(String emoji, String tip) {
        JButton btn = new JButton(emoji);
        btn.setToolTipText(tip);
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        btn.setForeground(TEXT_SEC);
        btn.setBackground(BG_INPUT);
        btn.setBorder(new CompoundBorder(
                new LineBorder(BORDER_SUB, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (btn.isEnabled()) btn.setForeground(TEXT_PRI); }
            @Override public void mouseExited(MouseEvent e)  { btn.setForeground(TEXT_SEC); }
        });
        return btn;
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setBackground(BG_PANEL);
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(0x6B, 0x5B, 0x40);
                trackColor = BG_PANEL;
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroBtn(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroBtn(); }
            private JButton zeroBtn() {
                JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b;
            }
        });
    }

    private static Color avatarColor(String userId) {
        return (Math.abs(userId.hashCode()) % 2 == 0)
                ? ACCENT : new Color(0xA6, 0x88, 0x57);
    }

    // ── Rounded bubble panel ──────────────────────────────────────────────────

    private static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bg;
        RoundedPanel(int radius, Color bg) { super(); this.radius = radius; this.bg = bg; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class GradientPanel extends JPanel {
        private final Color start;
        private final Color end;
        private final boolean horizontal;

        GradientPanel(Color start, Color end, boolean horizontal) {
            this.start = start;
            this.end = end;
            this.horizontal = horizontal;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int x2 = horizontal ? getWidth() : 0;
            int y2 = horizontal ? 0 : getHeight();
            g2.setPaint(new GradientPaint(0, 0, start, x2, y2, end));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ── PeerEntry model item ──────────────────────────────────────────────────

    private static class PeerEntry {
        String userId;
        PeerListener.PeerInfo info;
        boolean connected = false;
        int     unread    = 0;
        PeerEntry(PeerListener.PeerInfo info) { this.userId = info.userId; this.info = info; }
        @Override public String toString() { return userId; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MAIN
    // ══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java MeshLinkGUI <userId> <tcpPort>");
            return;
        }

        String userId  = args[0];
        int    tcpPort = Integer.parseInt(args[1]);

        // Apply system look-and-feel before creating windows
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        MessageService   messageService = new MessageService();
        PeerListener     discovery      = new PeerListener(userId);
        PeerBroadcaster  broadcaster    = new PeerBroadcaster(userId, "device-" + userId, tcpPort);

        // GUI holder so the lambda can reference it after construction
        MeshLinkGUI[] guiHolder = new MeshLinkGUI[1];

        PeerConnectionManager tcp = new PeerConnectionManager(
                userId, tcpPort, Paths.get("received"),
                new PeerConnectionManager.Listener() {
                    @Override public void onConnected(String p)       { if (guiHolder[0] != null) guiHolder[0].onConnected(p); }
                    @Override public void onDisconnected(String p)    { if (guiHolder[0] != null) guiHolder[0].onDisconnected(p); }
                    @Override public void onText(String p, String w)  { if (guiHolder[0] != null) guiHolder[0].onIncomingText(p, w); }
                    @Override public void onFileReceived(String p, Path f) { if (guiHolder[0] != null) guiHolder[0].onFileReceived(p, f); }
                    @Override public void onError(String p, Exception ex)  { System.err.println("[ERROR] " + p + ": " + ex.getMessage()); }
                }
        );

        tcp.start();
        new Thread(broadcaster, "broadcaster").start();
        new Thread(discovery,   "discovery").start();

        SwingUtilities.invokeAndWait(() ->
                guiHolder[0] = new MeshLinkGUI(userId, tcpPort, messageService, tcp, discovery));

        // discovery → connection loop (identical to DiscoveryTest)
        while (true) {
            for (PeerListener.PeerInfo peer : discovery.getKnownPeers()) {
                if (!tcp.isConnected(peer.userId)) tcp.connectTo(peer);
            }
            Thread.sleep(1000);
        }
    }
}
