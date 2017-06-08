/**
 * 
 */
package com.wuba.minicap;

import java.awt.Image;


/**
 * @author hui.qian qianhui@58.com
 * 
 */
public interface ScreenSubject {
	public void registerObserver(AndroidScreenObserver o);

	public void removeObserver(AndroidScreenObserver o);

	public void notifyObservers(Image image);

}
