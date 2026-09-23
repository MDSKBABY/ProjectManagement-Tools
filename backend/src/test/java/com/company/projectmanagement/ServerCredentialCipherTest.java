package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.server.security.ServerCredentialCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 不连接数据库，独立验证服务器凭据认证加密的关键安全属性。 */
class ServerCredentialCipherTest {

    private static final String TEST_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void roundTripsCredentialAndUsesFreshNonceForEveryEncryption() {
        ServerCredentialCipher cipher = new ServerCredentialCipher(TEST_KEY, "test-v1", new ObjectMapper());

        ServerCredentialCipher.EncryptedCredential first =
                cipher.encrypt(10L, 20L, "deploy", "test-password");
        ServerCredentialCipher.EncryptedCredential second =
                cipher.encrypt(10L, 20L, "deploy", "test-password");

        assertThat(first.nonce()).hasSize(12).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(cipher.decrypt(
                        10L, 20L, first.ciphertext(), first.nonce(), first.keyVersion()))
                .isEqualTo(new ServerCredentialCipher.PlainCredential("deploy", "test-password"));
    }

    @Test
    void rejectsCiphertextMovedToAnotherServer() {
        ServerCredentialCipher cipher = new ServerCredentialCipher(TEST_KEY, "test-v1", new ObjectMapper());
        ServerCredentialCipher.EncryptedCredential encrypted =
                cipher.encrypt(10L, 20L, "deploy", "test-password");

        assertThatThrownBy(() -> cipher.decrypt(
                        10L, 21L, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SERVER_CREDENTIAL_DECRYPTION_FAILED"));
    }

    @Test
    void failsClosedWhenMasterKeyIsMissingOrInvalid() {
        ServerCredentialCipher missing = new ServerCredentialCipher("", "v1", new ObjectMapper());
        ServerCredentialCipher invalid = new ServerCredentialCipher("dG9vLXNob3J0", "v1", new ObjectMapper());

        assertThatThrownBy(() -> missing.encrypt(1L, 2L, "user", "password"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("SERVER_CREDENTIAL_ENCRYPTION_NOT_CONFIGURED"));
        assertThatThrownBy(() -> invalid.encrypt(1L, 2L, "user", "password"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("SERVER_CREDENTIAL_ENCRYPTION_NOT_CONFIGURED"));
    }
}
