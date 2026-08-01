/*
Copyright 2026 The Spice.ai OSS Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ai.spice;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Self-signed certificate fixtures for TLS/mTLS tests, generated at runtime
 * with BouncyCastle (already a compile dependency of the SDK): a CA, a server
 * certificate for localhost/127.0.0.1, a client certificate signed by the same
 * CA, and a second, unrelated CA for negative tests. All materialized as PEM
 * files, matching the SDK's file-based TLS configuration.
 */
final class TestCerts {

    private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis());

    final Path caCert;
    final Path serverCert;
    final Path serverKey;
    final Path clientCert;
    final Path clientKey;
    /** A CA unrelated to any issued certificate, for trust-failure tests. */
    final Path otherCaCert;

    private TestCerts(Path caCert, Path serverCert, Path serverKey,
            Path clientCert, Path clientKey, Path otherCaCert) {
        this.caCert = caCert;
        this.serverCert = serverCert;
        this.serverKey = serverKey;
        this.clientCert = clientCert;
        this.clientKey = clientKey;
        this.otherCaCert = otherCaCert;
    }

    static TestCerts generate() throws Exception {
        Path dir = Files.createTempDirectory("spice-test-certs");

        KeyPair caKeys = newKeyPair();
        X509Certificate ca = newCaCert("CN=Spice Test CA", caKeys);

        KeyPair serverKeys = newKeyPair();
        X509Certificate server = newLeafCert("CN=localhost", serverKeys, ca, caKeys.getPrivate(),
                KeyPurposeId.id_kp_serverAuth, true);

        KeyPair clientKeys = newKeyPair();
        X509Certificate client = newLeafCert("CN=spice-test-client", clientKeys, ca, caKeys.getPrivate(),
                KeyPurposeId.id_kp_clientAuth, false);

        KeyPair otherCaKeys = newKeyPair();
        X509Certificate otherCa = newCaCert("CN=Unrelated Test CA", otherCaKeys);

        return new TestCerts(
                writePem(dir.resolve("ca.pem"), ca),
                writePem(dir.resolve("server.pem"), server),
                writeKeyPem(dir.resolve("server-key.pem"), serverKeys.getPrivate()),
                writePem(dir.resolve("client.pem"), client),
                writeKeyPem(dir.resolve("client-key.pem"), clientKeys.getPrivate()),
                writePem(dir.resolve("other-ca.pem"), otherCa));
    }

    private static KeyPair newKeyPair() throws Exception {
        // EC P-256: ~300x faster to generate than RSA-2048 (which shows
        // multi-second outliers on shared CI runners), equally supported by
        // Netty/gRPC and the SDK's PEM parsing.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static X509Certificate newCaCert(String subject, KeyPair keys) throws Exception {
        X500Name name = new X500Name(subject);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(SERIAL.incrementAndGet()),
                notBefore(), notAfter(), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        return sign(builder, keys.getPrivate());
    }

    private static X509Certificate newLeafCert(String subject, KeyPair keys,
            X509Certificate issuer, PrivateKey issuerKey, KeyPurposeId purpose,
            boolean withLocalhostSan) throws Exception {
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, BigInteger.valueOf(SERIAL.incrementAndGet()),
                notBefore(), notAfter(), new X500Name(subject), keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purpose));
        if (withLocalhostSan) {
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(
                    new GeneralName[] {
                            new GeneralName(GeneralName.dNSName, "localhost"),
                            new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                    }));
        }
        return sign(builder, issuerKey);
    }

    private static X509Certificate sign(X509v3CertificateBuilder builder, PrivateKey key) throws Exception {
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(key);
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static Date notBefore() {
        return new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
    }

    private static Date notAfter() {
        return new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
    }

    private static Path writePem(Path path, Object pemObject) throws Exception {
        try (JcaPEMWriter writer = new JcaPEMWriter(Files.newBufferedWriter(path))) {
            writer.writeObject(pemObject);
        }
        return path;
    }

    private static Path writeKeyPem(Path path, PrivateKey key) throws Exception {
        // PKCS#8 ("PRIVATE KEY"), accepted by both Netty (Flight path) and
        // the SDK's BouncyCastle PEM parsing (HTTP path).
        return writePem(path, new JcaPKCS8Generator(key, null));
    }
}
