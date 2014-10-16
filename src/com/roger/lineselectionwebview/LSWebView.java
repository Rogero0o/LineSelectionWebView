/*
 * Copyright (C) 2012 Brandon Tate
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

package com.roger.lineselectionwebview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Toast;

import com.roger.lineselectionwebview.drag.DragController;
import com.roger.lineselectionwebview.drag.DragLayer;
import com.roger.lineselectionwebview.drag.DragListener;
import com.roger.lineselectionwebview.drag.DragSource;
import com.roger.lineselectionwebview.drag.MyAbsoluteLayout;
import com.roger.lineselectionwebview.util.ActionItem;
import com.roger.lineselectionwebview.util.QuickAction;
import com.roger.lineselectionwebview.util.QuickAction.OnDismissListener;

/**
 * Webview subclass that hijacks web content selection.
 * 
 * @author Brandon Tate
 */
public class LSWebView extends WebView implements TextSelectionJavascriptInterfaceListener, OnTouchListener, OnLongClickListener, OnDismissListener, DragListener {

	/** Context. */
	protected Context mContext;

	/** The context menu. */
	protected QuickAction mContextMenu;

	/** The drag layer for selection. */
	protected DragLayer mSelectionDragLayer;

	/** The drag controller for selection. */
	protected DragController mDragController;

	/** The selection bounds. */
	protected Rect mSelectionBounds = null;

	/** The previously selected region. */
	protected Region mLastSelectedRegion = null;

	/** The selected range. */
	protected String mSelectedRange = "";

	/** The selected text. */
	protected String mSelectedText = "";

	/** Javascript interface for catching text selection. */
	protected TextSelectionJavascriptInterface mTextSelectionJSInterface = null;

	/** Selection mode flag. */
	protected boolean mInSelectionMode = false;

	/** Flag for dragging. */
	protected boolean mDragging = false;

	/** Flag to stop from showing context menu twice. */
	protected boolean mContextMenuVisible = false;

	/** The current content width. */
	protected int mContentWidth = 0;

	/** The current scale of the web view. */
	protected float mCurrentScale = 1.0f;

	// *****************************************************
	// *
	// * Selection Handles
	// *
	// *****************************************************

	/** The start selection handle. */
	protected ImageView mStartSelectionHandle;

	/** the end selection handle. */
	protected ImageView mEndSelectionHandle;

	/** Identifier for the selection start handle. */
	protected final int SELECTION_START_HANDLE = 0;

	/** Identifier for the selection end handle. */
	protected final int SELECTION_END_HANDLE = 1;

	/** Last touched selection handle. */
	protected int mLastTouchedSelectionHandle = -1;

	private List<Rect> rectList;

	/**
	 * 选中的文本
	 */
	private String selectContext;

	/**
	 * 画线的view
	 */
	private LineView lineView;
	/**
	 * 遮罩层
	 */
	private MyAbsoluteLayout myLayout;

	/**
	 * WebView的滚动距离
	 */
	private int scrollY;

	/**
	 * 选中文本和坐标的分隔符
	 */
	public static final String SELECT_SPLIT = "~d@@g~";

	/**
	 * 画线模式
	 */
	private boolean isLineMode = false;;

	public LSWebView(Context context) {
		super(context);

		mContext = context;
		setup(context);
	}

