package com.jabstone.jabtalk.basic.activity;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@SuppressLint("SetJavaScriptEnabled")
public class BrowserActivity extends Activity implements DialogInterface.OnCancelListener {

    private final int DIALOG_GENERIC = 1;
    private final String INTENT_EXTRA_URL = "IMG_URL";
    private final String INTENT_EXTRA_IMAGE = "IMG_BASE64";
    private String TAG = BrowserActivity.class.getSimpleName();
    private WebView webView;
    private Intent curIntent = null;
    private ProgressDialog m_dialog = null;
    private DownloadTask downloadTask = null;
    private Pattern m_googleThumbnailPattern = Pattern.compile("data:image/(.*?);base64",
            Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        m_dialog = new ProgressDialog(this);
        m_dialog.setCancelable(true);
        m_dialog.setIndeterminate(true);
        m_dialog.setMessage("Loading...");

        // setup the view of the web page and the associated web client
        setContentView(R.layout.browser);
        webView = (WebView) findViewById(R.id.webview);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        WebClient myWebView = new WebClient();
        webView.setWebViewClient(myWebView);
        registerForContextMenu(webView);
        webView.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                getIntent().removeExtra(INTENT_EXTRA_URL);
                getIntent().removeExtra(INTENT_EXTRA_IMAGE);
                WebView.HitTestResult res = webView.getHitTestResult();
                if (res != null && res.getExtra() != null) {
                    String extra = res.getExtra();
                    if (extra.startsWith("data:image")) {
                        getIntent().putExtra(INTENT_EXTRA_IMAGE, extra);
                        openContextMenu(v);
                        return true;
                    } else if (extra.startsWith("http")) {
                        getIntent().putExtra(INTENT_EXTRA_URL, extra);
                        openContextMenu(v);
                        return true;
                    }
//                    } else if ( extra.startsWith ( "http" ) ) {
//                        // Image starts with http but isn't a valid file type
//                        getIntent ().putExtra ( JTApp.INTENT_EXTRA_DIALOG_TITLE,
//                                getString ( R.string.app_name ) );
//                        getIntent ()
//                                .putExtra (
//                                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
//                                        getString ( R.string.dialog_message_browser_unsupported_image_type ) );
//                        showDialog ( DIALOG_GENERIC );
//                        return true;

                } else {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.app_name));
                    getIntent()
                            .putExtra(
                                    JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                                    getString(R.string.dialog_message_browser_unsupported_image_type));
                    showDialog(DIALOG_GENERIC);
                    return true;
                }
                return false;
            }
        });

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if ((getIntent().hasExtra(INTENT_EXTRA_URL)
                && getIntent().getStringExtra(INTENT_EXTRA_URL) != null) ||
                getIntent().hasExtra(INTENT_EXTRA_IMAGE) && getIntent().getStringExtra(INTENT_EXTRA_IMAGE) != null) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.browser_context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_download:
                if (downloadTask == null || downloadTask.getStatus() == Status.FINISHED) {
                    downloadTask = new DownloadTask();
                    downloadTask.execute();
                } else {
                    JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                            "Download Task in invalid state");
                }
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder;
        switch (id) {
            case DIALOG_GENERIC:
            default:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getIntent().getStringExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE));
                builder.setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE));
                builder.setPositiveButton(R.string.button_ok,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_GENERIC);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE);
                                getIntent().removeExtra(
                                        JTApp.INTENT_EXTRA_DIALOG_FINISH_ON_DISMISS);
                            }
                        });
                break;
        }
        return builder.create();
    }

    @Override
    protected void onPause() {
        super.onPause();
        m_dialog.dismiss();
        unlockScreenOrientation();
        webView.stopLoading();
        super.onDestroy();
    }

    private void lockScreenOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void unlockScreenOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    private void downloadImage(File fileName, String url) throws IOException {
        BufferedInputStream bis = null;
        ByteArrayOutputStream bos = null;
        URLConnection conn;
        try {
            URL webUrl = new URL(url);
            conn = webUrl.openConnection();
            bis = new BufferedInputStream(conn.getInputStream());
            bos = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int size;
            while ((size = bis.read(data, 0, 1024)) > -1) {
                bos.write(data, 0, size);
            }
            bis.close();
            bos.flush();
            saveImage(fileName, bos.toByteArray());
            bos.close();
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (bos != null) {
                    bos.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private void saveImage(File fileName, byte[] image) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fileName);
            fos.write(image);
            fos.flush();
            fos.close();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String getFileExtention(String fileName, boolean includeDot) {
        int pos = fileName.lastIndexOf(".");
        if (pos >= 0) {
            if (includeDot) {
                return fileName.substring(pos);
            } else {
                return fileName.substring(pos + 1);
            }
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        curIntent = intent;
        showWeb();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (curIntent == null) {
            curIntent = getIntent();
            showWeb();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        boolean ret;
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            // deal with the web option
            webView.goBack();
            ret = true;

        } else {
            // deal with the application normal behavior
            ret = super.onKeyDown(keyCode, event);
        }
        return ret;
    }

    public void onCancel(DialogInterface dialog) {
        webView.stopLoading();
    }

    /**
     * Displays either the web client or an error, if the current intent is not
     * set.
     */
    private void showWeb() {
        // get the URL to display from the passed in intent
        String url = JTApp.getSearchProvider()
                + getIntent().getStringExtra(JTApp.INTENT_EXTRA_SEARCH_TERM);
        webView.loadUrl(url);
    }

    /**
     * Web view client for use in our display of web based content.
     */
    private class WebClient extends WebViewClient {

        private boolean clearHistory = false;

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            // show the loading dialog as the page starts to load
            m_dialog.setMessage(getString(R.string.progress_loading));
            m_dialog.show();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // hide the page dialog when the page is finished loading
//            Matcher imgMatcher = m_imagePattern.matcher(url);
//            if ( (url.contains ( "google.com" ) && url.contains("imgrc=")) || imgMatcher.find()) {
//                downloadTip.setVisibility ( View.VISIBLE );
//            } else {
//                downloadTip.setVisibility ( View.INVISIBLE );
//            }

            m_dialog.dismiss();
            if (clearHistory) {
                clearHistory = false;
                webView.clearHistory();
            }

        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                                    String failingUrl) {
            // on an error, remove the loading dialog and tell the user
            // about it
            m_dialog.dismiss();
            Toast.makeText(getBaseContext(), "Web page error: " + description,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }
    }

    private class DownloadTask extends AsyncTask<Void, Void, Void> {

        private boolean errorFlag = false;
        private File resultFile = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            m_dialog.setMessage(getString(R.string.progress_downloading_image));
            m_dialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getIntent().hasExtra(INTENT_EXTRA_URL)) {
                    String location = getIntent().getStringExtra(INTENT_EXTRA_URL);
                    String ext = getFileExtention(location, true);
                    if (ext != null && (!ext.endsWith(".jpg") || !ext.endsWith(".jpeg") || !ext.endsWith(".gif") || !ext.endsWith(".png") || !ext.endsWith(".bmp"))) {
                        ext = ".jpg";
                    }
                    resultFile = new File(JTApp.getDataStore().getTempDirectory(), UUID.randomUUID()
                            .toString() + ext);
                    downloadImage(resultFile, location);
                }
                if (getIntent().hasExtra(INTENT_EXTRA_IMAGE)) {
                    String base64Text = getIntent().getStringExtra(INTENT_EXTRA_IMAGE);
                    String ext = null;
                    Matcher m = m_googleThumbnailPattern.matcher(base64Text);
                    if (m.find()) {
                        String type = m.group(1);
                        if (type.equalsIgnoreCase("gif")) {
                            ext = ".gif";
                        }
                        if (type.equalsIgnoreCase("png")) {
                            ext = ".png";
                        }
                        if (type.equalsIgnoreCase("jpeg") || type.equalsIgnoreCase("jpg")) {
                            ext = ".jpg";
                        }
                        if (ext != null) {
                            int base64Pos = base64Text.indexOf("base64");
                            if (base64Pos > -1) {
                                String base64Image = base64Text.substring(base64Pos + 7);
                                byte[] image = Base64.decode(base64Image, Base64.DEFAULT);
                                resultFile = new File(JTApp.getDataStore().getTempDirectory(), UUID.randomUUID()
                                        .toString() + ext);
                                saveImage(resultFile, image);
                            }
                        }
                    }
                }

            } catch (IOException e) {
                errorFlag = true;
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                        getString(R.string.dialog_title_save_results));
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);
            m_dialog.dismiss();
            unlockScreenOrientation();
            if (errorFlag) {
                showDialog(DIALOG_GENERIC);
            } else {
                Intent intent = new Intent();
                intent.setData(Uri.fromFile(resultFile));
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }
}
