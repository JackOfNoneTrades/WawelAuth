package org.fentanylsolutions.wawelauth.wawelclient;

/** Client-only persistence state supplied by the ServerList mixin. */
public interface IServerListPersistence {

    boolean wawelauth$isLoaded();

    boolean wawelauth$isCurrent();

    /** Synchronous on the client thread; off-thread callers are queued and return false. */
    boolean wawelauth$saveSafely();
}
