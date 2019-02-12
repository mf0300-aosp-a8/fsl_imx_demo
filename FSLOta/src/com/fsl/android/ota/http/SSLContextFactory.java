package com.fsl.android.ota.http;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public class SSLContextFactory {

    private static SSLContextFactory theInstance = null;
    private Context mContext;

    private SSLContextFactory(Context context) {
        mContext = context;
    }

    public static SSLContextFactory getInstance(Context context) {
        if(theInstance == null) {
            theInstance = new SSLContextFactory(context);
        }
        return theInstance;
    }

    /**
     * Creates an SSLContext with the client and server certificates
     * @param clientCertFile A File containing the client certificate
     * @param clientCertPassword Password for the client certificate
     * @param caCertString A String containing the server certificate
     * @return An initialized SSLContext
     * @throws Exception
     */
    public SSLContext makeContext(String clientCertFile, String clientCertPassword, String caCertString) throws Exception {
        //final KeyStore keyStore = loadPKCS12KeyStore(clientCertFile, clientCertPassword);
        final KeyStore keyStore = loadPKCS12KeyStore(getCertificateFileFromAssert(clientCertFile), clientCertPassword);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(keyStore, clientCertPassword.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();

        final KeyStore trustStore = loadPEMTrustStore(caCertString);
        TrustManager[] trustManagers = {new ExtendedTrustManager(trustStore)};

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

    public SSLContext makeContext(byte[] clientCertFile, String clientCertPassword, String caCertString) throws Exception {
        final KeyStore keyStore = loadPKCS12KeyStore(clientCertFile, clientCertPassword);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(keyStore, clientCertPassword.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();

        final KeyStore trustStore = loadPEMTrustStore(caCertString);
        TrustManager[] trustManagers = {new ExtendedTrustManager(trustStore)};

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

    /**
     * Produces a KeyStore from a String containing a PEM certificate (typically, the server's CA certificate)
     * @param certificateString A String containing the PEM-encoded certificate
     * @return a KeyStore (to be used as a trust store) that contains the certificate
     * @throws Exception
     */
    private KeyStore loadPEMTrustStore(String certificateString) throws Exception {

        byte[] der = loadPemCertificate(new ByteArrayInputStream(certificateString.getBytes()));
        ByteArrayInputStream derInputStream = new ByteArrayInputStream(der);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(derInputStream);
        String alias = cert.getSubjectX500Principal().getName();

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        trustStore.setCertificateEntry(alias, cert);

        return trustStore;
    }

    /**
     * Produces a KeyStore from a PKCS12 (.p12) certificate file, typically the client certificate
     * @param certificateFile A file containing the client certificate
     * @param clientCertPassword Password for the certificate
     * @return A KeyStore containing the certificate from the certificateFile
     * @throws Exception
     */
    private KeyStore loadPKCS12KeyStore(File certificateFile, String clientCertPassword) throws Exception {
        KeyStore keyStore = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(certificateFile);
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(fis, clientCertPassword.toCharArray());
        } finally {
            try {
                if(fis != null) {
                    fis.close();
                }
            } catch(IOException ex) {
                // ignore
            }
        }
        return keyStore;
    }

    private File getCertificateFileFromAssert(String filename) {
        File f = new File(mContext.getCacheDir() + "/" + filename);
        if (!f.exists()) try {
            InputStream is = mContext.getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(buffer);
            fos.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return f;
    }

    private KeyStore loadPKCS12KeyStore(byte[] certificateFile, String clientCertPassword) throws Exception {
        KeyStore keyStore = null;
        ByteArrayInputStream bis = null;
        FileInputStream fis = null;
        try {
            bis = new ByteArrayInputStream(certificateFile);
            // fis = new FileInputStream(bis);
            // FileInputStream fis = new FileInputStream(certificateFile);
            /*
            byte[] der = loadPemCertificate(new ByteArrayInputStream(certificateFile.getBytes(StandardCharsets.UTF_8.name())));
            ByteArrayInputStream derInputStream = new ByteArrayInputStream(der);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(derInputStream);
            String alias = cert.getSubjectX500Principal().getName();

            keyStore = KeyStore.getInstance("PKCS12");
            fis = new ByteArrayInputStream(certificateFile.getBytes(StandardCharsets.UTF_8.name()));
            */
            keyStore = KeyStore.getInstance("PKCS12");
            // keyStore.load(fis, clientCertPassword.toCharArray());
            keyStore.load(null, null);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ci = cf.generateCertificate(bis);
            keyStore.setCertificateEntry("ca", ci);
            // keyStore.load(null);
            // keyStore.setCertificateEntry(alias, cert);
        } finally {
            try {
                if(bis != null) {
                    bis.close();
                }
                if(fis != null) {
                    fis.close();
                }
            } catch(IOException ex) {
                // ignore
            }
        }
        return keyStore;
    }

    /**
     * Reads and decodes a base-64 encoded DER certificate (a .pem certificate), typically the server's CA cert.
     * @param certificateStream an InputStream from which to read the cert
     * @return a byte[] containing the decoded certificate
     * @throws IOException
     */
    byte[] loadPemCertificate(InputStream certificateStream) throws IOException {

        byte[] der = null;
        BufferedReader br = null;

        try {
            StringBuilder buf = new StringBuilder();
            br = new BufferedReader(new InputStreamReader(certificateStream));

            String line = br.readLine();
            while(line != null) {
                if(!line.startsWith("--")){
                    buf.append(line);
                }
                line = br.readLine();
            }

            String pem = buf.toString();
            der = Base64.decode(pem, Base64.DEFAULT);

        } finally {
            if(br != null) {
                br.close();
            }
        }

        return der;
    }
}
