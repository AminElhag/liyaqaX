package com.liyaqa.zatca.service

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.springframework.stereotype.Service
import java.io.StringWriter
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.security.auth.x500.X500Principal

@Service
class ZatcaCryptoService {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(ECGenParameterSpec("secp256k1"))
        return kpg.generateKeyPair()
    }

    fun exportPrivateKeyBase64(keyPair: KeyPair): String = Base64.getEncoder().encodeToString(keyPair.private.encoded)

    fun importPrivateKeyFromBase64(base64: String): ECPrivateKey {
        val keyBytes = Base64.getDecoder().decode(base64)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("EC", "BC").generatePrivate(keySpec) as ECPrivateKey
    }

    fun buildCsr(
        keyPair: KeyPair,
        vatNumber: String,
        egsSerialNumber: String,
        organizationName: String,
        countryCode: String = "SA",
        invoiceType: String = "1000",
    ): String {
        val subjectDn =
            "CN=$egsSerialNumber,O=$organizationName,C=$countryCode," +
                "OU=$invoiceType,2.5.4.97=$vatNumber"

        val csrBuilder =
            JcaPKCS10CertificationRequestBuilder(
                X500Principal(subjectDn),
                keyPair.public,
            )

        val signer =
            JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider("BC")
                .build(keyPair.private)

        val csr = csrBuilder.build(signer)

        val sw = StringWriter()
        JcaPEMWriter(sw).use { writer ->
            writer.writeObject(csr)
        }
        return sw.toString()
    }

    fun signData(
        privateKeyBase64: String,
        data: ByteArray,
    ): ByteArray {
        val privateKey = importPrivateKeyFromBase64(privateKeyBase64)
        val signer = java.security.Signature.getInstance("SHA256withECDSA", "BC")
        signer.initSign(privateKey)
        signer.update(data)
        return signer.sign()
    }

    fun getPublicKeyDerBase64(keyPair: KeyPair): String = Base64.getEncoder().encodeToString(keyPair.public.encoded)
}
