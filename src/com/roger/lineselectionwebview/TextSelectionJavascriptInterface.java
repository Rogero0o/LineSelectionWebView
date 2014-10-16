package com.roger.lineselectionwebview;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

/**
 * This javascript interface allows the page to communicate that text has been selected by the user.
 *
 * @author btate
 *
 */
public class TextSelectionJavascriptInterface {
    
	/** The TAG for logging. */
	private static final String TAG = "TextSelectionJavascriptInterface";
	
	/** The javascript interface name for adding to web view. */
	private final String interfaceName = "TextSelection";
	
	/** The webview to work with. */
	private TextSelectionJavascriptInterfaceListener mListener;
	
	/** The context. */
	Context mContext;
    
    // Need handler for callbacks to the UI thread
    final Handler mHandler = new Handler();
	
	
	/**
	 * Constructor accepting context.
	 * @param c
	 */
	public TextSelectionJavascriptInterface(Context c){
		this.mContext = c;
	}
	
	/**
	 * Constructor accepting context and mListener.
	 * @param c
	 * @param mListener
	 */
	public TextSelectionJavascriptInterface(Context c, TextSelectionJavascriptInterfaceListener mListener){
		this.mContext = c;
		this.mListener = mListener;
	}
	
	/**
	 * Handles javascript errors.
	 * @param error
	 */
    @JavascriptInterface
	public void jsError(final String error){
		if(this.mListener != null){
            
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.tsjiJSError(error);
                }
            });
		}
	}
	
	/**
	 * Gets the interface name
	 * @return
	 */
    @JavascriptInterface
	public String getInterfaceName(){
		return this.interfaceName;
	}
	
	/**
	 * Put the app in "selection mode".
	 */
    @JavascriptInterface
	public void startSelectionMode(){
		
		if(this.mListener != null)
            
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.tsjiStartSelectionMode();
                }
            });
	}
	
	/**
	 * Take the app out of "selection mode".
	 */
    @JavascriptInterface
	public void endSelectionMode(){
		
		if(this.mListener != null)
            
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.tsjiEndSelectionMode();
                }
            });
	}
    
	/**
	 * Show the context menu
	 * @param range
	 * @param text
	 * @param menuBounds
	 */
    @JavascriptInterface
	public void selectionChanged(final String range, final String text, final String handleBounds, final String menuBounds){
		if(this.mListener != null)  {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.tsjiSelectionChanged(range, text, handleBounds, menuBounds);
                }
            });
        }
	}
    
    @JavascriptInterface
	public void setContentWidth(final float contentWidth){
		if(this.mListener != null)
            
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.tsjiSetContentWidth(contentWidth);
                }
            });
	}
    
    //��ʾ��� TODO ��ͳһ��д����ʵ��  add-by zz
    @JavascriptInterface
    public void showText(String result){
    	Toast.makeText(mContext, result, Toast.LENGTH_LONG).show();
    }
    
  //��ʾ��� TODO ��ͳһ��д����ʵ��  add-by zz
    @JavascriptInterface
    public void selectionClientRects(String result,String selectContext){
    	if(this.mListener != null)  {
    		mListener.tsjiSelectionClientRects(result,selectContext);
    	}
    }
}
