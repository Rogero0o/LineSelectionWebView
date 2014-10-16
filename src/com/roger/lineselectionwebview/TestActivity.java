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

import com.roger.lineselectionwebview.LSWebView.OnTextSelectListener;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Toast;

public class TestActivity extends Activity {

	LineView myView;
	ViewGroup container;
	private LSWebView webView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		webView = (LSWebView) findViewById(R.id.webView);
		container = (ViewGroup) findViewById(R.id.container);
		webView.openLineMode(container);
		webView.setOnTextSelectListener(new OnTextSelectListener() {

			@Override
			public void select(String text) {
				// TODO Auto-generated method stub
				Toast.makeText(getBaseContext(), "选中：" + text, Toast.LENGTH_SHORT).show();
			}

			@Override
			public void cancle(String text) {
				// TODO Auto-generated method stub
				Toast.makeText(getBaseContext(), "取消：" + text, Toast.LENGTH_SHORT).show();
			}
		});

		webView.loadUrl("file:///android_asset/content.html");
	}

}