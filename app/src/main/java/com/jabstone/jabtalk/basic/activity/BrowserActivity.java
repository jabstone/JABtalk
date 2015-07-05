package com.jabstone.jabtalk.basic.activity;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;


@SuppressLint ( "SetJavaScriptEnabled" )
public class BrowserActivity extends Activity implements DialogInterface.OnCancelListener {

    private String TAG = BrowserActivity.class.getSimpleName ();
    private WebView webView;
    private WebClient myWebView;
    private TextView downloadTip;
    private Intent curIntent = null;
    private ProgressDialog m_dialog = null;
    private DownloadTask downloadTask = null;
    private String googleImageSrc = null;
    private Pattern m_imagePattern = Pattern.compile (
            "http.*?(\\.gif|\\.png|\\.jpeg|\\.jpg|th\\?id=)", Pattern.CASE_INSENSITIVE );
    private Pattern m_googleImagePattern = Pattern.compile ( "http.*imgrc=",
            Pattern.CASE_INSENSITIVE );

    private final int DIALOG_GENERIC = 1;
    private final String INTENT_EXTRA_URL = "IMG_URL";

    @Override
    protected void onCreate ( Bundle savedInstanceState ) {
        super.onCreate ( savedInstanceState );

        m_dialog = new ProgressDialog ( this );
        m_dialog.setCancelable ( true );
        m_dialog.setIndeterminate ( true );
        m_dialog.setMessage ( "Loading..." );

        // setup the view of the web page and the associated web client
        setContentView ( R.layout.browser );
        webView = ( WebView ) findViewById ( R.id.webview );
        downloadTip = ( TextView ) findViewById ( R.id.downloadHelp );

        webView.getSettings ().setJavaScriptEnabled ( true );
        webView.getSettings ().setBuiltInZoomControls ( true );
        myWebView = new WebClient ();
        webView.setWebViewClient ( myWebView );
        registerForContextMenu ( webView );
        webView.setOnLongClickListener ( new View.OnLongClickListener () {

            @Override
            public boolean onLongClick ( View v ) {
                getIntent ().removeExtra ( INTENT_EXTRA_URL );
                WebView.HitTestResult res = webView.getHitTestResult ();
                if ( res != null && res.getExtra () != null ) {
                    String extra = res.getExtra ();
                    Matcher m = m_imagePattern.matcher ( extra );
                    if ( m.find () ) {
                        String url = m.group ( 0 );
                        getIntent ().putExtra ( INTENT_EXTRA_URL, url );
                        openContextMenu ( v );
                        return true;
                    } else if ( extra.startsWith ( "http" ) ) {
                        // Image starts with http but isn't a valid file type
                        getIntent ().putExtra ( JTApp.INTENT_EXTRA_DIALOG_TITLE,
                                getString ( R.string.app_name ) );
                        getIntent ()
                                .putExtra (
                                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                                        getString ( R.string.dialog_message_browser_unsupported_image_type ) );
                        showDialog ( DIALOG_GENERIC );
                    }
                }
                return true;
            }
        } );

    }

    @Override
    public void onCreateContextMenu ( ContextMenu menu, View v, ContextMenuInfo menuInfo ) {
        super.onCreateContextMenu ( menu, v, menuInfo );
        if ( getIntent ().hasExtra ( INTENT_EXTRA_URL )
                && getIntent ().getStringExtra ( INTENT_EXTRA_URL ) != null ) {
            MenuInflater inflater = getMenuInflater ();
            inflater.inflate ( R.menu.browser_context_menu, menu );
        }
    }

