package com.wuba.device;

import java.io.File;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;

public class ADB {
	
	private AndroidDebugBridge mAndroidDebugBridge = null;
	private String adbPath = null;
	private String adbPlatformTools = "platform-tools";
	public static boolean hasInitAdb = false;
	
	public ADB(){
		init();
	}
	
	/**
	 * Get the system adb path
	 * @return
	 */
	private String getADBPath(){
		if (adbPath == null){
			adbPath = System.getenv("ANDROID_HOME");
			if(adbPath != null){
				adbPath += File.separator + adbPlatformTools;
			}else {
				return null;
			}
		}
		adbPath += File.separator + "adb";
		return adbPath;
	}

	/**
	 * Initialize adb connection
	 * @return
	 */
	private boolean init() {
		boolean success = false;
		if (!hasInitAdb){
			String adbPath = getADBPath();
			if (adbPath != null) {
				AndroidDebugBridge.init(false);
				mAndroidDebugBridge = AndroidDebugBridge.createBridge(adbPath, true);
				if (mAndroidDebugBridge != null) {
					success = true;
					hasInitAdb = true;
				}
				// Delay processing adb gets device information
				if (success) {
					int loopCount = 0;
					while (mAndroidDebugBridge.hasInitialDeviceList() == false) {
						try {
							Thread.sleep(100);
							loopCount++;
						} catch (InterruptedException e) {
						}
						if (loopCount > 100) {
							success = false;
							break;
						}
					}
				}
			}
		}
		
		return success;
	}
	
	// Gets the list of connected devices
	public IDevice[] getDevices() {
		IDevice[] devicelist = null;
		if (mAndroidDebugBridge != null) {
			devicelist = mAndroidDebugBridge.getDevices();
		}
		return devicelist;
	}
	
	public IDevice getDeviceBySerial(String serialNumber) {
	    IDevice[] devices = mAndroidDebugBridge.getDevices();
	    for (IDevice device : devices) {
            if(device.getSerialNumber().equals(serialNumber)) {
                return device;
            }
        }
	    
	    return null;
	}
}

