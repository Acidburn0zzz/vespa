// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metric values produced by {@link Transport}.
 *
 * @author bjorncs
 */
public class TransportMetrics {

    private final AtomicLong tlsCertificateVerificationFailures = new AtomicLong(0);
    private final AtomicLong peerAuthorizationFailures = new AtomicLong(0);
    private final AtomicLong serverTlsConnectionsEstablished = new AtomicLong(0);
    private final AtomicLong clientTlsConnectionsEstablished = new AtomicLong(0);
    private final AtomicLong serverUnencryptedConnectionsEstablished = new AtomicLong(0);
    private final AtomicLong clientUnencryptedConnectionsEstablished = new AtomicLong(0);

    public long tlsCertificateVerificationFailures() {
        return tlsCertificateVerificationFailures.get();
    }

    public long peerAuthorizationFailures() {
        return peerAuthorizationFailures.get();
    }

    public long serverTlsConnectionsEstablished() {
        return serverTlsConnectionsEstablished.get();
    }

    public long clientTlsConnectionsEstablished() {
        return clientTlsConnectionsEstablished.get();
    }

    public long serverUnencryptedConnectionsEstablished() {
        return serverUnencryptedConnectionsEstablished.get();
    }

    public long clientUnencryptedConnectionsEstablished() {
        return clientUnencryptedConnectionsEstablished.get();
    }

    void incrementTlsCertificateVerificationFailures() {
        tlsCertificateVerificationFailures.incrementAndGet();
    }

    void incrementPeerAuthorizationFailures() {
        peerAuthorizationFailures.incrementAndGet();
    }

    void incrementServerTlsConnectionsEstablished() {
        serverTlsConnectionsEstablished.incrementAndGet();
    }

    void incrementClientTlsConnectionsEstablished() {
        clientTlsConnectionsEstablished.incrementAndGet();
    }

    void incrementServerUnencryptedConnectionsEstablished() {
        serverUnencryptedConnectionsEstablished.incrementAndGet();
    }

    void incrementClientUnencryptedConnectionsEstablished() {
        clientUnencryptedConnectionsEstablished.incrementAndGet();
    }

    @Override
    public String toString() {
        return "TransportMetrics{" +
                "tlsCertificateVerificationFailures=" + tlsCertificateVerificationFailures +
                ", peerAuthorizationFailures=" + peerAuthorizationFailures +
                ", serverTlsConnectionsEstablished=" + serverTlsConnectionsEstablished +
                ", clientTlsConnectionsEstablished=" + clientTlsConnectionsEstablished +
                ", serverUnencryptedConnectionsEstablished=" + serverUnencryptedConnectionsEstablished +
                ", clientUnencryptedConnectionsEstablished=" + clientUnencryptedConnectionsEstablished +
                '}';
    }
}
