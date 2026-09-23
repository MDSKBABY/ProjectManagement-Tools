package com.company.projectmanagement.server.domain;

/** 只在服务内部流转的服务器凭据密文，永不直接作为接口响应。 */
public class ServerCredential {
    private Long serverId;
    private byte[] ciphertext;
    private byte[] nonce;
    private String keyVersion;
    private Long updatedBy;

    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public byte[] getCiphertext() { return ciphertext; }
    public void setCiphertext(byte[] ciphertext) { this.ciphertext = ciphertext; }
    public byte[] getNonce() { return nonce; }
    public void setNonce(byte[] nonce) { this.nonce = nonce; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
