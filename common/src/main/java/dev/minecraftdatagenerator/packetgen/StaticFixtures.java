package dev.minecraftdatagenerator.packetgen;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

/** Reproducible public wire fixtures; never used for real authentication or encryption. */
public final class StaticFixtures {
    private StaticFixtures() {}
    public static PublicKey rsaPublicKey(int bits) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                BigInteger.ONE.shiftLeft(bits - 1).add(BigInteger.valueOf(643)), BigInteger.valueOf(65537)));
    }
}