    @Override
    public boolean onContextItemSelected ( MenuItem item ) {
        switch ( item.getItemId () ) {
            case R.id.menu_download:
                if ( downloadTask == null || downloadTask.getStatus () == Status.FINISHED ) {
                    downloadTask = new DownloadTask ();
                    downloadTask.execute ( getIntent ().getStringExtra ( INTENT_EXTRA_URL ) );
                } else {
                    JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                            "Download Task in invalid state" );
                }
                return true;
            default:
                return super.onContextItemSelected ( item );
        }
    }

    @Override
    protected Dialog onCreateDialog ( int id ) {
        Dialog alert = null;
        AlertDialog.Builder builder = null;
        switch ( id ) {
            case DIALOG_GENERIC:
                builder = new AlertDialog.Builder ( this );
                builder.setTitle ( getIntent ().getStringExtra ( JTApp.INTENT_EXTRA_DIALOG_TITLE ) );
                builder.setMessage ( getIntent ().getStringExtra (
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE ) );
                builder.setPositiveButton ( R.string.button_ok,
                        new DialogInterface.OnClickListener () {

                            public void onClick ( DialogInterface dialog, int which ) {
                                dismissDialog ( DIALOG_GENERIC );
                                getIntent ().removeExtra ( JTApp.INTENT_EXTRA_DIALOG_TITLE );
                                getIntent ().removeExtra ( JTApp.INTENT_EXTRA_DIALOG_MESSAGE );
                                getIntent ().removeExtra (
                                        JTApp.INTENT_EXTRA_DIALOG_FINISH_ON_DISMISS );
                            }
                        } );
                alert = builder.create ();
                break;
        }
        return alert;
    }

    @Override
    protected void onPause () {
        m_dialog.dismiss ();
        unlockScreenOrientation ();
        webView.stopLoading ();
        super.onDestroy ();
    }

    private void lockScreenOrientation () {
        int currentOrientation = getResources ().getConfiguration ().orientation;
        if ( currentOrientation == Configuration.ORIENTATION_PORTRAIT ) {
            setRequestedOrientation ( ActivityInfo.SCREEN_ORIENTATION_PORTRAIT );
        } else {
            setRequestedOrientation ( ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE );
        }
    }

    private void unlockScreenOrientation () {
        setRequestedOrientation ( ActivityInfo.SCREEN_ORIENTATION_SENSOR );
    }

    private File downloadImage ( String url ) throws IOException {
        String location = url;
        File resultFile = null;
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        URLConnection conn = null;
        try {
            String ext = getFileExtention ( location, true );
            if ( url.contains ( "th?id=" ) ) {
                ext = ".jpg";
            }
            File output = new File ( JTApp.getDataStore ().getTempDirectory (), UUID.randomUUID ()
                    .toString () + ext );
            URL webUrl = new URL ( location );
            conn = webUrl.openConnection ();
            bis = new BufferedInputStream ( conn.getInputStream () );
            fos = new FileOutputStream ( output );
            byte[] data = new byte[1024];
            int size = 0;
            while ( ( size = bis.read ( data, 0, 1024 ) ) > -1 ) {
                fos.write ( data, 0, size );
            }
            bis.close ();
            fos.flush ();
            fos.close ();
            resultFile = output;
        } finally {
            try {
                if ( bis != null ) {
                    bis.close ();
                }
                if ( fos != null ) {
                    fos.close ();
                }
            } catch ( Exception ignore ) {
            }
        }
        return resultFile;
    }

    private String getFileExtention ( String fileName, boolean includeDot ) {
        int pos = fileName.lastIndexOf ( "." );
        if ( pos >= 0 ) {
            if ( includeDot ) {
                return fileName.substring ( pos );
            } else {
                return fileName.substring ( pos + 1 );
            }
        }
        return null;
    }

    @Override
    protected void onNewIntent ( Intent intent ) {
        super.onNewIntent ( intent );
        curIntent = intent;
        showWeb ();
    }

    @Override
    protected void onStart () {
        super.onStart ();
        if ( curIntent == null ) {
            curIntent = getIntent ();
            showWeb ();
        }
    }

    @Override
    public boolean onKeyDown ( int keyCode, KeyEvent event ) {

        boolean ret;
        if ( ( keyCode == KeyEvent.KEYCODE_BACK ) && webView.canGoBack () ) {
            // deal with the web option
            webView.goBack ();
            ret = true;

        } else {
            // deal with the application normal behavior
            ret = super.onKeyDown ( keyCode, event );
        }
        return ret;
    }

    public void onCancel ( DialogInterface dialog ) {
        webView.stopLoading ();
    }

    /**
     * Displays either the web client or an error, if the current intent is not
     * set.
     */
    private void showWeb () {
        // get the URL to display from the passed in intent
        String url = JTApp.getSearchProvider ()
                + getIntent ().getStringExtra ( JTApp.INTENT_EXTRA_SEARCH_TERM );
        webView.loadUrl ( url );
    }

    /**
     * Web view client for use in our display of web based content.
     */
    private class WebClient extends WebViewClient {

        private boolean clearHistory = false;

        @Override
        public void onPageStarted ( WebView view, String url, Bitmap favicon ) {
            googleImageSrc = null;
            // show the loading dialog as the page starts to load
            m_dialog.setMessage ( getString ( R.string.progress_loading ) );
            m_dialog.show ();
        }

        @Override
        public void onPageFinished ( WebView view, String url ) {
            // hide the page dialog when the page is finished loading
            Matcher imgMatcher = m_imagePattern.matcher(url);
            if ( (url.contains ( "google.com" ) && url.contains("imgrc=")) || imgMatcher.find()) {
                downloadTip.setVisibility ( View.VISIBLE );
            } else {
                downloadTip.setVisibility ( View.INVISIBLE );
            }

            m_dialog.dismiss ();
            if ( clearHistory ) {
                clearHistory = false;
                webView.clearHistory ();
            }

        }

        @Override
        public void onReceivedError ( WebView view, int errorCode, String description,
                String failingUrl ) {
            // on an error, remove the loading dialog and tell the user
            // about it
            m_dialog.dismiss ();
            Toast.makeText ( getBaseContext (), "Web page error: " + description,
                    Toast.LENGTH_SHORT ).show ();
        }

        @Override
        public boolean shouldOverrideUrlLoading ( WebView view, String url ) {
            return false;
        }
    }

    private class DownloadTask extends AsyncTask<String, Void, Void> {

        private boolean errorFlag = false;
        private File resultFile = null;

        @Override
        protected void onPreExecute () {
            super.onPreExecute ();
            lockScreenOrientation ();
            m_dialog.setMessage ( getString ( R.string.progress_downloading_image ) );
            m_dialog.show ();
        }

        @Override
        protected Void doInBackground ( String... params ) {
            try {
                resultFile = downloadImage ( params[0] );
            } catch ( IOException e ) {
                errorFlag = true;
                getIntent ().putExtra ( JTApp.INTENT_EXTRA_DIALOG_TITLE,
                        getString ( R.string.dialog_title_save_results ) );
                getIntent ().putExtra ( JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage () );
            }
            return null;
        }

        @Override
        protected void onPostExecute ( Void param ) {
            super.onPostExecute ( param );
            m_dialog.dismiss ();
            unlockScreenOrientation ();
            if ( errorFlag ) {
                showDialog ( DIALOG_GENERIC );
            } else {
                Intent intent = new Intent ();
                intent.setData ( Uri.fromFile ( resultFile ) );
                setResult ( RESULT_OK, intent );
                finish ();
            }
        }
    }
}
