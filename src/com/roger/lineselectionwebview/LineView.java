package com.roger.lineselectionwebview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 下划线View
 */
public class LineView extends View {

	private final String color = "#FF0000";

	private Paint paint;
	/**
	 * 存放选中内容+起始坐标与选中内容矩形集合的映射
	 */
	private Map<String, List<Rect>> lineMap;
	/**
	 * 当前webview的宽度
	 */
	private int width;

	public LineView(Context context) {
		super(context);
		init();
	}

	public LineView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public LineView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	/**
	 * 初始化画笔的属性
	 */
	public void init() {
		paint = new Paint();
		paint.setStrokeWidth(4);
		paint.setAntiAlias(true);
		paint.setColor(Color.parseColor(color));
		paint.setStyle(Style.FILL);
		lineMap = new HashMap<String, List<Rect>>();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return super.onTouchEvent(event);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// TODO 封装和优化
		for (Map.Entry<String, List<Rect>> entry : lineMap.entrySet()) {
			List<Rect> rectList = entry.getValue();
			List<Integer> indexList = computeLineIndex(rectList);
			int indexSize = indexList.size();
			if (indexSize > 1) {// 有多行的情况
				for (int i = 0; i < indexSize; i++) {
					int startIndex = 0;
					int endIndex = 0;

					if (i == 0) {// 第一行
						startIndex = 0;
						endIndex = indexList.get(i + 1);
					} else if (i == indexSize - 1) {// 最后一行
						startIndex = indexList.get(i) + 1;
						endIndex = rectList.size() - 1;
					} else {// 中间的行数
						startIndex = indexList.get(i) + 1;
						endIndex = indexList.get(i + 1);
					}
					Rect startRect = rectList.get(startIndex);
					Rect endRect = rectList.get(endIndex);
					Rect realEndRect = endRect;
					// 存在公式时,最后一个矩形的left坐标与上一个一样时,会扩大画线区域,因此选择两个节点中宽度较小的作为最终矩形
					// TODO 多个段落时，选中空行会有bug
					if (endIndex > 1) {
						Rect endPreRect = rectList.get(endIndex - 1);
						// left相等，且在同一行
						// 为何要判断是否在同一行:若选中两行,矩形有两个,左右起始点分别为16-1000和16-1001,则少画了一条横线
						if (endRect.left == endPreRect.left) {
							if (endRect.width() > endPreRect.width() && !indexList.contains(endIndex - 1)) {
								realEndRect = endPreRect;
							} else {
								realEndRect = endRect;
							}
						}
					}
					if (i == 0) {// 第一行起点
						canvas.drawCircle(startRect.left, realEndRect.bottom, 8, paint);
					}
					canvas.drawLine(startRect.left, realEndRect.bottom, realEndRect.right, realEndRect.bottom, paint);
					if (i == indexSize - 1) {// 最后一行终点
						canvas.drawCircle(realEndRect.right, realEndRect.bottom, 8, paint);
					}
				}
			} else {// 只有一行的情况,获取第一个的left坐标和最后一个right坐标
				Rect startRect = rectList.get(0);
				int size = rectList.size();
				Rect endRect = rectList.get(size - 1);
				Rect realEndRect = endRect;

				// 最后一个矩形的left坐标与上一个一样时,会扩大画线区域,因此选择两个节点中宽度较小的作为最终矩形
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
				canvas.drawCircle(startRect.left, realEndRect.bottom, 8, paint);
				canvas.drawLine(startRect.left, realEndRect.bottom, realEndRect.right, realEndRect.bottom, paint);
				canvas.drawCircle(realEndRect.right, realEndRect.bottom, 8, paint);
			}
		}
	}

	/**
	 * 删除画线并重绘
	 * @param context
	 */
	public void remove(String context) {
		lineMap.remove(context);
		invalidate();
	}

	/**
	 * 增加画线，并重绘
	 */
	public void add(String selectContext, List<Rect> rectList) {
		this.lineMap.put(selectContext, rectList);
		invalidate();
	}

	/**
	 * 删除所有画线
	 */
	public void removeAll() {
		lineMap.clear();
		invalidate();
	}

	/**
	 *  判断是否存在
	 */
	public boolean exist(String selectContext) {
		return lineMap.containsKey(selectContext);
	}

	/**
	 * 获取选中的文本值
	 * @return
	 */
	public Set<String> getData() {
		return lineMap.keySet();
	}

	/**
	 * 计算换行的索引
	 * @param list
	 * @return
	 */
	public List<Integer> computeLineIndex(List<Rect> list) {
		List<Integer> indexList = new ArrayList<Integer>();
		// 第一行的索引为0
		indexList.add(0);
		int size = list.size();
		if (size > 1) {// 超过两个矩形才执行以下逻辑，如单独选择一个字符，没必要计算行数
			int lineWidth = 500;
			if (width != 0) {
				lineWidth = width / 2;
			}
			for (int i = 0; i < size; i++) {
				if (i > 0) {
					Rect curRect = list.get(i);
					Rect preRect = list.get(i - 1);
					// 当前left - 上一个right >WebView宽度的一般,则视为换行,记住上一个所在索引，表示一行
					if (Math.abs(curRect.left - preRect.right) > lineWidth) {
						indexList.add(i - 1);
					}
				}
			}
		}
		return indexList;
	}

	public void setWidth(int width) {
		this.width = width;
	}

}