	public LSWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		mContext = context;
		setup(context);

	}

	public LSWebView(Context context, AttributeSet attrs) {
		super(context, attrs);

		mContext = context;
		setup(context);

	}

	// *****************************************************
	// *
	// * Touch Listeners
	// *
	// *****************************************************

	private boolean mScrolling = false;
	private float mLastTouchY = 0;
	private float mLastTouchX = 0;

	@SuppressWarnings("deprecation")
	@Override
	public boolean onTouch(View v, MotionEvent event) {

		if (!isLineMode) {
			return false;
		}

		@SuppressWarnings("deprecation")
		float xPoint = getDensityIndependentValue(event.getX(), mContext) / getDensityIndependentValue(getScale(), mContext);
		float yPoint = getDensityIndependentValue(event.getY(), mContext) / getDensityIndependentValue(getScale(), mContext);

		if (event.getAction() == MotionEvent.ACTION_DOWN) {

			String startTouchUrl = String.format(Locale.US, "javascript:android.selection.startTouch(%f, %f);", xPoint, yPoint);

			mLastTouchX = xPoint;
			mLastTouchY = yPoint;
			loadUrl(startTouchUrl);

			// Tell the javascript to handle this if not in selection mode
			if (!isInSelectionMode()) {
				loadUrl("javascript:android.selection.longTouch();");
				mScrolling = true;
			}

			// Flag scrolling for first touch
			// mScrolling = !isInSelectionMode();

		} else if (event.getAction() == MotionEvent.ACTION_UP) {
			// Check for scrolling flag
			if (!mScrolling) {
				mScrolling = false;
				endSelectionMode();
				return false;
			}

			mScrolling = false;

			// Fixes 4.4 double selection
			return true;

		} else if (event.getAction() == MotionEvent.ACTION_MOVE) {

			mLastTouchX = xPoint;
			mLastTouchY = yPoint;

		}
		// If this is in selection mode, then nothing else should handle this
		// touch
		return false;
	}

	@Override
	public boolean onLongClick(View v) {

		if (!isLineMode) {
			return false;
		}

		// Don't let the webview handle it
		return true;
	}

	// *****************************************************
	// *
	// * Setup
	// *
	// *****************************************************

	/**
	 * Setups up the web view.
	 * 
	 * @param context
	 */
	@SuppressLint("SetJavaScriptEnabled")
	protected void setup(Context context) {

		// On Touch Listener
		setOnLongClickListener(this);
		setOnTouchListener(this);

		// Webview setup
		getSettings().setJavaScriptEnabled(true);
		getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
		getSettings().setPluginState(WebSettings.PluginState.ON);
		// getSettings().setBuiltInZoomControls(true);

		// Webview client.
		setWebViewClient(new WebViewClient() {
			// This is how it is supposed to work, so I'll leave it in, but this
			// doesn't get called on pinch
			// So for now I have to use deprecated getScale method.
			@Override
			public void onScaleChanged(WebView view, float oldScale, float newScale) {
				super.onScaleChanged(view, oldScale, newScale);
				mCurrentScale = newScale;
			}
		});

		// Zoom out fully
		// getSettings().setLoadWithOverviewMode(true);
		// getSettings().setUseWideViewPort(true);

		// Javascript interfaces
		mTextSelectionJSInterface = new TextSelectionJavascriptInterface(context, this);
		addJavascriptInterface(mTextSelectionJSInterface, mTextSelectionJSInterface.getInterfaceName());

		// Create the selection handles
		createSelectionLayer(context);

		// Set to the empty region
		Region region = new Region();
		region.setEmpty();
		mLastSelectedRegion = region;

		// Load up the android asset file
		// String filePath = "file:///android_asset/content.html";

		// Load the url
		// this.loadUrl(filePath);

	}

	// *****************************************************
	// *
	// * Selection Layer Handling
	// *
	// *****************************************************

	/**
	 * Creates the selection layer.
	 * 
	 * @param context
	 */
	protected void createSelectionLayer(Context context) {

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mSelectionDragLayer = (DragLayer) inflater.inflate(R.layout.selection_drag_layer, null);

		// Make sure it's filling parent
		mDragController = new DragController(context);
		mDragController.setDragListener(this);
		mDragController.addDropTarget(mSelectionDragLayer);
		mSelectionDragLayer.setDragController(mDragController);

		mStartSelectionHandle = (ImageView) mSelectionDragLayer.findViewById(R.id.startHandle);
		mStartSelectionHandle.setTag(new Integer(SELECTION_START_HANDLE));
		mEndSelectionHandle = (ImageView) mSelectionDragLayer.findViewById(R.id.endHandle);
		mEndSelectionHandle.setTag(new Integer(SELECTION_END_HANDLE));

		OnTouchListener handleTouchListener = new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {

				boolean handledHere = false;

				final int action = event.getAction();

				// Down event starts drag for handle.
				if (action == MotionEvent.ACTION_DOWN) {
					handledHere = startDrag(v);
					mLastTouchedSelectionHandle = (Integer) v.getTag();
				}

				return handledHere;

			}

		};

		mStartSelectionHandle.setOnTouchListener(handleTouchListener);
		mEndSelectionHandle.setOnTouchListener(handleTouchListener);

	}

	/**
	 * Starts selection mode on the UI thread
	 */
	private Handler startSelectionModeHandler = new Handler() {

		public void handleMessage(Message m) {

			if (mSelectionBounds == null)
				return;

			addView(mSelectionDragLayer);

			drawSelectionHandles();

			int contentHeight = (int) Math.ceil(getDensityDependentValue(getContentHeight(), mContext));

			// Update Layout Params
			ViewGroup.LayoutParams layerParams = mSelectionDragLayer.getLayoutParams();
			layerParams.height = contentHeight;
			layerParams.width = mContentWidth;
			mSelectionDragLayer.setLayoutParams(layerParams);

		}

	};

	/**
	 * Starts selection mode.
	 * 
	 */
	public void startSelectionMode() {

		startSelectionModeHandler.sendEmptyMessage(0);

	}

	// Ends selection mode on the UI thread
	private Handler endSelectionModeHandler = new Handler() {
		public void handleMessage(Message m) {

			if (getParent() != null && mContextMenu != null && mContextMenuVisible) {
				// This will throw an error if the webview is being redrawn.
				// No error handling needed, just need to stop the crash.
				try {
					mContextMenu.dismiss();
				} catch (Exception e) {

				}
			}
			mSelectionBounds = null;
			mLastTouchedSelectionHandle = -1;
			loadUrl("javascript: android.selection.clearSelection();");
			removeView(mSelectionDragLayer);

		}
	};

	/**
	 * Ends selection mode.
	 */
	public void endSelectionMode() {

		endSelectionModeHandler.sendEmptyMessage(0);

	}

	/**
	 * Calls the handler for drawing the selection handles.
	 */
	private void drawSelectionHandles() {
		drawSelectionHandlesHandler.sendEmptyMessage(0);
	}

	/**
	 * Handler for drawing the selection handles on the UI thread.
	 */
	private Handler drawSelectionHandlesHandler = new Handler() {
		public void handleMessage(Message m) {

			MyAbsoluteLayout.LayoutParams startParams = (MyAbsoluteLayout.LayoutParams) mStartSelectionHandle.getLayoutParams();
			startParams.x = (int) (mSelectionBounds.left - mStartSelectionHandle.getDrawable().getIntrinsicWidth());
			startParams.y = (int) (mSelectionBounds.top);

			// Stay on screen.
			startParams.x = (startParams.x < 0) ? 0 : startParams.x;
			startParams.y = (startParams.y < 0) ? 0 : startParams.y;

			mStartSelectionHandle.setLayoutParams(startParams);

			MyAbsoluteLayout.LayoutParams endParams = (MyAbsoluteLayout.LayoutParams) mEndSelectionHandle.getLayoutParams();
			endParams.x = (int) mSelectionBounds.right;
			endParams.y = (int) mSelectionBounds.bottom;

			// Stay on screen
			endParams.x = (endParams.x < 0) ? 0 : endParams.x;
			endParams.y = (endParams.y < 0) ? 0 : endParams.y;

			mEndSelectionHandle.setLayoutParams(endParams);

		}
	};

	/**
	 * Checks to see if this view is in selection mode.
	 * 
	 * @return
	 */
	public boolean isInSelectionMode() {

		return mSelectionDragLayer.getParent() != null;

	}

	/**
	 * Checks to see if the view is currently dragging.
	 * 
	 * @return
	 */
	public boolean isDragging() {
		return mDragging;
	}

	// *****************************************************
	// *
	// * DragListener Methods
	// *
	// *****************************************************

	/**
	 * Start dragging a view.
	 * 
	 */
	private boolean startDrag(View v) {
		// Let the DragController initiate a drag-drop sequence.
		// I use the dragInfo to pass along the object being dragged.
		// I'm not sure how the Launcher designers do this.

		mDragging = true;
		Object dragInfo = v;
		mDragController.startDrag(v, mSelectionDragLayer, dragInfo, DragController.DRAG_ACTION_MOVE);
		return true;
	}

	@Override
	public void onDragStart(DragSource source, Object info, int dragAction) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onDrag() {
		// TODO Auto-generated method stub
	}

	@Override
	public void onDragEnd() {
		// TODO Auto-generated method stub

		MyAbsoluteLayout.LayoutParams startHandleParams = (MyAbsoluteLayout.LayoutParams) mStartSelectionHandle.getLayoutParams();
		MyAbsoluteLayout.LayoutParams endHandleParams = (MyAbsoluteLayout.LayoutParams) mEndSelectionHandle.getLayoutParams();

		float scale = getDensityIndependentValue(getScale(), mContext);

		float startX = startHandleParams.x - getScrollX();
		float startY = startHandleParams.y - getScrollY();
		float endX = endHandleParams.x - getScrollX();
		float endY = endHandleParams.y - getScrollY();

		startX = getDensityIndependentValue(startX, mContext) / scale;
		startY = getDensityIndependentValue(startY, mContext) / scale;
		endX = getDensityIndependentValue(endX, mContext) / scale;
		endY = getDensityIndependentValue(endY, mContext) / scale;

		if (startY <= 15) {
			startY = 15;
		}

		if (endY <= 15) {
			endY = 15;
		}

		System.out.println("onDragEnd startY = " + startY + ", endY = " + endY);

		if (mLastTouchedSelectionHandle == SELECTION_START_HANDLE && startX > 0 && startY > 0) {
			String saveStartString = String.format(Locale.US, "javascript: android.selection.setStartPos(%f, %f);", startX, startY);
			loadUrl(saveStartString);
		}
		if (endY <= 0) {
			endY = 10;
		}
		if (mLastTouchedSelectionHandle == SELECTION_END_HANDLE && endX > 0 && endY > 0) {
			String saveEndString = String.format(Locale.US, "javascript: android.selection.setEndPos(%f, %f);", endX, endY);
			loadUrl(saveEndString);
		}

		mDragging = false;

	}

	// *****************************************************
	// *
	// * Context Menu Creation
	// *
	// *****************************************************

	/**
	 * Shows the context menu using the given region as an anchor point.
	 * 
	 * @param displayRect
	 */
	protected void showContextMenu(Rect displayRect) {

		// Don't show this twice
		if (mContextMenuVisible) {
			return;
		}

		// Don't use empty rect
		// if(displayRect.isEmpty()){
		if (displayRect.right <= displayRect.left) {
			return;
		}

		// Copy action item
		ActionItem buttonOne = new ActionItem();

		buttonOne.setTitle("Selected");
		buttonOne.setActionId(1);

		// Highlight action item
		// ActionItem buttonTwo = new ActionItem();
		//
		// buttonTwo.setTitle("取消条件");
		// buttonTwo.setActionId(2);
		// buttonTwo.setIcon(getResources().getDrawable(R.drawable.menu_info));

		// ActionItem buttonThree = new ActionItem();

		// buttonThree.setTitle("Button 3");
		// buttonThree.setActionId(3);
		// buttonThree.setIcon(getResources().getDrawable(R.drawable.menu_eraser));

		// The action menu
		mContextMenu = new QuickAction(getContext());
		mContextMenu.setOnDismissListener(this);

		// Add buttons
		mContextMenu.addActionItem(buttonOne);

		// mContextMenu.addActionItem(buttonTwo);

		// mContextMenu.addActionItem(buttonThree);

		// setup the action item click listener
		mContextMenu.setOnActionItemClickListener(new QuickAction.OnActionItemClickListener() {

			@Override
			public void onItemClick(QuickAction source, int pos, int actionId) {
				// TODO Auto-generated method stub
				if (actionId == 1) {

					// Do Button 1 stuff
					// mdf by zz
					drawLayout();
					endSelectionMode();
					if (onTextSelectListener != null) {
						onTextSelectListener.select(selectContext.split(SELECT_SPLIT)[0]);
					}
				}

				mContextMenuVisible = false;
			}
		});

		mContextMenuVisible = true;
		mContextMenu.show(this, displayRect);
	}

	/**
	 * 在webview上绘制蒙版
	 */
	private void drawLayout() {

		// key值=选中内容加开始坐标和结束坐标
		if (rectList.size() > 1) {
			Rect rectStart = rectList.get(0);
			Rect rectEnd = rectList.get(rectList.size() - 1);
			selectContext = selectContext + SELECT_SPLIT + rectStart.left + rectEnd.right;
		} else {
			Rect rect = rectList.get(0);
			selectContext = selectContext + SELECT_SPLIT + rect.left + rect.right;
		}

		if (scrollY > 0) {
			for (Rect rect : rectList) {
				rect.bottom = rect.bottom + scrollY;
			}
		}

		if (!lineView.exist(selectContext)) {
			// 画线
			drawLine();
			// 增加删除的图片
			drawImage();
		}
	}

	/**
	 * 画线后在末尾增加删除的图标
	 */
	private void drawImage() {
		int size = rectList.size();
		Rect endRect = rectList.get(size - 1);
		Rect realEndRect = endRect;

		// 最后一个矩形的left坐标与上一个一样且同一行时,会扩大画线区域,因此选择两个节点中宽度较小的作为最终矩形
		if (size > 2) {
			Rect endPreRect = rectList.get(size - 2);
			if (endRect.left == endPreRect.left) {
				if (endRect.width() > endPreRect.width()) {
					realEndRect = endPreRect;
				} else {
					realEndRect = endRect;
				}
			}
		}

		Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.del);
		ImageView imageView = new ImageView(mContext);
		imageView.setImageBitmap(bitmap);
		imageView.setScaleType(ScaleType.CENTER);
		imageView.setTag(selectContext);
		MyAbsoluteLayout.LayoutParams lp = new MyAbsoluteLayout.LayoutParams(MyAbsoluteLayout.LayoutParams.WRAP_CONTENT, MyAbsoluteLayout.LayoutParams.WRAP_CONTENT,
				realEndRect.right, realEndRect.bottom);
		imageView.setLayoutParams(lp);

		imageView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String tag = (String) v.getTag();
				lineView.remove(tag);
				myLayout.removeView(v);
				if (onTextSelectListener != null) {
					onTextSelectListener.cancle(tag.split(SELECT_SPLIT)[0]);
				}
			}

		});
		myLayout.addView(imageView, lp);
	}

	/**
	 * 画线
	 */
	private void drawLine() {
		lineView.add(selectContext, rectList);
	}

	/**
	 * 初始化蒙版
	 * 
	 * @param container webView所在的父容器
	 */
	public void openLineMode(ViewGroup container) {

		this.myLayout = (MyAbsoluteLayout) LayoutInflater.from(mContext).inflate(R.layout.line, null);

		LayoutParams mLayoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 0, 0);
		myLayout.setLayoutParams(mLayoutParams);

		container.addView(myLayout);
		lineView = (LineView) myLayout.findViewById(R.id.lineView);
		isLineMode = true;
	}

	// *****************************************************
	// *
	// * OnDismiss Listener
	// *
	// *****************************************************

	/**
	 * Clears the selection when the context menu is dismissed.
	 */
	public void onDismiss() {
		// clearSelection();
		mContextMenuVisible = false;
	}

	// *****************************************************
	// *
	// * Text Selection Javascript Interface Listener
	// *
	// *****************************************************

	/**
	 * Shows/updates the context menu based on the range
	 * 
	 * @param error
	 */
	public void tsjiJSError(String error) {
	}

	/**
	 * The user has started dragging the selection handles.
	 */
	public void tsjiStartSelectionMode() {

		startSelectionMode();

	}

	/**
	 * The user has stopped dragging the selection handles.
	 */
	public void tsjiEndSelectionMode() {

		endSelectionMode();
	}

	/**
	 * The selection has changed
	 * 
	 * @param range
	 * @param text
	 * @param handleBounds
	 * @param menuBounds
	 */
	public void tsjiSelectionChanged(String range, String text, String handleBounds, String menuBounds) {

		handleSelection(range, text, handleBounds);
		Rect displayRect = getContextMenuBounds(menuBounds);

		if (displayRect != null)
			// This will send the menu rect
			showContextMenu(displayRect);
	}

	/**
	 * Receives the content width for the page.
	 */
	public void tsjiSetContentWidth(float contentWidth) {
		mContentWidth = (int) getDensityDependentValue(contentWidth, mContext);
	}

	// *****************************************************
	// *
	// * Convenience
	// *
	// *****************************************************

	/**
	 * Puts up the selection view.
	 * 
	 * @param range
	 * @param text
	 * @param handleBounds
	 * @return
	 */
	protected void handleSelection(String range, String text, String handleBounds) {
		try {
			JSONObject selectionBoundsObject = new JSONObject(handleBounds);

			float scale = getDensityIndependentValue(getScale(), mContext);

			Rect handleRect = new Rect();
			handleRect.left = (int) (getDensityDependentValue(selectionBoundsObject.getInt("left"), getContext()) * scale);
			handleRect.top = (int) (getDensityDependentValue(selectionBoundsObject.getInt("top"), getContext()) * scale);
			handleRect.right = (int) (getDensityDependentValue(selectionBoundsObject.getInt("right"), getContext()) * scale);
			handleRect.bottom = (int) (getDensityDependentValue(selectionBoundsObject.getInt("bottom"), getContext()) * scale);

			mSelectionBounds = handleRect;
			mSelectedRange = range;
			mSelectedText = text;

			if (!isInSelectionMode()) {
				startSelectionMode();
			}

			drawSelectionHandles();

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Calculates the context menu display rect
	 * 
	 * @param menuBounds
	 * @return The display Rect
	 */
	protected Rect getContextMenuBounds(String menuBounds) {
		try {

			JSONObject menuBoundsObject = new JSONObject(menuBounds);

			float scale = getDensityIndependentValue(getScale(), mContext);

			Rect displayRect = new Rect();
			displayRect.left = (int) (getDensityDependentValue(menuBoundsObject.getInt("left"), getContext()) * scale);
			displayRect.top = (int) (getDensityDependentValue(menuBoundsObject.getInt("top") - 25, getContext()) * scale);
			displayRect.right = (int) (getDensityDependentValue(menuBoundsObject.getInt("right"), getContext()) * scale);
			displayRect.bottom = (int) (getDensityDependentValue(menuBoundsObject.getInt("bottom") + 25, getContext()) * scale);

			return displayRect;

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	// *****************************************************
	// *
	// * Density Conversion
	// *
	// *****************************************************

	/**
	 * Returns the density dependent value of the given float
	 * 
	 * @param val
	 * @param ctx
	 * @return
	 */
	public float getDensityDependentValue(float val, Context ctx) {

		// Get display from context
		Display display = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

		// Calculate min bound based on metrics
		DisplayMetrics metrics = new DisplayMetrics();
		display.getMetrics(metrics);

		return val * (metrics.densityDpi / 160f);

		// return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
		// metrics);

	}

	/**
	 * Returns the density independent value of the given float
	 * 
	 * @param val
	 * @param ctx
	 * @return
	 */
	public float getDensityIndependentValue(float val, Context ctx) {

		// Get display from context
		Display display = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

		// Calculate min bound based on metrics
		DisplayMetrics metrics = new DisplayMetrics();
		display.getMetrics(metrics);

		return val / (metrics.densityDpi / 160f);

		// return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, val,
		// metrics);

	}

	// TODO 跟业务相关，独立封装
	public String submitCondition() {
		StringBuilder sb = new StringBuilder();
		Set<String> set = lineView.getData();
		for (String selectStr : set) {
			String[] array = selectStr.split(SELECT_SPLIT);
			sb.append(array[0]).append("\n");
		}
		Toast.makeText(mContext, sb.toString(), Toast.LENGTH_LONG).show();
		return sb.toString();
	}

	/**
	 * 获取选中的条件
	 */
	public List<String> getSelection() {
		List<String> list = new ArrayList<String>();
		Set<String> set = lineView.getData();
		for (String selectStr : set) {
			String[] array = selectStr.split(SELECT_SPLIT);
			list.add(array[0]);
		}
		return list;
	}

	@Override
	public void tsjiSelectionClientRects(String rectStr, String selectContext) {
		// 一开始进行初始化。获取行高，并取得对应的bottom
		// 计算top+实际行高 取得最靠近的bottom
		try {
			float scale = getDensityIndependentValue(getScale(), mContext);
			JSONArray jsonArray = new JSONArray(rectStr);
			int length = jsonArray.length();
			this.selectContext = selectContext;
			// TODO 优化,每次都new一个实例
			rectList = new ArrayList<Rect>();
			for (int i = 0; i < length; i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);

				Rect handleRect = new Rect();
				handleRect.left = (int) (getDensityDependentValue(jsonObject.getInt("left"), getContext()) * scale);
				handleRect.top = (int) (getDensityDependentValue(jsonObject.getInt("top"), getContext()) * scale);
				handleRect.right = (int) (getDensityDependentValue(jsonObject.getInt("right"), getContext()) * scale);
				handleRect.bottom = (int) (getDensityDependentValue(jsonObject.getInt("bottom"), getContext()) * scale);
				// TODO
				// handleRect.bottom = 92;
				rectList.add(handleRect);
			}
		} catch (JSONException e) {
			// e.printStackTrace();
		}
	}

	/**
	 * 关闭画线模式
	 */
	public void closeLineMode() {
		this.isLineMode = false;
	}

	/**
	 * 清空所有画线
	 */
	public void clearLine() {

		lineView.removeAll();
		myLayout.removeAllViews();
		myLayout.addView(lineView);
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		if (isLineMode) {
			scrollY = t;
			myLayout.scrollTo(l, t);
		}

		super.onScrollChanged(l, t, oldl, oldt);
	}

	/**
	 * 文本选中事件
	 */
	public interface OnTextSelectListener {

		/**
		 * 选中文本
		 * 
		 * @param text 选中的文本
		 */
		void select(String text);

		/**
		 * 取消选中文本
		 * 
		 * @param text 取消选中的文本
		 */
		void cancle(String text);
	}

	/**
	 * 文本选择监听器对象
	 */
	private OnTextSelectListener onTextSelectListener;

	public void setOnTextSelectListener(OnTextSelectListener onTextSelectListener) {
		this.onTextSelectListener = onTextSelectListener;
	}
}
