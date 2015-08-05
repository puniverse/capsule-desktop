/*
 * Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import capsule.org.eclipse.aether.RepositoryListener;
import capsule.org.eclipse.aether.transfer.AbstractTransferListener;
import capsule.org.eclipse.aether.transfer.TransferCancelledException;
import capsule.org.eclipse.aether.transfer.TransferEvent;
import capsule.org.eclipse.aether.transfer.TransferListener;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.io.OutputStream;
import java.io.PrintStream;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author pron
 */
public class GUIListener {
    private final long start;

    private volatile JFrame frame;
    private JPanel content;
    private JProgressBar progress;
    private final JTextArea text;
    private final String name;
    private final String icon;
    private final RepositoryListener repositoryListener;
    private final TransferListener transferListener;
    private volatile boolean disposed;

    public GUIListener(String name, String icon) {
        this.start = System.nanoTime();
        this.name = name;
        this.icon = icon;

        this.text = new JTextArea(5, 20);
        text.setMargin(new Insets(5, 5, 5, 5));
        text.setEditable(false);

        progress = new JProgressBar(0, 100);
        progress.setValue(0);
        progress.setStringPainted(true);

        this.repositoryListener = new ConsoleRepositoryListener(false, new PrintStream(new TextAreaOutputStream(text)));
        this.transferListener = new AbstractTransferListener() {

            @Override
            public void transferInitiated(TransferEvent te) throws TransferCancelledException {
                init();
                progress.setIndeterminate(true);
            }

            @Override
            public void transferStarted(TransferEvent event) throws TransferCancelledException {
                init();
                progress.setIndeterminate(false);
                progress.setMaximum(100);
            }

            @Override
            public void transferProgressed(TransferEvent te) throws TransferCancelledException {
                init();
                progress.setValue((int) (100.0 * (double) te.getTransferredBytes() / (double) te.getResource().getContentLength()));
            }

            @Override
            public void transferSucceeded(TransferEvent te) {
                progress.setValue(100);
            }
        };
    }

    private long elapsedMillis() {
        return (System.nanoTime() - start) / 1000000;
    }

    private void init() {
        if (!disposed && frame == null && elapsedMillis() > 1000) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ex) {
            }

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    frame = new JFrame(name);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                    if (icon != null)
                        frame.setIconImage(new ImageIcon(iconFile(icon)).getImage());
                    
                    createContent();
                    content.setOpaque(true);
                    frame.setContentPane(content);

                    frame.pack();
                    frame.setVisible(true);
                }
            });
        }
    }

    private static String iconFile(String icon) {
        if (icon == null)
            return null;
        if (isWindows())
            return icon + ".ico";
        if (isMac())
            return icon + ".icns";
        return icon + ".png";
    }

    private void createContent() {
        content = new JPanel(new BorderLayout());

        final JPanel panel = new JPanel();
        panel.add(progress);
        // panel.add(label);

        content.add(panel, BorderLayout.PAGE_START);
        content.add(new JScrollPane(text), BorderLayout.CENTER);
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    }

    public void dispose() {
        this.disposed = true;
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    public TransferListener getTransferListener() {
        return transferListener;
    }

    public RepositoryListener getRepositoryListener() {
        return repositoryListener;
    }

    private static class TextAreaOutputStream extends OutputStream {
        private final JTextArea textArea;

        public TextAreaOutputStream(JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void write(int b) {
            textArea.append(String.valueOf((char) b));
            textArea.setCaretPosition(textArea.getDocument().getLength());
        }
    }

    protected static final boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    protected static final boolean isMac() {
        return System.getProperty("os.name").toLowerCase().startsWith("mac");
    }
}
