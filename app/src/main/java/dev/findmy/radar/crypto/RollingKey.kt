package dev.findmy.radar.crypto

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Port of FindMy.py's accessory key derivation (findmy/util/crypto.py):
 * X9.63 KDF chain plus the per-index private-key diversification on P-224.
 */
object RollingKey {

    /** ANSI-X9.63 KDF with SHA-256: Hash(Z || counter32BE || sharedInfo), counter from 1. */
    fun x963Kdf(z: ByteArray, info: ByteArray, length: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val out = ByteArrayOutputStream()
        var counter = 1
        while (out.size() < length) {
            md.update(z)
            md.update(
                byteArrayOf(
                    (counter ushr 24).toByte(),
                    (counter ushr 16).toByte(),
                    (counter ushr 8).toByte(),
                    counter.toByte(),
                ),
            )
            md.update(info)
            out.write(md.digest())
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    /** One step of the secret-key chain: sk(n) = KDF(sk(n-1), "update"). */
    fun nextSk(sk: ByteArray): ByteArray = x963Kdf(sk, "update".toByteArray(), 32)

    /**
     * Accessory private key for secret key [sk]: d = (u*master + v) mod n,
     * with u and v from KDF(sk, "diversify", 72).
     */
    fun derivePrivateKey(masterKey: ByteArray, sk: ByteArray): ByteArray {
        val at = x963Kdf(sk, "diversify".toByteArray(), 72)
        val nMinus1 = P224.N.subtract(BigInteger.ONE)
        val u = BigInteger(1, at.copyOf(36)).mod(nMinus1).add(BigInteger.ONE)
        val v = BigInteger(1, at.copyOfRange(36, 72)).mod(nMinus1).add(BigInteger.ONE)
        val d = u.multiply(BigInteger(1, masterKey)).add(v).mod(P224.N)
        val b = d.toByteArray()
        val out = ByteArray(28)
        val copyLen = minOf(b.size, 28)
        System.arraycopy(b, b.size - copyLen, out, 28 - copyLen, copyLen)
        return out
    }

    /** 28-byte advertisement key (P-224 public-key x coordinate) for a private key. */
    fun advKey(privateKey: ByteArray): ByteArray = P224.publicKeyX(BigInteger(1, privateKey))
}
