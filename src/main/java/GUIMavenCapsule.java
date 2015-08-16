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
import java.util.Map;

/**
 *
 * @author pron
 */
public class GUIMavenCapsule extends MavenCapsule {

    protected static final Map.Entry<String, String> ATTR_ICON = ATTRIBUTE("Icon", T_STRING(), null, true, "The path of the application's icon file(s), with no suffix, relative to the capsule root");

    public GUIMavenCapsule(Path jarFile) {
        super(jarFile);
    }

    public GUIMavenCapsule(Capsule pred) {
        super(pred);
    }

    private GUIListener listener;

    @Override
    protected ProcessBuilder prelaunch(List<String> args, List<String> jvmArgs) {
        this.listener = new GUIListener(getAttribute(ATTR_APP_NAME), getAttribute(ATTR_ICON));
        try {
            return super.prelaunch(args, jvmArgs);
        } finally {
            listener.dispose();
        }
    }

    @Override
    protected DependencyManager createDependencyManager(Path localRepo, boolean reset, int logLevel) {
        return new GUIDependencyManager(listener, localRepo, reset, logLevel);
    }
}
