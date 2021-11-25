package com.fsl.android.ota.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by a2.tishchenko on 20.12.17.
 */

public class IOUtil {
    public static String readFully(InputStream inputStream) throws IOException {

        if(inputStream == null) {
            return "";
        }

        BufferedInputStream bufferedInputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = null;

        try {
            bufferedInputStream = new BufferedInputStream(inputStream);
            byteArrayOutputStream = new ByteArrayOutputStream();

            final byte[] buffer = new byte[1024];
            int available = 0;

            while ((available = bufferedInputStream.read(buffer)) >= 0) {
                byteArrayOutputStream.write(buffer, 0, available);
            }

            return byteArrayOutputStream.toString();

        } finally {
            if(bufferedInputStream != null) {
                bufferedInputStream.close();
            }
        }
    }

    public static byte[] readFullyByte(InputStream inputStream) throws IOException {

        if(inputStream == null) return null;

        BufferedInputStream bufferedInputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = null;

        try {
            bufferedInputStream = new BufferedInputStream(inputStream);
            byteArrayOutputStream = new ByteArrayOutputStream();

            final byte[] buffer = new byte[1024];
            int available = 0;

            while ((available = bufferedInputStream.read(buffer)) >= 0) {
                byteArrayOutputStream.write(buffer, 0, available);
            }

            return byteArrayOutputStream.toByteArray();

        } finally {
            if(bufferedInputStream != null) {
                bufferedInputStream.close();
            }
        }
    }
}
