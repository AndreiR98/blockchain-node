package uk.co.roteala.glaciernode.security;

import lombok.Builder;
import lombok.Setter;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import uk.co.roteala.glaciernode.security.utils.HashingFactory;
import uk.co.roteala.utils.Base58;

import java.math.BigInteger;

@Builder
public class PrivateKey implements PrivKey {

    @Setter
    private String d;

    @Override
    public KeyType getType() {
        return KeyType.PRIVATE;
    }

    @Override
    public BigInteger getD() {
        return new BigInteger(this.d, 10);
    }

    @Override
    public String getHex() {
        return this.d;
    }

    @Override
    public byte[] getEncoded() {
        //TODO: Use ECFieldElement?
        return new BigInteger(this.d, 16).toByteArray();
    }

    @Override
    public String toAddress() {
        return this.getPublicKey()
                .toAddress();
    }

    @Override
    public String toWIF() {
        return generateWIF();
    }

    @Override
    public PublicKey getPublicKey() {
        ECNamedCurveParameterSpec ecParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1");

        ECPoint ecPoint = ecParameterSpec.getG();

        ECPoint publicPoint = ecPoint.multiply(new BigInteger(this.d, 16)).normalize();

        return PublicKey.builder()
                .ecPoint(publicPoint)
                .build();
    }

    private String generateWIF() {
        byte[] privateKeyBytes = this.getEncoded();

        if (privateKeyBytes[0] == 0) {
            byte[] tmp = new byte[privateKeyBytes.length - 1];
            System.arraycopy(privateKeyBytes, 1, tmp, 0, tmp.length);
            privateKeyBytes = tmp;
        }

        byte[] prefixPrivateKeyBytes = new byte[privateKeyBytes.length + 1];
        prefixPrivateKeyBytes[0] = (byte) 0x80;
        System.arraycopy(privateKeyBytes, 0, prefixPrivateKeyBytes, 1, privateKeyBytes.length);

        byte[] hash = HashingFactory.doubleSHA256(prefixPrivateKeyBytes);

        byte[] checksum = new byte[4];
        System.arraycopy(hash, 0, checksum, 0, 4);

        byte[] privateKeyWithChecksum = new byte[prefixPrivateKeyBytes.length + 4];
        System.arraycopy(prefixPrivateKeyBytes, 0, privateKeyWithChecksum, 0, prefixPrivateKeyBytes.length);
        System.arraycopy(checksum, 0, privateKeyWithChecksum, prefixPrivateKeyBytes.length, 4);

        return Base58.encode(privateKeyWithChecksum);
    }
}
