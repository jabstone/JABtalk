package com.jabstone.jabtalk.basic;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.jabstone.jabtalk.basic.listeners.ICategorySelectionListener;
import com.jabstone.jabtalk.basic.listeners.IDataStoreListener;
import com.jabstone.jabtalk.basic.listeners.ISpeechCompleteListener;
import com.jabstone.jabtalk.basic.listeners.IWordSelectionListener;
import com.jabstone.jabtalk.basic.storage.DataStore;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;
import com.jabstone.jabtalk.basic.widgets.AutoResizeTextView;
import com.jabstone.jabtalk.basic.widgets.PictureFrameDimensions;


public class JTApp extends Application implements OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener, OnInitListener {

    /* JABtalk Constants used throughout application */
    public final static int LOG_SEVERITY_ERROR = 0;
    public final static int LOG_SEVERITY_WARNING = 1;
    public final static int LOG_SEVERITY_INFO = 2;

    public final static int IMAGEVIEW_ID = 9410276;
    public final static int TEXTVIEW_ID = 9415590;
    public final static int RELATIVELAYOUT_ID = 2433746;

    public final static float MAX_TEXT_SIZE = 150f;
    public final static float TEXT_BUTTON_PADDING = .72f;

    public final static String EXTENSION_SYNTHESIZER = "syn";
    public final static String EXTENSION_TEXT_IMAGE = "txt";

    public final static String INTENT_EXTRA_TYPE = "Type";
    public final static String INTENT_EXTRA_IDEOGRAM_ID = "IdeogramId";
    public final static String INTENT_EXTRA_REFRESH = "RefreshGrid";
    public final static String INTENT_EXTRA_DIRTY_DATA = "DirtyData";
    public final static String INTENT_EXTRA_DIALOG_TITLE = "dialog.title";
    public final static String INTENT_EXTRA_DIALOG_MESSAGE = "dialog.message";
    public final static String INTENT_EXTRA_DIALOG_FINISH_ON_DISMISS = "dialog.finish";
    public final static String INTENT_EXTRA_SEARCH_TERM = "";
    public final static String INTENT_EXTRA_CALLED_FROM_MAIN = "CalledFromMain";
    public final static String INTENT_EXTRA_CLEAR_MANAGE_STACK = "ClearStack";

    public final static String DATASTORE_VERSION = "2";

    private String TAG = JTApp.class.getSimpleName ();
    private static Object lock = new Object ();
    private static JTApp me = null;
    private static List<ICategorySelectionListener> categoryListeners = new ArrayList<ICategorySelectionListener> ();
    private static List<IDataStoreListener> datastoreListeners = new ArrayList<IDataStoreListener> ();
    private static List<IWordSelectionListener> wordListeners = new ArrayList<IWordSelectionListener> ();
    private static List<ISpeechCompleteListener> speechListeners = new ArrayList<ISpeechCompleteListener> ();

    private MediaPlayer m_player = new MediaPlayer ();
    private AudioManager m_audioManager = null;
    private TextToSpeech m_speechManager = null;
    private StringBuffer m_buff = new StringBuffer ();
    private StringBuffer m_log = new StringBuffer ();
    private SimpleDateFormat m_formatter = new SimpleDateFormat ( "yyyy-MM-dd hh:mm a",
            Locale.ENGLISH );
    private static DataStore m_dataStore = null;
    private static ClipBoard m_clipboard = null;
    private boolean m_speechEngineReady = false;

    public static String URL_SUPPORT = "http://www.jabstone.com/support";

    private static List<Ideogram> m_currentWordQueue = new ArrayList<Ideogram> ();
    private static volatile int m_pictureVersion = 0;
    private static volatile PictureFrameDimensions m_pictureDimensions = null;

    @Override
    public void onCreate () {
        super.onCreate ();
        synchronized ( lock ) {
            if ( me == null ) {
                me = this;
                me.m_player.setOnCompletionListener ( this );
                m_dataStore = new DataStore ();
                m_clipboard = new ClipBoard ();
                m_audioManager = ( AudioManager ) getApplicationContext ().getSystemService (
                        Context.AUDIO_SERVICE );
                m_speechManager = new TextToSpeech ( this, this );
                m_pictureDimensions = new PictureFrameDimensions ( me.getResources ()
                        .getDimensionPixelSize ( R.dimen.picture_frame_width_default ), me
                        .getResources ().getDimensionPixelSize (
                                R.dimen.picture_frame_width_default ) );
            }
        }
    }

