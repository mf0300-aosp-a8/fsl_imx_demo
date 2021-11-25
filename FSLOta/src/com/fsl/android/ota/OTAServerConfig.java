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
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

// TODO: get the configure from a configure file.
public class OTAServerConfig {

	private Context mContext;
	private String default_serveraddr = "lighthouse-api.harbortouch.com";
	private String default_protocol = "https";
	private int default_port = 443;
	URL updatePackageURL;
	URL updateRequestURL;
	String product;
	final String TAG = "OTA";
	private String configFile = "/system/etc/ota.conf";
	private String machineFile = "/sys/devices/soc0/machine";
	private String server_ip_config = "server";
	private String port_config_str = "port";
	private String machineString = null;
	private String mServerDomain = null;
	private int mServerPort = 0;

	public OTAServerConfig (String productname, Context context) throws MalformedURLException {
		mContext = context;
		if (loadConfigureFromFile(configFile, productname) == false)
			defaultConfigure(productname);
	}
	void readMachine() {
		File file = new File(machineFile);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			machineString = reader.readLine();
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			// FAKE NAME
			machineString = "imx6dq";
		}
	}

	boolean loadConfigureFromFile (String configFile, String product) {
		try {
			BuildPropParser parser = new BuildPropParser(new File(configFile), mContext);
			mServerDomain = parser.getProp(server_ip_config);
			String port_str = parser.getProp(port_config_str);
			Log.i(TAG, "Read server name from file:" + mServerDomain);
			Log.i(TAG, "Read server port from file:" + port_str);

			if (port_str == null) {
				port_str = Integer.toString(default_port);
			}

			if (mServerDomain == null) {
				mServerDomain = default_serveraddr;
			}
			mServerPort = new Long(port_str).intValue();
			if (mServerPort == 0) {
				mServerPort = default_port;
			}
			Log.i(TAG, "Result server name:" + mServerDomain);
			Log.i(TAG, "Result server port:" + mServerPort);

			readMachine();
			String version = SystemProperties.get("ro.build.version.release");

			updateRequestURL = new URL(default_protocol, mServerDomain, mServerPort, mContext.getString(R.string.ota_server_url));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	void defaultConfigure(String productname) throws MalformedURLException
	{
		product = productname;
		String buildconfigAddr = new String(product + "/api/v1/update");
		if (mServerDomain != null) {
			updateRequestURL = new URL(default_protocol, mServerDomain, default_port, buildconfigAddr);
		} else {
			updateRequestURL = new URL(default_protocol, default_serveraddr, default_port, buildconfigAddr);
		}
		Log.i(TAG, "Default update check URL is :" + updateRequestURL.toString());
	}
	
	public URL getPackageURL () { return updatePackageURL; }
	public URL getUpdateRequestURL() { return updateRequestURL; }
	
}
