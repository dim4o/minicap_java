/**
 * 
 */
package com.wuba.minicap;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceUnixSocketNamespace;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.wuba.utils.Constant;
import com.wuba.utils.TimeUtil;

/**
 * @author hui.qian qianhui@58.com
 * 
 */
public class MiniCapUtil implements ScreenSubject {
	private static final Logger LOG = Logger.getLogger(MiniCapUtil.class);
	// The type of CPU architecture
	public static final String ABIS_ARM64_V8A = "arm64-v8a";
	public static final String ABIS_ARMEABI_V7A = "armeabi-v7a";
	public static final String ABIS_X86 = "x86";
	public static final String ABIS_X86_64 = "x86_64";

	private Queue<byte[]> dataQueue = new LinkedBlockingQueue<byte[]>();
	private List<AndroidScreenObserver> observers = new ArrayList<AndroidScreenObserver>();

	private Banner banner = new Banner();
	private static final int PORT = 1717;
	private Socket socket;
	private IDevice device;
	private String REMOTE_PATH = "/data/local/tmp";
	private String ABI_COMMAND = "ro.product.cpu.abi";
	private String SDK_COMMAND = "ro.build.version.sdk";
	private String MINICAP_BIN = "minicap";
	private String MINICAP_SO = "minicap.so";
	private String MINICAP_CHMOD_COMMAND = "chmod 777 %s/%s";
	private String MINICAP_WM_SIZE_COMMAND = "wm size";
	private String MINICAP_START_COMMAND = "LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P %s@%s/0";
	private String MINICAP_TAKESCREENSHOT_COMMAND = "LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P %s@%s/0 -s >%s";
	private String ADB_PULL_COMMAND = "adb -s %s pull %s %s";
	private boolean isRunning = false;
	private String size;
	
	private static long testSize = 0;
	
	private static double max = Integer.MIN_VALUE;
	
	public MiniCapUtil(IDevice device) {
		this.device = device;
		init();
	}
	
	// To determine whether to support minicap
	public boolean isSupoort(){
		String supportCommand = String.format("LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P %s@%s/0 -t", size,size);
		String output = executeShellCommand(supportCommand);
		if(output.trim().endsWith("OK")){
			return true;
		}
		return false;
	}
	
	/**
	 * Place the minicap binary and .so files into the / data / local / tmp folder, start the minicap service
	 */
	private void init() {

		String abi = device.getProperty(ABI_COMMAND);
		String sdk = device.getProperty(SDK_COMMAND);
		File minicapBinFile = new File(Constant.getMinicapBin(), abi + "/" + MINICAP_BIN);
		File minicapSoFile = new File(Constant.getMinicapSo(), "android-" + sdk + "/" + abi + "/" + MINICAP_SO);
		try {
			// Push the minicap executable and the .so file to the device
			device.pushFile(minicapBinFile.getAbsolutePath(), REMOTE_PATH + "/" + MINICAP_BIN);
			device.pushFile(minicapSoFile.getAbsolutePath(), REMOTE_PATH + "/" + MINICAP_SO);
			executeShellCommand(String.format(MINICAP_CHMOD_COMMAND, REMOTE_PATH, MINICAP_BIN));
			// Port forwarding
			device.createForward(PORT, "minicap", DeviceUnixSocketNamespace.ABSTRACT);

			// Get the size of the device screen
			String output = executeShellCommand(MINICAP_WM_SIZE_COMMAND);
			size = output.split(":")[1].trim(); // 600x800
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		
		// Service for benchmark
		ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);
	    ses.scheduleAtFixedRate(new Runnable() {
	        @Override
	        public void run() {
	            if(testSize > max) {
	               max = testSize; 
	            } 

	            testSize = 0;
	            
	            System.out.println();
	            System.out.println("Max: " + max);
	            System.out.println();
	        }
	    }, 0, 1, TimeUnit.SECONDS); 
	}
	
	public void takeScreenShotOnce() {
		String savePath = "/data/local/tmp/screenshot.jpg";
		String takeScreenShotCommand = String.format(MINICAP_TAKESCREENSHOT_COMMAND, size, size, savePath);
		String localPath = System.getProperty("user.dir") + "/screenshot.jpg";
		String pullCommand = String.format(ADB_PULL_COMMAND,
				device.getSerialNumber(), savePath, localPath);
		try {
			// Process process =
			// Runtime.getRuntime().exec(takeScreenShotCommand);
			executeShellCommand(takeScreenShotCommand);
			device.pullFile(savePath, localPath);
			// BufferedReader br = new BufferedReader(new InputStreamReader(
			// process.getInputStream()));
			// String line = null;
			// while ((line = br.readLine()) != null) {
			// LOG.debug(line);
			// }
			// Thread.sleep(200);
			// process.waitFor();
			Runtime.getRuntime().exec(pullCommand);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SyncException e) {
			e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}

	}

