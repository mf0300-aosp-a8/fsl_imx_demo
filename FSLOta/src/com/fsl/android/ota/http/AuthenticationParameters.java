package com.fsl.android.ota.http;

import java.io.File;

/**
 * Created by a2.tishchenko on 20.12.17.
 */

public class AuthenticationParameters {
    private File clientCertificate = null;
    private byte[] p12cert = null;
    private String clientCertificateAssertName = null;
    private String clientCertificatePassword = null;
    private String caCertificate = null;

    public File getClientCertificate() {
        return clientCertificate;
    }

    public byte[] getClientCertificateP12Content() {
        return p12cert;
    }

    public void setClientCertificateAssertName(String path) {
        clientCertificateAssertName = path;
    }

    public String getClientCertificateAssertName() {
        return clientCertificateAssertName;
    }

    public void setClientCertificate(File clientCertificate) {
        this.clientCertificate = clientCertificate;
    }

    public String getClientCertificatePassword() {
        return clientCertificatePassword;
    }

    public void setP12Certificate(byte[] p) {
        p12cert = p;
    }
    public void setClientCertificatePassword(String clientCertificatePassword) {
        this.clientCertificatePassword = clientCertificatePassword;
    }

    public String getCaCertificate() {
        return caCertificate;
    }

    public void setCaCertificate(String caCertificate) {
        this.caCertificate = caCertificate;
    }
}
