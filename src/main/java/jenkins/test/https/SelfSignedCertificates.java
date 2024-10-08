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
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A utility class to generate self-signed certificates.
 * <ul>
 *   <li>A root CA
 *   <li>An intermediate CA signed by the root CA
 * </ul>
 * Then you can generate user certificates for a given DNS name signed by the intermediate CA.
 *
 * @see #createRootCAs()
 * @see #createUserCert(String, CertificateKeyPair)
 */
public record SelfSignedCertificates(CertificateKeyPair root, CertificateKeyPair intermediate) {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CERTIFICATE_ALGORITHM = "RSA";
    private static final int CERTIFICATE_BITS = 2048;
    private static final String ROOT_DN = "CN=Root";

    static {
        // adds the Bouncy castle provider to java security
        Security.addProvider(new BouncyCastleProvider());
    }

    @NonNull
    public static SelfSignedCertificates createRootCAs() {
        try {
            // create root CA self-signed.
            var rootKeyPair = generateKeyPair();
            var rootBuilder = new JcaX509v3CertificateBuilder(
                    new X500Name(ROOT_DN),
                    generateSerialNumber(),
                    Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                    Date.from(LocalDate.now()
                            .plusDays(4)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()),
                    new X500Name(ROOT_DN),
                    SubjectPublicKeyInfo.getInstance(rootKeyPair.getPublic().getEncoded()));
            rootBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign));
            rootBuilder.addExtension(Extension.basicConstraints, false, new BasicConstraints(true));
            var root = new CertificateKeyPair(rootKeyPair, new JcaX509CertificateConverter().getCertificate(rootBuilder.build(newContentSigner(rootKeyPair))));

            // create Intermediate CA cert signed by Root CA
            var intermediateKeyPair = generateKeyPair();
            var intermediateBuilder = new JcaX509v3CertificateBuilder(
                    root.certificate(),
                    generateSerialNumber(),
                    Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                    Date.from(LocalDate.now()
                            .plusDays(2)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()),
                    new X500Name("CN=Intermediate"),
                    intermediateKeyPair.getPublic());
            intermediateBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign));
            intermediateBuilder.addExtension(Extension.basicConstraints, false, new BasicConstraints(true));
            var intermediate = new CertificateKeyPair(intermediateKeyPair, new JcaX509CertificateConverter().getCertificate(intermediateBuilder.build(newContentSigner(root.keyPair()))));
            return new SelfSignedCertificates(root, intermediate);
        } catch (OperatorCreationException | CertificateException | CertIOException | NoSuchAlgorithmException e) {
            throw new SelfSignedCertificatesException(e);
        }
    }

    private static ContentSigner newContentSigner(KeyPair keyPair) throws OperatorCreationException {
        return new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.getPrivate());
    }

    @NonNull
    static BigInteger generateSerialNumber() {
        return BigInteger.valueOf(RANDOM.nextInt());
    }

    /**
     * Generate a user certificate signed by the intermediate CA.
     * @param dnsName The DNS name to use in the certificate.
     * @param issuer The intermediate CA to sign the certificate.
     * @return The user certificate and key pair.
     */
    public static CertificateKeyPair createUserCert(String dnsName, CertificateKeyPair issuer) {
        try {
            var keyPair = generateKeyPair();
            // create end user cert signed by Intermediate CA
            var builder = new JcaX509v3CertificateBuilder(
                    issuer.certificate(),
                    generateSerialNumber(),
                    Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                    Date.from(LocalDate.now()
                            .plusDays(1)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()),
                    new X500Name("CN=endUserCert"),
                    keyPair.getPublic());
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
            var altNames = List.of(new GeneralName(GeneralName.dNSName, dnsName));
            var subjectAltNames = GeneralNames.getInstance(new DERSequence(altNames.toArray(new GeneralName[0])));
            builder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
            return new CertificateKeyPair(
                    keyPair,
                    new JcaX509CertificateConverter().getCertificate(builder.build(newContentSigner(issuer.keyPair()))));
        } catch (OperatorCreationException | CertificateException | CertIOException | NoSuchAlgorithmException e) {
            throw new SelfSignedCertificatesException(e);
        }
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        var keyPairGenerator = KeyPairGenerator.getInstance(CERTIFICATE_ALGORITHM);
        keyPairGenerator.initialize(CERTIFICATE_BITS, RANDOM);
        return keyPairGenerator.generateKeyPair();
    }

    public record CertificateKeyPair(KeyPair keyPair, X509Certificate certificate) {}
}
