package com.lizz.tc25;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    WebView mWebview;
    WebSettings mWebSettings;

    String _curUrl = "file:///android_asset/index.html";


    Rfd8500Object rfd8500 = null;
    Handler notifyHandler = new NotifyHandler();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mWebview = findViewById(R.id.webview);

        mWebSettings = mWebview.getSettings();
        mWebSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebSettings.setJavaScriptEnabled(true);
        mWebSettings.setUseWideViewPort(true);
        mWebSettings.setLoadWithOverviewMode(true);


        mWebview.loadUrl(_curUrl);

        //设置不用系统浏览器打开,直接显示在当前Webview
        mWebview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                //Android 8.0以下版本的需要返回true 并且需要loadUrl()
                if (Build.VERSION.SDK_INT < 26) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });

        //设置WebChromeClient类
        mWebview.setWebChromeClient(new WebChromeClient() {
            //获取网站标题
            @Override
            public void onReceivedTitle(WebView view, String title) {

            }

            //获取加载进度
            @Override
            public void onProgressChanged(WebView view, int newProgress) {

            }
        });
        //设置WebViewClient类
        mWebview.setWebViewClient(new WebViewClient() {
            //设置加载前的函数
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {

            }

            //设置结束加载函数
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                setTitle(String.valueOf(view.getTitle()));
            }
        });


        rfd8500 = new Rfd8500Object(this, notifyHandler);

        mWebview.addJavascriptInterface(rfd8500, Rfd8500Object.TAG);


    }


    //fix -> Binary XML file line #9: Error inflating class android.webkit.WebView
    @Override
    public AssetManager getAssets() {
        return getResources().getAssets();
    }

    //点击返回上一页面而不是退出浏览器
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebview.canGoBack()) {
            mWebview.goBack();//返回上个页面
            return true;
        }
        return super.onKeyDown(keyCode, event);//退出H5界面
    }

    @Override
    protected void onPause() {
        if (rfd8500 != null) {
            rfd8500.disconnect();
        }

        mWebview.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebview.onResume();
//        rfd8500.connect(null);
    }

    //销毁Webview
    @Override
    protected void onDestroy() {

        if (rfd8500 != null) {
            rfd8500.dispose();
            rfd8500 = null;
        }

        if (mWebview != null) {
            mWebview.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            mWebview.clearHistory();

            ((ViewGroup) mWebview.getParent()).removeView(mWebview);
            mWebview.destroy();
            mWebview = null;
        }
        super.onDestroy();
    }


    class NotifyHandler extends Handler {

        @Override
        public void handleMessage(@NonNull Message msg) {

            String data = msg.getData().getString("data");
            Log.d(TAG, TAG + data);
            if (mWebview != null) {
                mWebview.evaluateJavascript(Rfd8500Object.TAG + ".onStatus" + "('" + data + "')", null);
            }

        }
    }


}
