/*
 * Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import capsule.org.eclipse.aether.AbstractForwardingRepositorySystemSession;
import capsule.org.eclipse.aether.DefaultRepositorySystemSession;
import capsule.org.eclipse.aether.RepositoryListener;
import capsule.org.eclipse.aether.RepositorySystem;
import capsule.org.eclipse.aether.RepositorySystemSession;
import capsule.org.eclipse.aether.repository.LocalRepository;
import capsule.org.eclipse.aether.transfer.TransferListener;
import java.nio.file.Path;

/**
 *
 * @author pron
 */
public class GUIDependencyManager extends DependencyManager {
    private final GUIListener listener;

    public GUIDependencyManager(GUIListener listener, Path localRepoPath, boolean forceRefresh, int logLevel) {
        super(localRepoPath, forceRefresh, logLevel);
        this.listener = listener;
    }

    @Override
    protected RepositorySystemSession newRepositorySession(RepositorySystem system, LocalRepository localRepo) {
        final RepositorySystemSession s = super.newRepositorySession(system, localRepo);

        if (s instanceof DefaultRepositorySystemSession) {
            ((DefaultRepositorySystemSession) s).setTransferListener(listener.getTransferListener());
            ((DefaultRepositorySystemSession) s).setRepositoryListener(listener.getRepositoryListener());
        } else {
            return new AbstractForwardingRepositorySystemSession() {
                @Override
                protected RepositorySystemSession getSession() {
                    return s;
                }

                @Override
                public TransferListener getTransferListener() {
                    return listener.getTransferListener();
                }

                @Override
                public RepositoryListener getRepositoryListener() {
                    return listener.getRepositoryListener();
                }
            };
        }
        return s;
    }

}
