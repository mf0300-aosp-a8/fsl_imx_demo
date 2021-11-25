/*
/* Copyright 2012-2015 Freescale Semiconductor, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fsl.android.ota;

import android.os.SystemProperties;

import java.net.*;
import java.security.GeneralSecurityException;
import java.io.*;
import java.util.concurrent.Semaphore;

import android.content.*;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RecoverySystem;
import android.util.Log;
import android.widget.TextView;

import com.fsl.android.ota.http.Api;
import com.fsl.android.ota.http.AuthenticationParameters;
import com.fsl.android.ota.util.IOUtil;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class OTAServerManager  {
	public interface OTAStateChangeListener {
		
		final int STATE_IN_IDLE = 0;
		final int STATE_IN_CHECKED = 1; // state in checking whether new available.
		final int STATE_IN_DOWNLOADING = 2; // state in download upgrade package
		final int STATE_IN_UPGRADING = 3;  // In upgrade state
		
		final int MESSAGE_DOWNLOAD_PROGRESS = 4;
		final int MESSAGE_VERIFY_PROGRESS = 5;
		final int MESSAGE_STATE_CHANGE = 6;
		final int MESSAGE_ERROR = 7;
		
		// should be raise exception ? but how to do exception in async mode ?
		final int NO_ERROR = 0;
		final int ERROR_WIFI_NOT_AVALIBLE = 1;  // require wifi network, for OTA app.
		final int ERROR_CANNOT_FIND_SERVER = 2;
		final int ERROR_PACKAGE_VERIFY_FALIED = 3;
		final int ERROR_WRITE_FILE_ERROR = 4;
		final int ERROR_NETWORK_ERROR = 5;
		final int ERROR_PACKAGE_INSTALL_FAILED = 6;
		final int ERROR_PACKAGE_SIGN_FAILED = 7;
        final int ERROR_SERIAL_NOT_AVALIBLE = 8;  // require wifi network, for OTA app.

		// results
		final int RESULTS_ALREADY_LATEST = 1;

		public void onStateOrProgress(int message, int error, Object info);
		
	}

	private OTAStateChangeListener mListener;	
	private OTAServerConfig mConfig;
	private BuildPropParser parser = null;

	private Api HTTPSApi;
	private String mServerResponse;

    private String mUpdateURL;
    private String mBuildNumber;
    private String mBuildId;
    private String mBuildDate;
    private String mBuildDescription;
    private String mBuildSize;

	private String clientCertificatePassword;
	private String clientCertificateName;
	private String caCertificateName;
    private String mDeviceSerialNumber = null;
    private Context mContext;
    private String mUpdatePackageLocation = "/cache/update.zip";
    private boolean localtest = false;
    private final Semaphore sema_sync = new Semaphore(0, true);

	long mCacheProgress = -1;
	boolean mStop = false;

	final String TAG = "OTA";

	Handler mSelfHandler;
	WakeLock mWakelock;
	
	public OTAServerManager(Context context) throws MalformedURLException {
		mConfig = new OTAServerConfig(Build.PRODUCT, context);
		PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
		mWakelock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA Wakelock");
		mContext = context;

		clientCertificateName = "my-app-client.p12";
		caCertificateName = "my-root-ca.crt.pem";
		clientCertificatePassword = "1234";

        Log.i(TAG, "Getting Device HW:SerialNo");
        TcpClientThread tcpClientThread = new TcpClientThread();
        tcpClientThread.start();

        try {
            sema_sync.acquire();
        } catch(Exception e) {
            Log.e(TAG, "*** Unable Get Device HW:SerialNo");
            e.printStackTrace();
        }

        Log.d(TAG, "*** OTAServerManager DEVICE: " + Build.PRODUCT + " SerialNo: " + mDeviceSerialNumber);

    }

	public OTAStateChangeListener getmListener() {
		return mListener;
	}

	public String getServerResponse() {
	    return mServerResponse;
    }

    class TcpClientThread extends Thread {
        TcpClientThread() {}

        public void run() {
            try {
                String sernum="cmd::get::";

                LocalSocket socket;
                LocalSocketAddress localsocketaddr;
                InputStream is;
                OutputStream os;
                DataInputStream dis;
                PrintStream ps;
                BufferedReader br;
                Log.i(TAG, "*** HWSER LocalSocket");
                socket = new LocalSocket();
                localsocketaddr = new LocalSocketAddress("serialnumber",LocalSocketAddress.Namespace.RESERVED);
                socket.connect(localsocketaddr);
                is = socket.getInputStream();
                os = socket.getOutputStream();
                dis = new DataInputStream(is);
                ps = new PrintStream(os);

                Log.i(TAG, "*** HWSER getBytes");

                byte[] msg1 = sernum.getBytes();
                ps.write(msg1);
                InputStream in = socket.getInputStream();
                br = new BufferedReader(new InputStreamReader(in));
                if (sernum.endsWith("get::")) {
                    StringBuffer strBuffer = new StringBuffer();
                    char c = (char) br.read();
                    while (c != 0xffff) {
                        strBuffer.append(c);
                        c=(char) br.read();
                    }
                    mDeviceSerialNumber = strBuffer.toString();
                    sema_sync.release();
                }
                Log.i(TAG, "*** HWSER getBytes DONE serial:" + mDeviceSerialNumber);

                dis.close();
                ps.close();
                is.close();
                os.close();
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "*** ENABLE GET HW:SERIAL FROM SOCKET EXCEPTION " + e.getMessage());
                e.printStackTrace();
                sema_sync.release();
            }
        }
    }


    public String getServerResponse(String key) {
	    try {
            JSONObject mainObject = new JSONObject(mServerResponse);
            String item = mainObject.getString(key);
            Log.d(TAG, "*** getServerResponse key: " + key + " val: " + item);
            return item;
        } catch (Exception e) {
	        e.printStackTrace();
        }
        return null;
    }

	public void setmListener(OTAStateChangeListener mListener) {
		this.mListener = mListener;
	}
	
	public boolean checkNetworkOnline() {
		ConnectivityManager conMgr =  (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (conMgr.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET).isConnectedOrConnecting()||
			conMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting()) {
			return true;
		} else {
			return false;
		}
	}

    public String getBuildNumber() {return mBuildNumber;}
    public String getBuildId() {return mBuildId;}
    public String getBuildDate() {return mBuildDate;}
    public String getBuildDescription() {return mBuildDescription;}
    public String getBuildSize() {return mBuildSize;}

	/*
	Checking for a new updates for the device
	 */
	public void startCheckingVersion() {
		Log.v(TAG, "startCheckingVersion was called");
		int res = getUpdateURL(mConfig.getUpdateRequestURL());
		if (res != 0) {
		    if (res == -1) {
                reportCheckingError(OTAStateChangeListener.ERROR_CANNOT_FIND_SERVER);
                Log.v(TAG, "error cannot find server!");
            } else if (res == -2) {
                reportCheckingError(OTAStateChangeListener.ERROR_SERIAL_NOT_AVALIBLE);
                Log.v(TAG, "error serial id not avalible");
            } else if (res == -3) {
                if (this.mListener != null) {
                    if (this.checkNetworkOnline()) {
                        reportCheckingError(OTAStateChangeListener.ERROR_CANNOT_FIND_SERVER);
                        Log.v(TAG, "error cannot find server!");
                    } else {
                        reportCheckingError(OTAStateChangeListener.ERROR_WIFI_NOT_AVALIBLE);
                        Log.v(TAG, "error wifi or ethernet not avalible");
                    }
                }
            }
			return;
		}
        // get build info from response
		mUpdateURL = getServerResponse("url");
        mBuildNumber = getServerResponse("buildNumber");
        mBuildId = getServerResponse("buildDisplayId");
        mBuildDate = getServerResponse("buildDate");
        mBuildDescription = getServerResponse("description");
        mBuildSize = getServerResponse("size");

		Log.d(TAG, "*** Get update url: " + mUpdateURL);
        if (this.mListener != null) {
            this.mListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_CHECKED, OTAStateChangeListener.NO_ERROR, parser);
        }
	}
	
	// return true if needs to upgrade
	public boolean isUpdateURLPresent() {
	    if (mUpdateURL != null) return true;
	    return false;
	}
	
	void publishDownloadProgress(long total, long downloaded) {
		//Log.v(TAG, "download Progress: total: " + total + "download:" + downloaded);
		Long progress = new Long((downloaded*100)/total);
		if (this.mListener != null && progress.longValue() != mCacheProgress) {
			this.mListener.onStateOrProgress(OTAStateChangeListener.MESSAGE_DOWNLOAD_PROGRESS, 0, progress);
			mCacheProgress = progress.longValue();
		}
	}
	
	void reportCheckingError(int error) {
		if (this.mListener != null ) {
			this.mListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_CHECKED, error, null);
	        Log.v(TAG, "---------state in checked----------- ");
        }
    }
	
	void reportDownloadError(int error) {
		if (this.mListener != null) {
            this.mListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_DOWNLOADING, error, null);
        }
	}
	
	void reportInstallError(int error) {
		if (this.mListener != null) {
			this.mListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_UPGRADING, error, null);
            Log.v(TAG, "---------state in upgrading----------- ");
        }
	}
	
	public void onStop() {
		mStop = true;
	}
	
	public void startDownloadUpgradePackage() {
        long total = 0, count;

		Log.v(TAG, "startDownloadUpgradePackage()");
		if (localtest) {
		    try {
                File odir = mContext.getCacheDir();
                File ofil = File.createTempFile("temp", "zip", odir);
                mUpdatePackageLocation = ofil.getPath();
            }catch(Exception e) {
		        e.printStackTrace();
            }
        }
		File targetFile = new File(mUpdatePackageLocation);
		try {
			targetFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
			reportDownloadError(OTAStateChangeListener.ERROR_WRITE_FILE_ERROR);
			return;
		}

		try {
            mWakelock.acquire();
		    if (mUpdateURL != null) {
                AuthenticationParameters authParams = new AuthenticationParameters();
                authParams.setClientCertificateAssertName(clientCertificateName);
                authParams.setClientCertificatePassword(clientCertificatePassword);
                authParams.setCaCertificate(readCaCert());

                HTTPSApi = new Api(mContext, authParams);
                Log.d(TAG, "Connecting to " + mUpdateURL);

                //HTTPSApi.setAccessKey("12345678");
                //HTTPSApi.setSecretKey("LIGHTHOUSE_SECRET_KEY");
                HTTPSApi.setHardwareId(mDeviceSerialNumber);

                InputStream is = HTTPSApi.doGetStream(mUpdateURL);
                int lengthOfFile = 96038693;
                lengthOfFile = HTTPSApi.doGetSize();
                // download the file
                InputStream input = new BufferedInputStream(is);
                OutputStream output = new FileOutputStream(targetFile);

                Log.d(TAG, "file size:" + lengthOfFile);
                byte data[] = new byte[100 * 1024];
                while ((count = input.read(data)) >= 0 && !mStop) {
                    total += count;

                    // publishing the progress....
                    publishDownloadProgress(lengthOfFile, total);
                    output.write(data, 0, (int) count);
                    Log.d(TAG, "readed:" + total);
                }

                output.flush();
                output.close();
                input.close();
                if (this.mListener != null && !mStop) {
                    this.mListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_DOWNLOADING, 0, null);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
		    if (e instanceof SocketTimeoutException) {
                Log.e(TAG, "*** IOException TIMEOUT:" + e.toString());
            } else {
                Log.e(TAG, "*** IOException:" + e.toString());
            }
		} catch (Exception e) {
			e.printStackTrace();
			reportDownloadError(OTAStateChangeListener.ERROR_WRITE_FILE_ERROR);
		} finally {
			mWakelock.release();
			mWakelock.acquire(2);
		}
	}
	
	RecoverySystem.ProgressListener recoveryVerifyListener = new RecoverySystem.ProgressListener() {
		public void onProgress(int progress) {
			Log.d(TAG, "verify progress" + progress);
			if (mListener != null)
				mListener.onStateOrProgress(OTAStateChangeListener.MESSAGE_VERIFY_PROGRESS, 
						0, new Long(progress));
		}
	};
	
	public void startInstallUpgradePackage() {
		File recoveryFile = new File(mUpdatePackageLocation);
		
		// first verify package
         try {
        	 mWakelock.acquire();
        	 RecoverySystem.verifyPackage(recoveryFile, recoveryVerifyListener, null);
         } catch (IOException e1) {
        	 reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_VERIFY_FALIED);
        	 e1.printStackTrace();
        	 return;
         } catch (GeneralSecurityException e1) {
        	 reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_VERIFY_FALIED);
        	 e1.printStackTrace();
        	 return;
         } finally {
        	 mWakelock.release();
         }

         // then install package
         try {
        	 mWakelock.acquire();
			 RecoverySystem.installPackage(mContext, recoveryFile);
         } catch (IOException e) {
      	   // TODO Auto-generated catch block
        	 reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_INSTALL_FAILED);
        	 e.printStackTrace();
        	 return;
         } catch (SecurityException e){
        	 e.printStackTrace();
        	 reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_INSTALL_FAILED);
        	 return;
         } finally {
        	 mWakelock.release();
         }
         // cannot reach here...

	}

	private byte[] readP12Cert() throws Exception {
		/*File externalStorageDir = Environment.getExternalStorageDirectory();
		return new File(externalStorageDir, clientCertificateName);
		*/
		AssetManager assetManager = mContext.getAssets();
		InputStream inputStream = assetManager.open(caCertificateName);
		return IOUtil.readFullyByte(inputStream);
	}

	private String readCaCert() throws Exception {
        /*
        AssetManager assetManager = getAssets();
        InputStream inputStream = assetManager.open(caCertificateName);
        return IOUtil.readFully(inputStream);
        */
		AssetManager assetManager = mContext.getAssets();
		InputStream inputStream = assetManager.open(caCertificateName);
		return IOUtil.readFully(inputStream);
	}


	int getUpdateURL(URL url) {
		try {
			AuthenticationParameters authParams = new AuthenticationParameters();
			authParams.setClientCertificateAssertName(clientCertificateName);
			authParams.setClientCertificatePassword(clientCertificatePassword);
			authParams.setCaCertificate(readCaCert());

			if (mDeviceSerialNumber != null) {
                HTTPSApi = new Api(mContext, authParams);
                Log.d(TAG, "Connecting to " + url.toURI());

                //HTTPSApi.setAccessKey("12345678");
                //HTTPSApi.setSecretKey("LIGHTHOUSE_SECRET_KEY");
                HTTPSApi.setHardwareId(mDeviceSerialNumber);

                mServerResponse = HTTPSApi.doGet(url.toString());
                int responseCode = HTTPSApi.getLastResponseCode();
                if (responseCode == 200) {
                    Log.d(TAG, "HTTP Response Code: 200 Text: " + mServerResponse);
                    return 0;
                } else {
                    if (mServerResponse.contains("Connection refused")) {
                        Log.e(TAG, "*** ERROR: Server reset connection");
                    }
                    Log.e(TAG, "*** ERROR: HTTP Response Code: " + responseCode);
                    return -1;
                }
            } else {
                Log.e(TAG, "*** ERROR: HW:SerialId is Absent");
                return -2;
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
        return -3;
	}

	
	// function: 
	// download the property list from remote site, and parse it to peroerty list.
	// the caller can parser this list and get information.
	boolean getTargetPackagePropertyList(URL configURL) {
		// first try to download the property list file. the build.prop of target image.
		try {
            ByteArrayOutputStream writer = new ByteArrayOutputStream();
			BuildPropParser parser = new BuildPropParser(writer, mContext);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean handleMessage(Message arg0) {
		// TODO Auto-generated method stub
		return false;
	}

}