    @Override
    public void onTerminate () {
        super.onTerminate ();
        if ( m_player != null ) {
            m_player.release ();
        }
        if ( m_speechManager != null ) {
            m_speechManager.shutdown ();
        }
    }

    public static JTApp getInstance () {
        return me;
    }

    public static DataStore getDataStore () {
        return m_dataStore;
    }

    public static ClipBoard getClipBoard () {
        return m_clipboard;
    }

    public static boolean isSentenceBuilderEnabled () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean sb = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_sentence_building_key ), true );
        return sb;
    }

    public static boolean isScreenWakeLockEnabled () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean ac = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_screen_keep_on_key ), false );
        return ac;
    }

    public static boolean isNetworkConnected () {
        ConnectivityManager connManager = ( ConnectivityManager ) me
                .getSystemService ( Context.CONNECTIVITY_SERVICE );
        NetworkInfo activeNetwork = connManager.getActiveNetworkInfo ();
        return activeNetwork != null && activeNetwork.isConnected ();
    }

    public static boolean isAccessCodeRequired () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean ac = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_access_code_key ), true );
        return ac;
    }

    public static boolean isBackButtonDisabled () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean be = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_backbutton_disable_key ), false );
        return be;
    }

    public static boolean isDisplayPhraseEnabled () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean be = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_word_progress_key ), true );
        return be;
    }

    public static boolean isCameraAvailable () {
        PackageManager pm = JTApp.getInstance ().getPackageManager ();
        if ( pm.hasSystemFeature ( PackageManager.FEATURE_CAMERA ) ) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isMicrophoneAvailable () {
        PackageManager pm = JTApp.getInstance ().getPackageManager ();
        if ( pm.hasSystemFeature ( PackageManager.FEATURE_MICROPHONE ) ) {
            return true;
        } else {
            return false;
        }
    }

    public static String getSearchProvider () {
        String url = me.getResources ().getString ( R.string.google_image_search_url );
        return url;
    }

    public static void setPictureDimensions ( int width, int height ) {
        m_pictureDimensions.setWidth ( width );
        m_pictureDimensions.setHeight ( height );
        m_pictureVersion++;
    }

    public static PictureFrameDimensions getPictureDimensions ( PictureSize size,
            boolean isTextButton ) {
        PictureFrameDimensions pfd = null;
        switch ( size ) {
            case GridPicture:
                pfd = new PictureFrameDimensions ( m_pictureDimensions.getWidth (),
                        m_pictureDimensions.getHeight () );
                break;
            case SentencePicture:
                int h = Math.round ( m_pictureDimensions.getHeight ()
                        * getSentencePictureSizePreference () );
                int w = Math.round ( m_pictureDimensions.getWidth ()
                        * getSentencePictureSizePreference () );
                pfd = new PictureFrameDimensions ( w, h );
                break;
            default:
                pfd = new PictureFrameDimensions ( me.getResources ().getDimensionPixelSize (
                        R.dimen.picture_frame_width_default ), me.getResources ()
                        .getDimensionPixelSize ( R.dimen.picture_frame_width_default ) );
                break;
        }

        if ( isTextButton ) {
            pfd.setWidth ( ( int ) ( pfd.getWidth () * TEXT_BUTTON_PADDING ) );
            pfd.setHeight ( ( int ) ( pfd.getHeight () * TEXT_BUTTON_PADDING ) );
        }
        return pfd;
    }

    public static int getPictureVersion () {
        return m_pictureVersion;
    }

    public static float getScrollSpeed () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        float value = .015f;
        try {
            value = Float.parseFloat ( settings.getString (
                    me.getResources ().getString ( R.string.preference_scroll_speed_key ), ".01" ) );
        } catch ( Exception e ) {
            logMessage ( me.TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Error retrieving scroll speed from preferences" );
        }
        return value;
    }

    public static float getTitleFontSize () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        float value = 16f;
        try {
            value = Float.parseFloat ( settings.getString (
                    me.getResources ().getString ( R.string.preference_fontsize_key ), "17f" ) );
        } catch ( Exception e ) {
            logMessage ( me.TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Error retrieving picture title font size" );
        }
        return value;
    }

    public static void setSpeechResourceFound ( boolean value ) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        SharedPreferences.Editor editor = settings.edit ();
        editor.putBoolean (
                me.getResources ().getString ( R.string.preference_speech_resource_key ), value );
        editor.commit ();
    }

    public static boolean isCategoryAudioEnabled () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean value = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_category_audio_key ), true );
        return value;
    }

    public static boolean isWordAudioEnabled () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean value = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_word_audio_key ), true );
        return value;
    }

    public static boolean isSpeechResourceFound () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        boolean value = settings.getBoolean (
                me.getResources ().getString ( R.string.preference_speech_resource_key ), false );
        return value;
    }

    public static void setAcknowledgeNewFeatures () {

        int code = 0;
        try {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                    .getApplicationContext () );
            PackageInfo pinfo = me.getPackageManager ().getPackageInfo ( me.getPackageName (), 0 );
            code = pinfo.versionCode;
            SharedPreferences.Editor editor = settings.edit ();
            editor.putInt ( me.getResources ().getString ( R.string.preference_new_features_key ),
                    code );
            editor.commit ();
        } catch ( Exception e ) {

        }
    }

    public static String getVersionName () {
        String name = "";
        try {
            PackageInfo pinfo = me.getPackageManager ().getPackageInfo ( me.getPackageName (), 0 );
            name = pinfo.versionName;
        } catch ( Exception e ) {
            logMessage ( me.TAG, LOG_SEVERITY_WARNING, "Failed to retrieve version code." );
        }
        return name;
    }

    public static boolean isAcknowledgeNewFeatures () {
        boolean result = false;
        try {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                    .getApplicationContext () );
            int value = settings.getInt (
                    me.getResources ().getString ( R.string.preference_new_features_key ), 0 );
            PackageInfo pinfo = me.getPackageManager ().getPackageInfo ( me.getPackageName (), 0 );
            int code = pinfo.versionCode;
            if ( value >= code ) {
                result = true;
            }
        } catch ( Exception e ) {
            logMessage ( me.TAG, LOG_SEVERITY_WARNING,
                    "Failed to retrieve version code...surpressing new features screen" );
            result = true; // Let's not bug the user if a problem is encountered
        }
        return result;
    }

    public static int getPicturesPerRow ( boolean isLandscape ) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        if ( isLandscape ) {
            return Integer.parseInt ( settings
                    .getString (
                            me.getResources ().getString (
                                    R.string.preference_landscape_columns_key ), "4" ) );
        } else {
            return Integer
                    .parseInt ( settings.getString (
                            me.getResources ()
                                    .getString ( R.string.preference_portrait_columns_key ), "2" ) );
        }
    }

    public static void setPicturesPerRow ( int columns, boolean isLandscape ) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        SharedPreferences.Editor editor = settings.edit ();
        if ( isLandscape ) {
            editor.putString (
                    me.getResources ().getString ( R.string.preference_landscape_columns_key ),
                    String.valueOf ( columns ) );
        } else {
            editor.putString (
                    me.getResources ().getString ( R.string.preference_portrait_columns_key ),
                    String.valueOf ( columns ) );
        }
        editor.commit ();
    }

    public static float getSentencePictureSizePreference () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        return Float.parseFloat ( settings
                .getString (
                        me.getResources ().getString ( R.string.preference_sentence_bar_size_key ),
                        me.getResources ().getString (
                                R.string.preference_sentence_bar_size_default ) ) );
    }

    public static int getSentenceWordLimitPreference () {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences ( me
                .getApplicationContext () );
        return Integer.parseInt ( settings
                .getString (
                        me.getResources ().getString (
                                R.string.preference_sentence_bar_word_limit_key ),
                        me.getResources ().getString (
                                R.string.preference_sentence_bar_word_limit_default ) ) );
    }

    public static RelativeLayout getPictureLayout ( Context context, Type type, PictureSize size,
            boolean isTextPicture ) {

        RelativeLayout pictureView;
        pictureView = new RelativeLayout ( context );
        switch ( type ) {
            case Word:
                pictureView.setBackgroundDrawable ( context.getResources ().getDrawable (
                        com.jabstone.jabtalk.basic.R.drawable.word_background ) );
                break;
            case Category:
                pictureView.setBackgroundDrawable ( context.getResources ().getDrawable (
                        com.jabstone.jabtalk.basic.R.drawable.category_background ) );
                break;
        }
        PictureFrameDimensions pfd = getPictureDimensions ( size, false );

        int textSize = ( int ) getTitleFontSize ();
        switch ( size ) {
            case GridPicture:
                AbsListView.LayoutParams gridLayout = new AbsListView.LayoutParams (
                        pfd.getWidth (), pfd.getHeight () );
                pictureView.setLayoutParams ( gridLayout );
                break;
            case SentencePicture:
                LinearLayout.LayoutParams sentenceLayout = new LinearLayout.LayoutParams (
                        pfd.getWidth (), pfd.getHeight () );
                sentenceLayout.setMargins ( 10, 5, 0, 0 );
                pictureView.setLayoutParams ( sentenceLayout );
                textSize = 10;
        }

        if ( !isTextPicture ) {
            MarginLayoutParams tvParams = new MarginLayoutParams (
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT );
            tvParams.setMargins ( 0, 2, 0, 0 );
            RelativeLayout.LayoutParams tvLayout = new RelativeLayout.LayoutParams ( tvParams );
            tvLayout.addRule ( RelativeLayout.ALIGN_PARENT_TOP );
            tvLayout.addRule ( RelativeLayout.CENTER_HORIZONTAL );

            AutoResizeTextView tv = new AutoResizeTextView ( context );
            tv.setId ( TEXTVIEW_ID );
            tv.setTextSize ( TypedValue.COMPLEX_UNIT_SP, textSize );
            tv.setTypeface ( Typeface.DEFAULT, Typeface.BOLD );
            tv.setTextColor ( context.getResources ().getColor (
                    com.jabstone.jabtalk.basic.R.color.jabtalkWhite ) );
            tv.setGravity ( Gravity.CENTER_HORIZONTAL );
            pictureView.addView ( tv, tvLayout );

            MarginLayoutParams ivParams = new MarginLayoutParams ( pfd.getWidth (),
                    pfd.getHeight () );
            ivParams.setMargins ( 4, 4, 4, 4 );
            RelativeLayout.LayoutParams ivLayout = new RelativeLayout.LayoutParams ( ivParams );
            ImageView iv = new ImageView ( context );
            iv.setId ( IMAGEVIEW_ID );
            ivLayout.addRule ( RelativeLayout.BELOW, TEXTVIEW_ID );
            ivLayout.addRule ( RelativeLayout.CENTER_HORIZONTAL );
            pictureView.addView ( iv, ivLayout );
            pictureView.setTag ( m_pictureVersion );
        } else {
            MarginLayoutParams ivParams = new MarginLayoutParams ( pfd.getWidth (),
                    pfd.getHeight () );
            ivParams.setMargins ( 4, 4, 4, 4 );
            RelativeLayout.LayoutParams ivLayout = new RelativeLayout.LayoutParams ( ivParams );
            ImageView iv = new ImageView ( context );
            iv.setId ( IMAGEVIEW_ID );

            ivLayout.addRule ( RelativeLayout.CENTER_IN_PARENT );
            pictureView.addView ( iv, ivLayout );
            pictureView.setTag ( m_pictureVersion );

            MarginLayoutParams tvParams = new MarginLayoutParams (
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT );
            tvParams.setMargins ( 8, 8, 8, 8 );
            RelativeLayout.LayoutParams tvLayout = new RelativeLayout.LayoutParams ( tvParams );
            tvLayout.addRule ( RelativeLayout.CENTER_IN_PARENT );

            AutoResizeTextView tv = new AutoResizeTextView ( context );
            tv.setId ( TEXTVIEW_ID );
            tv.setTextSize ( MAX_TEXT_SIZE );
            tv.setTypeface ( Typeface.DEFAULT, Typeface.BOLD );
            tv.setTextColor ( context.getResources ().getColor (
                    com.jabstone.jabtalk.basic.R.color.jabtalkWhite ) );
            tv.setGravity ( Gravity.CENTER );
            pictureView.setTag ( m_pictureVersion );
            pictureView.addView ( tv, tvLayout );
        }
        return pictureView;
    }

    public static void removeAllListeners () {
        categoryListeners.clear ();
        wordListeners.clear ();
        speechListeners.clear ();
        datastoreListeners.clear ();
    }

    public static void addCategorySelectionListener ( ICategorySelectionListener listener ) {
        categoryListeners.add ( listener );
    }

    public static void addDataStoreListener ( IDataStoreListener listener ) {
        datastoreListeners.add ( listener );
    }

    public static void addWordSelectionListener ( IWordSelectionListener listener ) {
        wordListeners.add ( listener );
    }

    public static void addSpeechCompleteListener ( ISpeechCompleteListener listener ) {
        speechListeners.add ( listener );
    }

    public static void removeCategorySelectionListener ( ICategorySelectionListener listener ) {
        categoryListeners.remove ( listener );
    }

    public static void removeDataStoreListener ( IDataStoreListener listener ) {
        datastoreListeners.remove ( listener );
    }

    public static void removeWordSelectionListener ( IWordSelectionListener listener ) {
        wordListeners.remove ( listener );
    }

    public static void removeSpeechCompleteListener ( ISpeechCompleteListener listener ) {
        speechListeners.remove ( listener );
    }

    public static void fireCategorySelected ( Ideogram category, boolean touchEvent ) {
        me.m_buff.append ( me.m_formatter.format ( new Date () ) + " - Category selected: "
                + category.getLabel () + "\r\n" );
        for ( ICategorySelectionListener listener : JTApp.categoryListeners ) {
            listener.CategorySelected ( category, touchEvent );
        }
    }

    public static void fireDataStoreUpdated () {
        for ( IDataStoreListener listener : JTApp.datastoreListeners ) {
            listener.DataStoreUpdated ();
        }
    }

    public static void fireWordSelected ( Ideogram gram, boolean isSentenceWord ) {
        me.m_buff.append ( me.m_formatter.format ( new Date () ) + " - Word selected: "
                + gram.getLabel () + "\r\n" );
        for ( IWordSelectionListener listener : JTApp.wordListeners ) {
            listener.WordSelected ( gram, isSentenceWord );
        }
    }

    public static void play ( Ideogram gram ) {
        FileInputStream fis = null;
        if ( gram.isSynthesizeButton () ) {
            if ( me.m_speechEngineReady && me.m_speechManager != null && isSpeechResourceFound () ) {
                HashMap<String, String> optionMap = new HashMap<String, String> ();
                optionMap.put ( TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, gram.getId () );
                me.m_speechManager.speak ( gram.getPhrase (), TextToSpeech.QUEUE_FLUSH, optionMap );
            } else {
                logMessage ( me.TAG, JTApp.LOG_SEVERITY_ERROR,
                        "Speech engine was not initialized properly." );
                me.fireSpeechComplete ();
            }
        } else {
            int result = me.m_audioManager.requestAudioFocus ( me, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN );
            if ( result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ) {
                try {
                    File f = new File ( gram.getAudioPath () );
                    if ( !f.exists () && f.length () > 0 ) {
                        throw new Exception ( "Audio file does not exist: " + gram.getAudioPath () );
                    }
                    me.m_player.reset ();
                    fis = new FileInputStream ( f );
                    me.m_player.setDataSource ( fis.getFD () );
                    me.m_player.prepare ();
                    me.m_player.start ();
                    
                } catch ( Exception e ) {
                    JTApp.logMessage ( me.TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage () );
                    me.fireSpeechComplete ();
                } finally {
                    try {
                        if ( fis != null ) {
                            fis.close ();
                        }
                    } catch ( Exception ignore ) {
                        JTApp.logMessage ( me.TAG, JTApp.LOG_SEVERITY_WARNING,
                                "Failed to close audio file" );
                    }
                }
            } else {
                logMessage ( me.TAG, JTApp.LOG_SEVERITY_WARNING,
                        "System failed to grant audio focus. Try again later." );
                me.fireSpeechComplete ();
            }
        }
    }

    public static void play ( List<Ideogram> wordList ) {
        JTApp.m_currentWordQueue.clear ();
        JTApp.m_currentWordQueue.addAll ( wordList );
        if ( JTApp.m_currentWordQueue.size () > 0 ) {
            Ideogram w = JTApp.m_currentWordQueue.get ( 0 );
            JTApp.play ( w );
            m_currentWordQueue.remove ( 0 );
        }
    }

    public static void logMessage ( String tag, int severity, String message ) {
        me.m_log.append ( me.m_formatter.format ( new Date () ) + " - " + message + "\r\n" );
    }

    public static String getLog () {
        return me.m_log.toString ();
    }

    public static String getHistory () {
        return me.m_buff.toString ();
    }

    public static void clearLog () {
        me.m_log = new StringBuffer ();
    }

    public static void clearHistory () {
        me.m_buff = new StringBuffer ();
    }

    public void onCompletion ( MediaPlayer arg0 ) {
        if ( m_currentWordQueue != null && m_currentWordQueue.size () > 0 ) {
            Ideogram w = JTApp.m_currentWordQueue.get ( 0 );
            JTApp.play ( w );
            m_currentWordQueue.remove ( 0 );
        } else {
            fireSpeechComplete ();
        }
    }

    private void fireSpeechComplete () {
        for ( ISpeechCompleteListener listener : JTApp.speechListeners ) {
            listener.SpeechComplete ();
        }
        if ( me.m_audioManager != null ) {
            me.m_audioManager.abandonAudioFocus ( me );
        }
    }

    /* Stops audio playback if incoming call is received */
    public void onAudioFocusChange ( int event ) {
        if ( event != AudioManager.AUDIOFOCUS_GAIN ) {
            stopPlayback ();
        }
    }

    // Stop audio playback
    public static void stopPlayback () {
        if ( me.m_player != null ) {
            me.m_player.stop ();
        }
        if ( me.m_audioManager != null ) {
            me.m_speechManager.stop ();
        }
        me.fireSpeechComplete ();
    }

    public static boolean isAudioPlaying () {
        return me.m_audioManager.isMusicActive () || me.m_speechManager.isSpeaking ();
    }

    public void onUtteranceCompleted ( String utteranceId ) {
        if ( m_currentWordQueue != null && m_currentWordQueue.size () > 0 ) {
            Ideogram w = JTApp.m_currentWordQueue.get ( 0 );
            JTApp.play ( w );
            m_currentWordQueue.remove ( 0 );
        } else {
            fireSpeechComplete ();
        }
    }

    public void onInit ( int status ) {
        if ( status == TextToSpeech.SUCCESS ) {
            m_speechEngineReady = true;
            m_speechManager.setOnUtteranceProgressListener(new UtteranceProgressListener() {
				
				@Override
				public void onStart(String utteranceId) {}
				
				@Override
				public void onError(String utteranceId) {}
				
				@Override
				public void onDone(String utteranceId) {
			        if ( m_currentWordQueue != null && m_currentWordQueue.size () > 0 ) {
			            Ideogram w = JTApp.m_currentWordQueue.get ( 0 );
			            JTApp.play ( w );
			            m_currentWordQueue.remove ( 0 );
			        } else {
			            fireSpeechComplete ();
			        }					
				}
			});
            if ( m_speechManager.isLanguageAvailable ( Locale.getDefault () ) != TextToSpeech.LANG_MISSING_DATA ) {
                setSpeechResourceFound ( true );
            }
            logMessage ( me.TAG, JTApp.LOG_SEVERITY_WARNING, "Text-to-Speech engine initialized." );
        } else {
            logMessage ( me.TAG, JTApp.LOG_SEVERITY_WARNING,
                    "Text-to-Speech engine failed to initialized properly." );
        }
    }

    public synchronized static String getNewFeaturesString () {

        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder ( 4096 );
        try {
            reader = new BufferedReader ( new InputStreamReader ( me.getResources ()
                    .openRawResource ( com.jabstone.jabtalk.basic.R.raw.new_features ) ), 32768 );
            String line = null;
            while ( ( line = reader.readLine () ) != null ) {
                builder.append ( line + System.getProperty ( "line.separator" ) );
            }

        } catch ( IOException io ) {
            // suppressed as this condition would only result from a bad
            // build
        } finally {
            try {
                if ( reader != null ) {
                    reader.close ();
                }
            } catch ( Exception ignore ) {
                // suppressed as we don't really care at this point if
                // the stream doesn't close properly
            }
        }

        return builder.toString ();
    }

}
