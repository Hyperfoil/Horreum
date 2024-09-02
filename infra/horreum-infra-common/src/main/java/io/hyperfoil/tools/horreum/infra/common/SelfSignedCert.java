package io.hyperfoil.tools.horreum.infra.common;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Generates a self-signed certificate using the BouncyCastle lib.
 */
public final class SelfSignedCert {

    private final X509Certificate certificate;
    private final KeyPair keyPair;

    public SelfSignedCert(String keyAlgorithm, String hashAlgorithm, String cn, int days)
            throws OperatorCreationException, CertificateException, CertIOException, NoSuchAlgorithmException {
        keyPair = KeyPairGenerator.getInstance(keyAlgorithm).generateKeyPair();

        Instant now = Instant.now();
        X500Name x500Name = new X500Name("CN=" + cn);

        X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                x500Name,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now),
                Date.from(now.plus(Duration.ofDays(days))),
                x500Name,
                keyPair.getPublic())
                .addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()))
                .addExtension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()))
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        certificate = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(
                        certificateBuilder.build(new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate())));
    }

    public String getCertString() throws IOException {
        StringWriter certWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(certWriter)) {
            pemWriter.writeObject(certificate);
        }
        return certWriter.toString();
    }

    public String getKeyString() throws IOException {
        StringWriter keyWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(keyWriter)) {
            pemWriter.writeObject(keyPair.getPrivate());
        }
        return keyWriter.toString();
    }

    // --- //

    /**
     * Creates the hash value of the public key.
     */
    private static SubjectKeyIdentifier createSubjectKeyId(PublicKey publicKey) throws OperatorCreationException {
        return new X509ExtensionUtils(
                new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1)))
                .createSubjectKeyIdentifier(SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));
    }

    /**
     * Creates the hash value of the authority public key.
     */
    private static AuthorityKeyIdentifier createAuthorityKeyId(PublicKey publicKey) throws OperatorCreationException {
        return new X509ExtensionUtils(
                new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1)))
                .createAuthorityKeyIdentifier(SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));
    }
}
