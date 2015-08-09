/*
 * Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.JOptionPane;

public class GUICapsule extends Capsule {
    protected static final Entry<String, String> ATTR_SPLASH = ATTRIBUTE("Splash-Image", T_STRING(), null, true, "The path of the application's splash image, with no suffix, relative to the capsule root");

    protected static final Entry<String, Boolean> ATTR_GUI = ATTRIBUTE("GUI", T_BOOL(), false, true, "Whether or not this Capsule uses a GUI");

    public GUICapsule(Capsule pred) {
        super(pred);
    }

    public GUICapsule(Path jarFile) {
        super(jarFile);
    }

    boolean isGUI() {
        return getAttribute(ATTR_GUI);
    }

    @Override
    protected void onError(Throwable t) {
        final StringBuilder sb = new StringBuilder();
        sb.append("CAPSULE EXCEPTION: ").append(t.getMessage()).append('\n');

        final StringWriter stackTrace = new StringWriter();
        t.printStackTrace(new PrintWriter(stackTrace));

        sb.append('\n').append(stackTrace).append("\n");
        JOptionPane.showMessageDialog(null, sb, "Capsule Error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    protected Path getJavaExecutable() {
        final Path p = super.getJavaExecutable();
        return isWindows() ? p.getParent().resolve(p.getFileName().toString().replace("java.exe", "javaw.exe")) : p;
    }

    @Override
    protected Process postlaunch(Process child) {
        return null; // don't wait for child process
    }

    
    @Override
    @SuppressWarnings("unchecked")
    protected <T> T attribute(Entry<String, T> attr) {
        if (ATTR_JVM_ARGS == attr) {
            if (hasAttribute(ATTR_SPLASH)) {
                final List<String> args = new ArrayList<>(super.attribute(ATTR_JVM_ARGS));
                args.add("-splash:" + getWritableAppCache().resolve(getAttribute(ATTR_SPLASH)));
                return (T) args;
            }
        }
        return super.attribute(attr);
    }
}
