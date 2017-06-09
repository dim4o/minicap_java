/**
 * 
 */
package com.wuba.utils;

import java.io.File;

/**
 * @author hui.qian qianhui@58.com
 * 
 */
public class Constant {
	private static final String ROOT = System.getProperty("user.dir");

	public static File getMinicap() {
		return new File(ROOT, "minicap");
	}

	public static File getMinicapBin() {
		return new File(ROOT, "minicap/bin");
	}

	public static File getMinicapSo() {
		return new File(ROOT, "minicap/shared");
	}

}
