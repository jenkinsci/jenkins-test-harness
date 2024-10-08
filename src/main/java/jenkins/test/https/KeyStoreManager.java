/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.test.https;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Allows to manage a java keystore file more easily than base JDK.
 */
public class KeyStoreManager {
    @NonNull
    private final Path path;
    @NonNull
    private final URL url;
    @NonNull
    private final String password;
    @NonNull
    private final KeyStore keyStore;
    @NonNull
    private final String type;

    /**
     * Creates a new instance using the default keystore type.
     * @param path path of the keystore file. If it exists, it will be loaded automatically.
     * @param password password for the keystore file.
     */
    public KeyStoreManager(@NonNull Path path, @NonNull String password)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        this(path, password, KeyStore.getDefaultType());
    }

    /**
     * Creates a new instance using the specified keystore type.
     * @param path path of the keystore file. If it exists, it will be loaded automatically.
     * @param password password for the keystore file.
     * @param type type of the keystore file.
     */
    public KeyStoreManager(@NonNull Path path, @NonNull String password, @NonNull String type)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        this.path = path;
        this.url = path.toUri().toURL();
        this.password = password;
        this.type = type;
        var tmpKeyStore = KeyStore.getInstance(type);
        if (Files.exists(path)) {
            try (var is = Files.newInputStream(path)) {
                tmpKeyStore.load(is, password.toCharArray());
            }
        } else {
            tmpKeyStore.load(null);
        }
        this.keyStore = tmpKeyStore;
    }

    @NonNull
    private static X509TrustManager getDefaultX509CertificateTrustManager(TrustManagerFactory trustManagerFactory) {
        return Arrays.stream(trustManagerFactory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not load default trust manager"));
    }

    /**
     * @return the password for the managed keystore
     */
    public String getPassword() {
        return password;
    }

    /**
     * @return the path where the managed keystore is persisted to.
     * Make sure {@link #save()} has been called before using the path.
     */
    public Path getPath() {
        return path;
    }

    /**
     * @return the type of the managed keystore.
     */
    public String getType() {
        return type;
    }

    /**
     * @return returns the URL representation of the keystore file.
     * <p>
     * Make sure {@link #save()} has been called before using the path.
     */
    public URL getURL() {
        return url;
    }

    /**
     * Persists the current keystore to disk.
     */
    public void save() throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        try (var os = Files.newOutputStream(path)) {
            keyStore.store(os, password.toCharArray());
        }
    }

    /**
     * Build a custom SSL context that trusts the default certificates as well as those in the current keystore.
     */
    @NonNull
    public SSLContext buildClientSSLContext()
            throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException,
                    KeyManagementException {
        X509TrustManager result;
        try (var myKeysInputStream = Files.newInputStream(path)) {
            var myTrustStore = KeyStore.getInstance(type);
            myTrustStore.load(myKeysInputStream, password.toCharArray());
            var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(myTrustStore);
            result = getDefaultX509CertificateTrustManager(trustManagerFactory);
        }
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        var wrapper = new MergedTrustManager(getDefaultX509CertificateTrustManager(trustManagerFactory), result);
        var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {wrapper}, null);
        return context;
    }

    /**
     * Build server context for server usage.
     * @return a SSLContext instance configured with the key store.
     */
    public SSLContext buildServerSSLContext() {
        final KeyManager[] keyManagers;
        try {
            var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password.toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new RuntimeException("Unable to initialise KeyManager[]", e);
        }

        final TrustManager[] trustManagers;
        try {
            var trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException("Unable to initialise TrustManager[]", e);
        }

        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Unable to create and initialise the SSLContext", e);
        }
    }

    /**
     * @see KeyStore#setCertificateEntry(String, Certificate)
     */
    public void setCertificateEntry(String alias, X509Certificate certificate) throws KeyStoreException {
        keyStore.setCertificateEntry(alias, certificate);
    }

    /**
     * @see KeyStore#setKeyEntry(String, byte[], Certificate[])
     */
    public void setKeyEntry(String host, PrivateKey privateKey, Certificate[] certificates) throws KeyStoreException {
        keyStore.setKeyEntry(host, privateKey, password.toCharArray(), certificates);
    }

    private static class MergedTrustManager implements X509TrustManager {
        private final X509TrustManager defaultTrustManager;
        private final List<X509TrustManager> trustManagers;

        public MergedTrustManager(X509TrustManager defaultTrustManager, X509TrustManager... trustManagers) {
            this.defaultTrustManager = defaultTrustManager;
            this.trustManagers = List.of(trustManagers);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManagers.stream()
                    .map(X509TrustManager::getAcceptedIssuers)
                    .flatMap(Arrays::stream)
                    .toArray(X509Certificate[]::new);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException exceptionResult = null;
            for (var trustManager : trustManagers) {
                try {
                    trustManager.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    if (exceptionResult == null) {
                        exceptionResult = e;
                    } else {
                        exceptionResult.addSuppressed(e);
                    }
                }
            }
            if (exceptionResult != null) {
                throw exceptionResult;
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            defaultTrustManager.checkClientTrusted(trustManagers.stream()
                    .map(X509TrustManager::getAcceptedIssuers)
                    .flatMap(Arrays::stream)
                    .toArray(X509Certificate[]::new), authType);
        }
    }
}
