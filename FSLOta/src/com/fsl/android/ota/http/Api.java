package com.fsl.android.ota.http;

import android.content.Context;
import android.util.Log;

import com.fsl.android.ota.util.IOUtil;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class Api {
    private String TAG = Api.class.getSimpleName();

    private String access_key;
    private String secret_key;
    private String hardware_id;

    private SSLContext sslContext;
    private int lastResponseCode;
    private Context mContext = null;
    private HttpURLConnection urlConnection = null;

    public int getLastResponseCode() {
        return lastResponseCode;
    }

    public Api(Context context, AuthenticationParameters authParams) throws Exception {
        mContext = context;
        String clientCertFile = authParams.getClientCertificateAssertName();
        // trying to load from assert
        sslContext = SSLContextFactory.getInstance(mContext).makeContext(clientCertFile, authParams.getClientCertificatePassword(), authParams.getCaCertificate());
        CookieHandler.setDefault(new CookieManager());
    }

    public void setAccessKey(String key) {
        access_key = key;
    }

    public void setSecretKey(String key) {
        secret_key = key;
    }

    public void setHardwareId(String key) {
        hardware_id = key;
    }

    private static final char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    protected char[] encodeHex(final byte[] data, final char[] toDigits) {
        final int l = data.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
            out[j++] = toDigits[0x0F & data[i]];
        }
        return out;
    }

    public String getHash(String payload) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec sk = new SecretKeySpec(secret_key.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(sk);
            return new String(encodeHex(sha256_HMAC.doFinal(payload.getBytes("UTF-8")), DIGITS_LOWER));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;    }

     public String doGet(String url)  throws Exception {
        String result = null;
        HttpURLConnection urlConnection = null;
        Log.d(TAG, "doGet: was called");
        try {
            Log.d(TAG, "doGet: new URL(" + url + ")");
            URL requestedUrl = new URL(url);
            Log.d(TAG, "doGet: (HttpURLConnection) requestedUrl.openConnection()");
            urlConnection = (HttpURLConnection) requestedUrl.openConnection();
            if(urlConnection instanceof HttpsURLConnection) {
                Log.d(TAG, "doGet: ((HttpsURLConnection)urlConnection).setSSLSocketFactory(sslContext.getSocketFactory())");
                ((HttpsURLConnection)urlConnection).setSSLSocketFactory(sslContext.getSocketFactory());
            }
            urlConnection.setRequestMethod("GET");
            urlConnection.addRequestProperty("X-Hardware-Id", hardware_id);
            urlConnection.setConnectTimeout(1500);
            urlConnection.setReadTimeout(1500);
            Log.d(TAG, "doGet: HardwareId:" + hardware_id);
            lastResponseCode = urlConnection.getResponseCode();
            result = IOUtil.readFully(urlConnection.getInputStream());
            Log.d(TAG, "doGet: urlConnection:result:" + result);
        } catch(Exception e) {
            e.printStackTrace();
            result = e.toString();
        } finally {
            if(urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    public int doGetSize() {
        if (urlConnection != null) {
            return urlConnection.getContentLength();
        }
        return -1;
    }

    public InputStream doGetStream(String url)  throws Exception {
        InputStream res = null;
        String result = null;
        Log.d(TAG, "doGetStream: was called");
        try {
            URL requestedUrl = new URL(url);
            urlConnection = (HttpURLConnection) requestedUrl.openConnection();
            if(urlConnection instanceof HttpsURLConnection) {
                ((HttpsURLConnection)urlConnection).setSSLSocketFactory(sslContext.getSocketFactory());
            }
            urlConnection.setRequestMethod("GET");
            urlConnection.addRequestProperty("X-Hardware-Id", hardware_id);
            urlConnection.setConnectTimeout(60000);
            urlConnection.setReadTimeout(60000);
            Log.d(TAG, "doGet: HardwareId:" + hardware_id);
            lastResponseCode = urlConnection.getResponseCode();
            res = urlConnection.getInputStream();
        } catch(Exception e) {
            e.printStackTrace();
            if(urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return res;
    }
}
