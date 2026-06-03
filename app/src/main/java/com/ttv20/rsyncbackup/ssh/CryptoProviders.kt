package com.ttv20.rsyncbackup.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

internal object CryptoProviders {
    fun ensureModernBouncyCastleProvider() {
        val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (existing?.javaClass?.name == BouncyCastleProvider::class.java.name) {
            return
        }

        if (existing != null) {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        }
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