	@SuppressWarnings("unused")
    private static String bytesToHexString(byte[] src) {
		StringBuilder stringBuilder = new StringBuilder("");
		if (src == null || src.length <= 0) {
			return null;
		}
		for (int i = 0; i < src.length; i++) {
			int v = src[i] & 0xFF;
			String hv = Integer.toHexString(v);
			if (hv.length() < 2) {
				stringBuilder.append(0);
			}
			stringBuilder.append(hv + " ");
		}
		return stringBuilder.toString();
	}

	
    @SuppressWarnings("deprecation")
    private String executeShellCommand(String command) {
		CollectingOutputReceiver output = new CollectingOutputReceiver();
		try {
			device.executeShellCommand(command, output, 0);
		} catch (TimeoutException e) {
			e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
		} catch (ShellCommandUnresponsiveException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return output.getOutput();
	}

	public void startScreenListener() {
		isRunning = true;
		Thread frame = new Thread(new ImageBinaryFrameCollector());
		frame.start();
		Thread convert = new Thread(new ImageConverter());
		convert.start();
	}

	public void stopScreenListener() {
		isRunning = false;
	}

	private BufferedImage createImageFromByte(byte[] binaryData) {
		BufferedImage bufferedImage = null;
		InputStream in = new ByteArrayInputStream(binaryData);
		try {
			bufferedImage = ImageIO.read(in);
			if (bufferedImage == null) {
				LOG.debug("Buffered image is empty");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return bufferedImage;
	}

	@SuppressWarnings("unused")
    private Image createImage(byte[] data) {
		Image image = Toolkit.getDefaultToolkit().createImage(data);
		LOG.info("Create success");
		return image;
	}

	// Java merge two byte arrays
	private static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
		byte[] byte_3 = new byte[byte_1.length + byte_2.length];
		System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
		System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
		return byte_3;
	}

	private byte[] subByteArray(byte[] byte1, int start, int end) {
		byte[] byte2 = new byte[0];
		try {
			byte2 = new byte[end - start];
		} catch (NegativeArraySizeException e) {
			e.printStackTrace();
		}
		System.arraycopy(byte1, start, byte2, 0, end - start);
		return byte2;
	}
	
	class ImageBinaryFrameCollector implements Runnable {
		private InputStream stream = null;

		// private DataInputStream input = null;

		public void run() {
			LOG.debug("Picture Binary Data Collector is already on");
			try {

				final String startCommand = String.format(MINICAP_START_COMMAND, size, size);
				// Start the minicap service
				new Thread(new Runnable() {
					public void run() {
						LOG.info("The minicap server starts : " + startCommand);
						executeShellCommand(startCommand);
					}
				}).start();
				try {
					Thread.sleep(1 * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				socket = new Socket("localhost", PORT);
				stream = socket.getInputStream();
				// input = new DataInputStream(stream);
				final int len = 4096;
				while (isRunning) {
					byte[] buffer;
					buffer = new byte[len];

					int realLen = stream.read(buffer);
					// Benchmark computation
                    testSize += realLen;
                    
					if (buffer.length != realLen) {
						buffer = subByteArray(buffer, 0, realLen);
					}
					dataQueue.add(buffer);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (socket != null && socket.isConnected()) {
					try {
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (stream != null) {
					try {
						stream.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			LOG.debug("Picture Binary Data Collector is off");
		}

	}

	class ImageConverter implements Runnable {
		private int readBannerBytes = 0;
		private int bannerLength = 2;
		private int readFrameBytes = 0;
		private int frameBodyLength = 0;
		private byte[] frameBody = new byte[0];

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		public void run() {
			// TODO Auto-generated method stub
			long start = System.currentTimeMillis();
			while (isRunning) {
				if (dataQueue.isEmpty()) {
					// LOG.info("The data queue is empty");
					continue;
				}
				byte[] buffer = dataQueue.poll();
				int len = buffer.length;
				for (int cursor = 0; cursor < len;) {
					int byte10 = buffer[cursor] & 0xff;
					if (readBannerBytes < bannerLength) {
						cursor = parserBanner(cursor, byte10);
					} else if (readFrameBytes < 4) {
						// The first four bits of the buffer are the size of the buffer for the frame
						frameBodyLength += (byte10 << (readFrameBytes * 8)) >>> 0;
						cursor += 1;
						readFrameBytes += 1;
						// LOG.debug("Resolve image size = " + readFrameBytes);
					} else {
						if (len - cursor >= frameBodyLength) {
							LOG.debug("frameBodyLength = " + frameBodyLength);
							byte[] subByte = subByteArray(buffer, cursor,
									cursor + frameBodyLength);
							frameBody = byteMerger(frameBody, subByte);
							if ((frameBody[0] != -1) || frameBody[1] != -40) {
								LOG.error(String
										.format("Frame body does not start with JPG header"));
								return;
							}
							final byte[] finalBytes = subByteArray(frameBody,
									0, frameBody.length);
							// Convert to bufferedImage
							new Thread(new Runnable() {

								@Override
								public void run() {
									// TODO Auto-generated method stub
									Image image = createImageFromByte(finalBytes);
									notifyObservers(image);
								}
							}).start();

							long current = System.currentTimeMillis();
							LOG.info("Image has been generated and time consuming: "
									+ TimeUtil.formatElapsedTime(current
											- start));
							start = current;
							cursor += frameBodyLength;
							restore();
						} else {
							LOG.debug("The required data size : " + frameBodyLength);
							byte[] subByte = subByteArray(buffer, cursor, len);
							frameBody = byteMerger(frameBody, subByte);
							frameBodyLength -= (len - cursor);
							readFrameBytes += (len - cursor);
							cursor = len;
						}
					}
				}
			}

		}

		private void restore() {
			frameBodyLength = 0;
			readFrameBytes = 0;
			frameBody = new byte[0];
		}

		private int parserBanner(int cursor, int byte10) {
			switch (readBannerBytes) {
			case 0:
				// version
				banner.setVersion(byte10);
				break;
			case 1:
				// length
				bannerLength = byte10;
				banner.setLength(byte10);
				break;
			case 2:
			case 3:
			case 4:
			case 5:
				// pid
				int pid = banner.getPid();
				pid += (byte10 << ((readBannerBytes - 2) * 8)) >>> 0;
				banner.setPid(pid);
				break;
			case 6:
			case 7:
			case 8:
			case 9:
				// real width
				int realWidth = banner.getReadWidth();
				realWidth += (byte10 << ((readBannerBytes - 6) * 8)) >>> 0;
				banner.setReadWidth(realWidth);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
				// real height
				int realHeight = banner.getReadHeight();
				realHeight += (byte10 << ((readBannerBytes - 10) * 8)) >>> 0;
				banner.setReadHeight(realHeight);
				break;
			case 14:
			case 15:
			case 16:
			case 17:
				// virtual width
				int virtualWidth = banner.getVirtualWidth();
				virtualWidth += (byte10 << ((readBannerBytes - 14) * 8)) >>> 0;
				banner.setVirtualWidth(virtualWidth);

				break;
			case 18:
			case 19:
			case 20:
			case 21:
				// virtual height
				int virtualHeight = banner.getVirtualHeight();
				virtualHeight += (byte10 << ((readBannerBytes - 18) * 8)) >>> 0;
				banner.setVirtualHeight(virtualHeight);
				break;
			case 22:
				// orientation
				banner.setOrientation(byte10 * 90);
				break;
			case 23:
				// quirks
				banner.setQuirks(byte10);
				break;
			}

			cursor += 1;
			readBannerBytes += 1;

			if (readBannerBytes == bannerLength) {
				LOG.debug(banner.toString());
			}
			return cursor;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.wuba.utils.screenshot.ScreenSubject#registerObserver(com.wuba.utils
	 * .screenshot.AndroidScreenObserver)
	 */
	public void registerObserver(AndroidScreenObserver o) {
		// TODO Auto-generated method stub
		observers.add(o);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.wuba.utils.screenshot.ScreenSubject#removeObserver(com.wuba.utils
	 * .screenshot.AndroidScreenObserver)
	 */
	public void removeObserver(AndroidScreenObserver o) {
		// TODO Auto-generated method stub
		int index = observers.indexOf(o);
		if (index != -1) {
			observers.remove(o);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.wuba.minicap.ScreenSubject#notifyObservers(java.awt.Image)
	 */
	@Override
	public void notifyObservers(Image image) {
		for (AndroidScreenObserver observer : observers) {
			observer.frameImageChange(image);
		}
		// TODO Auto-generated method stub

	}
}
