package dev.omniloader.installer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Omniloader installer.
 *   Double-click: opens a small window.
 *   Command line: --official [dir] | --prism [dir] | --mrpack <file> | --server <dir>   (+ --no-neoforge, --open)
 */
public final class Installer {
    public static String version() {
        String v = Installer.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) { cli(args); return; }
        if (GraphicsEnvironment.isHeadless()) { System.out.println("Run with --help for command line use."); return; }
        System.setProperty("apple.awt.application.name", "Omniloader Installer");
        SwingUtilities.invokeLater(Installer::window);
    }

    // ------------------------------------------------------------------ command line

    private static void cli(String[] a) throws Exception {
        Install in = new Install(System.out::println);
        boolean neo = true;
        for (String s : a) if (s.equals("--no-neoforge")) neo = false;
        for (int i = 0; i < a.length; i++) {
            String next = i + 1 < a.length && !a[i + 1].startsWith("--") ? a[i + 1] : null;
            switch (a[i]) {
                case "--official" -> in.official(next != null ? Path.of(next) : Install.officialDir(), neo);
                case "--prism" -> in.prism(next != null ? Path.of(next) : Install.prismDir());
                case "--mrpack" -> in.mrpack(Path.of(next != null ? next : "Omniloader.mrpack"));
                case "--server" -> in.server(Path.of(next != null ? next : "server"), neo);
                case "--write-icon" -> Files.write(Path.of(next), Install.icon(Integer.parseInt(a[i + 2])));
                case "--snapshot" -> snapshot(Path.of(next));
                case "--help" -> System.out.println("""
                        Omniloader installer %s
                          --official [minecraft dir]   add a profile to the official Minecraft Launcher
                          --prism [prism dir]          add an instance to Prism Launcher
                          --mrpack <file>              write a Modrinth modpack (.mrpack)
                          --server <dir>               set up a NeoForge server with Omniloader
                          --no-neoforge                don't download/run the NeoForge installer
                        """.formatted(version()));
                default -> {}
            }
        }
    }

    // ------------------------------------------------------------------ window

    /** Test helper: draws the installer window into a PNG without showing it. */
    private static void snapshot(Path out) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = build();
            f.pack();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(f.getContentPane().getWidth(), f.getContentPane().getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            f.getContentPane().paint(g);
            g.dispose();
            try { javax.imageio.ImageIO.write(img, "png", out.toFile()); } catch (Exception e) { throw new RuntimeException(e); }
            f.dispose();
        });
        System.exit(0);
    }

    private static void window() {
        JFrame f = build();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    private static JFrame build() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        JFrame f = new JFrame("Omniloader Installer");
        try { f.setIconImage(javax.imageio.ImageIO.read(new ByteArrayInputStream(Install.icon(256)))); } catch (Exception ignored) {}
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel head = new JPanel(new BorderLayout(14, 0));
        try { head.add(new JLabel(new ImageIcon(Install.icon(72))), BorderLayout.WEST); } catch (Exception ignored) {}
        JLabel title = new JLabel("<html><span style='font-size:18pt'><b>Omniloader</b></span><br>"
                + "Play <b>Fabric</b>, <b>Quilt</b>, <b>Forge</b> and <b>NeoForge</b> mods together in Minecraft " + Install.MC + ".</html>");
        head.add(title, BorderLayout.CENTER);
        root.add(head, BorderLayout.NORTH);

        List<String> found = Install.detected();
        JCheckBox official = box("Minecraft Launcher (the official one)", found.contains("official"));
        JCheckBox prism = box("Prism Launcher", found.contains("prism"));
        JCheckBox modrinth = box("Modrinth App", found.contains("modrinth"));
        JPanel choices = new JPanel(new GridLayout(0, 1, 0, 4));
        choices.add(new JLabel("Add Omniloader to:"));
        choices.add(official); choices.add(prism); choices.add(modrinth);

        JTextArea log = new JTextArea(9, 52);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        JPanel mid = new JPanel(new BorderLayout(0, 10));
        mid.add(choices, BorderLayout.NORTH);
        mid.add(new JScrollPane(log), BorderLayout.CENTER);
        root.add(mid, BorderLayout.CENTER);

        JButton go = new JButton("Install");
        JButton openMods = new JButton("Open mods folder");
        openMods.setEnabled(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(openMods); buttons.add(go);
        root.add(buttons, BorderLayout.SOUTH);

        List<Path> modsFolders = new ArrayList<>();
        go.addActionListener(e -> {
            go.setEnabled(false);
            Install in = new Install(s -> SwingUtilities.invokeLater(() -> log.append(s + "\n")));
            new Thread(() -> {
                boolean ok = true;
                try {
                    if (official.isSelected()) modsFolders.add(in.official(Install.officialDir(), true));
                    if (prism.isSelected()) modsFolders.add(in.prism(Install.prismDir()));
                    if (modrinth.isSelected()) {
                        Path pack = Install.home().resolve("Downloads").resolve("Omniloader.mrpack");
                        in.mrpack(pack);
                        if (Install.mac()) new ProcessBuilder("open", "-a", "Modrinth App", pack.toString()).start();
                        else Desktop.getDesktop().open(pack.toFile());
                        SwingUtilities.invokeLater(() -> log.append("The Modrinth App will ask you to install the \"Omniloader\" pack: say yes.\n"));
                    }
                } catch (Exception ex) {
                    ok = false;
                    String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                    SwingUtilities.invokeLater(() -> log.append("\nSomething went wrong: " + msg + "\n"));
                }
                boolean done = ok;
                SwingUtilities.invokeLater(() -> {
                    go.setEnabled(true);
                    openMods.setEnabled(!modsFolders.isEmpty());
                    if (done) log.append("\nAll done! Open your launcher, pick \"Omniloader\" and press Play.\n"
                            + "Add mods with \"Open mods folder\".\n");
                });
            }, "install").start();
        });
        openMods.addActionListener(e -> {
            for (Path p : modsFolders) try { Desktop.getDesktop().open(p.toFile()); } catch (Exception ignored) {}
        });

        if (found.isEmpty()) log.setText("I couldn't find a Minecraft launcher on this computer.\n"
                + "Install the Minecraft Launcher, Prism Launcher or the Modrinth App, open it once, then run this again.\n");
        f.setContentPane(root);
        f.pack();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        return f;
    }

    private static JCheckBox box(String label, boolean present) {
        JCheckBox b = new JCheckBox(present ? label : label + "  (not found on this computer)", present);
        b.setEnabled(present);
        return b;
    }

    static boolean exists(Path p) { return Files.exists(p); }
}
