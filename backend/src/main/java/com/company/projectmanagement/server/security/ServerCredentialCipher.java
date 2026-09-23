package com.company.projectmanagement.server.security;

import com.company.projectmanagement.common.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用 AES-256-GCM 加密服务器凭据。
 *
 * <p>项目、服务器和密钥版本作为认证附加数据，密文被复制到其他资源后无法解密。
 */
@Component
public class ServerCredentialCipher {

    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String configuredMasterKey;
    private final String keyVersion;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public ServerCredentialCipher(
            @Value("${app.server-credential.master-key:}") String configuredMasterKey,
            @Value("${app.server-credential.key-version:v1}") String keyVersion,
            ObjectMapper objectMapper) {
        this.configuredMasterKey = configuredMasterKey;
        this.keyVersion = keyVersion;
        this.objectMapper = objectMapper;
    }

    /** 每次加密生成独立随机 nonce；返回值只能写入凭据表。 */
    public EncryptedCredential encrypt(
            Long projectId, Long serverId, String username, String password) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(new PlainCredential(username, password));
            Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, nonce, projectId, serverId, keyVersion);
            return new EncryptedCredential(cipher.doFinal(plaintext), nonce, keyVersion);
        } catch (IOException | GeneralSecurityException exception) {
            throw encryptionFailure();
        }
    }

    /** 只有业务服务完成负责人授权后才可调用解密。 */
    public PlainCredential decrypt(
            Long projectId,
            Long serverId,
            byte[] ciphertext,
            byte[] nonce,
            String storedKeyVersion) {
        try {
            Cipher cipher = newCipher(
                    Cipher.DECRYPT_MODE, nonce, projectId, serverId, storedKeyVersion);
            return objectMapper.readValue(cipher.doFinal(ciphertext), PlainCredential.class);
        } catch (IOException | GeneralSecurityException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SERVER_CREDENTIAL_DECRYPTION_FAILED",
                    "服务器凭据无法解密，请联系系统管理员检查密钥配置");
        }
    }

    private Cipher newCipher(
            int mode, byte[] nonce, Long projectId, Long serverId, String storedKeyVersion)
            throws GeneralSecurityException {
        byte[] key = masterKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(aad(projectId, serverId, storedKeyVersion));
        return cipher;
    }

    private byte[] masterKey() {
        if (!StringUtils.hasText(configuredMasterKey)) {
            throw configurationFailure();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredMasterKey.trim());
            if (decoded.length != 32) {
                throw configurationFailure();
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw configurationFailure();
        }
    }

    private static byte[] aad(
            Long projectId, Long serverId, String storedKeyVersion) {
        return ("server-credential:%d:%d:%s"
                        .formatted(projectId, serverId, storedKeyVersion))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static ApiException configurationFailure() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVER_CREDENTIAL_ENCRYPTION_NOT_CONFIGURED",
                "服务器凭据加密尚未正确配置");
    }

    private static ApiException encryptionFailure() {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SERVER_CREDENTIAL_ENCRYPTION_FAILED",
                "服务器凭据保存失败");
    }

    public record EncryptedCredential(byte[] ciphertext, byte[] nonce, String keyVersion) { }

    public record PlainCredential(String username, String password) { }
}
