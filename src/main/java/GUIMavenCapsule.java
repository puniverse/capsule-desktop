/*
 * Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManager;
import capsule.GUIDependencyManager;
import capsule.GUIListener;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author pron
 */
public class GUIMavenCapsule extends MavenCapsule {
    public GUIMavenCapsule(Path jarFile) {
        super(jarFile);
    }

    public GUIMavenCapsule(Capsule pred) {
        super(pred);
    }

    private GUIListener listener;

    @Override
    protected ProcessBuilder prelaunch(List<String> args) {
        this.listener = new GUIListener(getAttribute(ATTR_APP_NAME), getAttribute(NativeCapsule.ATTR_ICON));
        try {
            return super.prelaunch(args);
        } finally {
            listener.dispose();
        }
    }

    @Override
    protected DependencyManager createDependencyManager(Path localRepo, boolean reset, int logLevel) {
        // final GUICapsule gui = sup(GUICapsule.class);
        return new GUIDependencyManager(listener, localRepo, reset, logLevel);
    }
}
