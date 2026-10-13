package dev.findmy.radar.crypto

import java.math.BigInteger

/**
 * NIST P-224 (secp224r1) arithmetic — only what key derivation needs:
 * scalar multiplication of the base point, returning the affine x coordinate.
 *
 * Android's built-in crypto providers do not support P-224, so the point math
 * (Jacobian coordinates, curve coefficient a = -3) is implemented directly on
 * BigInteger.
 */
internal object P224 {

    private val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF000000000000000000000001", 16)
    val N: BigInteger =
        BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFF16A2E0B8F03E13DD29455C5C2A3D", 16)

    private val GX = BigInteger("B70E0CBD6BB4BF7F321390B94A03C1D356C21122343280D6115C1D21", 16)
    private val GY = BigInteger("BD376388B5F723FB4C22DFE6CD4375A05A07476444D5819985007E34", 16)

    private val THREE = BigInteger.valueOf(3)
    private val FOUR = BigInteger.valueOf(4)
    private val EIGHT = BigInteger.valueOf(8)

    /** Jacobian point; z == 0 marks the point at infinity. */
    private class JPoint(val x: BigInteger, val y: BigInteger, val z: BigInteger)

    private fun infinity() = JPoint(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    /** 28-byte big-endian x coordinate of k*G — an AirTag advertisement key. */
    fun publicKeyX(k: BigInteger): ByteArray {
        var r = infinity()
        for (i in k.bitLength() - 1 downTo 0) {
            r = double(r)
            if (k.testBit(i)) r = addAffine(r, GX, GY)
        }
        require(r.z.signum() != 0) { "scalar produced the point at infinity" }
        val zInv = r.z.modInverse(P)
        val x = r.x.multiply(zInv).multiply(zInv).mod(P)
        return toFixed28(x)
    }

    private fun double(p: JPoint): JPoint {
        if (p.z.signum() == 0 || p.y.signum() == 0) return infinity()
        val yy = p.y.multiply(p.y).mod(P)
        val s = FOUR.multiply(p.x).multiply(yy).mod(P)
        val zz = p.z.multiply(p.z).mod(P)
        val m = THREE.multiply(p.x.subtract(zz)).multiply(p.x.add(zz)).mod(P)
        val x3 = m.multiply(m).subtract(s.shiftLeft(1)).mod(P)
        val y3 = m.multiply(s.subtract(x3)).subtract(EIGHT.multiply(yy).multiply(yy)).mod(P)
        val z3 = p.y.multiply(p.z).shiftLeft(1).mod(P)
        return JPoint(x3, y3, z3)
    }

    private fun addAffine(p: JPoint, ax: BigInteger, ay: BigInteger): JPoint {
        if (p.z.signum() == 0) return JPoint(ax, ay, BigInteger.ONE)
        val z1z1 = p.z.multiply(p.z).mod(P)
        val u2 = ax.multiply(z1z1).mod(P)
        val s2 = ay.multiply(p.z).multiply(z1z1).mod(P)
        val h = u2.subtract(p.x).mod(P)
        val r = s2.subtract(p.y).mod(P)
        if (h.signum() == 0) return if (r.signum() == 0) double(p) else infinity()
        val hh = h.multiply(h).mod(P)
        val hhh = h.multiply(hh).mod(P)
        val v = p.x.multiply(hh).mod(P)
        val x3 = r.multiply(r).subtract(hhh).subtract(v.shiftLeft(1)).mod(P)
        val y3 = r.multiply(v.subtract(x3)).subtract(p.y.multiply(hhh)).mod(P)
        val z3 = p.z.multiply(h).mod(P)
        return JPoint(x3, y3, z3)
    }

    private fun toFixed28(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        val out = ByteArray(28)
        val copyLen = minOf(b.size, 28)
        System.arraycopy(b, b.size - copyLen, out, 28 - copyLen, copyLen)
        return out
    }
}
