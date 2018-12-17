// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.tls.TlsContext;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

/**
 * A {@link CryptoSocket} that creates {@link TlsCryptoSocket} instances.
 *
 * @author bjorncs
 */
public class TlsCryptoEngine implements CryptoEngine {

    private final TlsContext tlsContext;

    public TlsCryptoEngine(TlsContext tlsContext) {
        this.tlsContext = tlsContext;
    }

    @Override
    public TlsCryptoSocket createCryptoSocket(TransportMetrics metrics, SocketChannel channel, boolean isServer)  {
        SSLEngine sslEngine = tlsContext.createSslEngine();
        sslEngine.setNeedClientAuth(true);
        sslEngine.setUseClientMode(!isServer);
        return new TlsCryptoSocket(metrics, channel, sslEngine);
    }

    @Override
    public void close() {
        tlsContext.close();
    }

}
