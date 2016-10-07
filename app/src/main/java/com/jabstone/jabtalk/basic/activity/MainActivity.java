package com.jabstone.jabtalk.basic.activity;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.Html;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.BounceInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.PictureSize;
import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.adapters.GridAdapter;
import com.jabstone.jabtalk.basic.listeners.ICategorySelectionListener;
import com.jabstone.jabtalk.basic.listeners.IDataStoreListener;
import com.jabstone.jabtalk.basic.listeners.ISpeechCompleteListener;
import com.jabstone.jabtalk.basic.listeners.IWordSelectionListener;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;
import com.jabstone.jabtalk.basic.widgets.AutoResizeTextView;
import com.jabstone.jabtalk.basic.widgets.IAutoResizeTextListener;
import com.jabstone.jabtalk.basic.widgets.IFrameResizeListener;
import com.jabstone.jabtalk.basic.widgets.JTLinearLayout;
import com.jabstone.jabtalk.basic.widgets.PictureFrameDimensions;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class MainActivity extends Activity implements ICategorySelectionListener,
        IWordSelectionListener, ISpeechCompleteListener, IDataStoreListener, IFrameResizeListener {

    private final int ACTIVITY_RESULT_MANAGE = 4000;
    private final int DIALOG_CHALLENGE = 2001;
    private final int DIALOG_NEW_FEATURES = 2002;
    String[] m_boardWords = null;
    private String TAG = MainActivity.class.getSimpleName();
    private int INCORRECT_THRESHOLD = 3;
    private String CATEGORY_ID = "cat_id";
    private String BOARD_WORDS = "board_words";
    private String m_selectedCategoryId = null;
    private String m_challenge = null;
    private boolean m_challenge_incorrect = false;
    private int m_incorrect_count = 0;
    private Ideogram m_currentlySpeaking = null;
    private GridView m_ideogramGrid;
    private LinearLayout m_emptyLayout;
    private RelativeLayout m_sentenceContainer;
    private AutoResizeTextView m_sentenceBoardTip;
    private GridAdapter m_gridAdapter = null;
    private ProgressDialog m_speakDialog = null;
    private LinearLayout m_sentenceBoard = null;
    private HorizontalScrollView m_sentenceScroll = null;
    private Handler m_handler = null;
    private TextView m_emptyText = null;
    private boolean m_isGridSized = false;
    private int m_gridWidth = 0;
    private float m_beginMove = 0;
    private PowerManager.WakeLock m_wakeLock;

    @Override
    public void onCreate ( Bundle savedInstanceState ) {
        super.onCreate ( savedInstanceState );
        m_handler = new Handler ();
        m_selectedCategoryId = JTApp.getDataStore().getRootCategory().getId();

        setContentView ( R.layout.main );
        setTitle ( getString ( R.string.app_name ) + " - v" + JTApp.getVersionName () );
        m_speakDialog = new ProgressDialog ( this );
        m_speakDialog.setCancelable ( false );
        m_speakDialog.setOnDismissListener ( new DialogInterface.OnDismissListener () {

            public void onDismiss ( DialogInterface dialog ) {
                JTApp.stopPlayback ();
            }
        } );

        m_ideogramGrid = ( GridView ) findViewById ( R.id.ideogramGrid );
        m_emptyLayout = (LinearLayout)findViewById(R.id.emptyWord);
        JTLinearLayout ideogramLayout = (JTLinearLayout) findViewById(R.id.ideogramLayout);
        m_sentenceContainer = ( RelativeLayout ) findViewById ( R.id.sentenceContainer );
        m_sentenceBoardTip = ( AutoResizeTextView ) findViewById ( R.id.sentenceBoardTip );
        m_gridAdapter = new GridAdapter( getApplicationContext () );
        m_sentenceScroll = ( HorizontalScrollView ) findViewById ( R.id.sentence_scroll );
        m_ideogramGrid.setAdapter ( m_gridAdapter );
        RelativeLayout navHome = (RelativeLayout) findViewById(R.id.navigationHome);
        RelativeLayout navBack = (RelativeLayout) findViewById(R.id.navigationBack);
        m_emptyText = (TextView)findViewById(R.id.emptyWordText);
        TextView websiteText = (TextView) findViewById(R.id.websiteText);
        Linkify.addLinks(websiteText, Linkify.WEB_URLS);

        // Setup listeners
        JTApp.addCategorySelectionListener ( this );
        JTApp.addWordSelectionListener ( this );
        JTApp.addSpeechCompleteListener ( this );
        JTApp.addDataStoreListener ( this );
        JTApp.addDataStoreListener ( m_gridAdapter );
        JTApp.addCategorySelectionListener(m_gridAdapter);

        // frame resize listener for resizing pictures correctly
        ideogramLayout.setOnFrameResizeListener(this);
        
        //Navigation listeners
        navBack.setOnClickListener(new OnClickListener() {
            @Override
			public void onClick(View v) {				
				navigateBackPressed();
			}
		});
        navHome.setOnClickListener(new OnClickListener() {
            @Override
			public void onClick(View v) {				
				navigateHomePressed();
			}
		});
        
        //empty text listener
        m_emptyText.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showManageActivity(getSelectedIdeogram().getId());
			}
		});
        
        //Get handle to power management wake lock...toggled in onResume and onPause methods.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
        m_wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "JABTalk");        

    }

    @Override
    protected void onSaveInstanceState ( Bundle outState ) {

        outState.putString ( CATEGORY_ID, getSelectedIdeogram().getId() );
        if(m_sentenceBoard != null) {
            List<String> boardWords = new ArrayList<>();
            for (int i = 0; i < m_sentenceBoard.getChildCount(); i++) {
                View picture = m_sentenceBoard.getChildAt(i);
                Ideogram w = (Ideogram) picture.getTag();
                if (w != null) {
                    boardWords.add(w.getId());
                }
            }
            if (boardWords.size() > 0) {
                m_boardWords = boardWords.toArray(new String[boardWords.size()]);
                outState.putStringArray(BOARD_WORDS, m_boardWords);
            }
        }
        super.onSaveInstanceState ( outState );
    }
    
    

    @Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
        // Restore activity state
        if ( savedInstanceState != null ) {
            m_selectedCategoryId = savedInstanceState.getString ( CATEGORY_ID );
            if(m_sentenceBoard != null) {
                m_boardWords = savedInstanceState.getStringArray(BOARD_WORDS);
            }
            JTApp.fireCategorySelected ( getSelectedIdeogram(), false );
        }     		
	}

	@Override
    protected void onDestroy () {
        JTApp.removeCategorySelectionListener ( this );
        JTApp.removeWordSelectionListener ( this );
        JTApp.removeSpeechCompleteListener ( this );
        JTApp.removeDataStoreListener ( this );
        JTApp.removeCategorySelectionListener ( m_gridAdapter );
        JTApp.removeDataStoreListener(m_gridAdapter);
        m_speakDialog.dismiss ();
        super.onDestroy ();
    }

    @Override
    protected void onPause () {
    	if(m_wakeLock.isHeld()) {
    		m_wakeLock.release();
    	}
        super.onPause ();
        m_speakDialog.dismiss ();
    }

    @Override
    public void onBackPressed () {
        boolean shouldExit = true;

        Ideogram gram = getSelectedIdeogram();
        if ( gram.getParentId () != null && gram.getParentId ().length () > 0 ) {
            Ideogram parent = JTApp.getDataStore ().getIdeogram ( gram.getParentId () );
            if ( parent != null ) {
            	shouldExit = false;
                JTApp.fireCategorySelected ( parent, false );
            }
        }
        
    	
    	if(shouldExit && !JTApp.isBackButtonDisabled()) {
    		this.finish();
    	}
    }

    @Override
    protected Dialog onCreateDialog ( int id ) {
        Dialog dialog = null;

        switch ( id ) {
            case DIALOG_CHALLENGE:
                AlertDialog.Builder builder = new AlertDialog.Builder ( this );
                LayoutInflater inflater = ( LayoutInflater ) getSystemService ( LAYOUT_INFLATER_SERVICE );
                final View layout = inflater.inflate ( R.layout.manage_challenge,
                        ( ViewGroup ) findViewById ( R.id.manage_challenge_layout ) );
                builder.setTitle ( R.string.dialog_title_manage_challenge );
                builder.setIcon ( R.drawable.ic_lock_idle_lock );
                builder.setView ( layout );
                builder.setCancelable ( true );
                final TextView wrong = ( TextView ) layout.findViewById ( R.id.lock_incorrect );
                Button cancel = ( Button ) layout.findViewById ( R.id.btn_cancel );
                cancel.setOnClickListener ( new View.OnClickListener () {

                    public void onClick ( View v ) {
                        dismissDialog ( DIALOG_CHALLENGE );
                    }
                } );
                Button unlock = ( Button ) layout.findViewById ( R.id.btn_unlock );
                unlock.setOnClickListener ( new View.OnClickListener () {

                    public void onClick ( View v ) {
                        EditText answer = ( EditText ) layout.findViewById ( R.id.lock_answer );
                        boolean result = unlockManageActivity ( answer.getText ().toString () );
                        if ( !result ) {
                            wrong.setVisibility ( View.VISIBLE );
                            answer.setText ( "" );
                            if ( m_incorrect_count >= INCORRECT_THRESHOLD ) {
                                resetChallengeState ();
                                dismissDialog ( DIALOG_CHALLENGE );
                            }
                        } else {
                            resetChallengeState ();
                            dismissDialog ( DIALOG_CHALLENGE );
                            showManageActivity ();
                        }
                    }
                } );
                dialog = builder.create ();

                break;
            case DIALOG_NEW_FEATURES:
                dialog = new Dialog ( this );
                dialog.setContentView ( R.layout.new_features_dialog );
                dialog.setTitle ( getString ( R.string.dialog_title_new_features ));
                TextView tv = ( TextView ) dialog.findViewById ( R.id.newFeatures );
                tv.setText ( Html.fromHtml ( JTApp.getNewFeaturesString () ) );
                Button ackButton = ( Button ) dialog.findViewById ( R.id.closeButton );
                ackButton.setOnClickListener ( new View.OnClickListener () {

                    public void onClick ( View v ) {
                        JTApp.setAcknowledgeNewFeatures ();
                        removeDialog ( DIALOG_NEW_FEATURES );
                    }
                } );
                break;
        }
        return dialog;
    }

    @Override
    protected void onPrepareDialog ( int id, Dialog dialog ) {
        super.onPrepareDialog ( id, dialog );
        switch ( id ) {
            case DIALOG_CHALLENGE:
                int x = generateNumber ();
                m_challenge = String.valueOf ( x );
                TextView text = ( TextView ) dialog.findViewById ( R.id.lock_message );
                text.setText ( String.format (
                        getString ( R.string.dialog_message_manage_unlock_question ), m_challenge ) );
                EditText answer = ( EditText ) dialog.findViewById ( R.id.lock_answer );
                answer.setText ( "" );
                TextView wrong = ( TextView ) dialog.findViewById ( R.id.lock_incorrect );
                if ( m_challenge_incorrect ) {
                    wrong.setVisibility ( View.VISIBLE );
                } else {
                    wrong.setVisibility ( View.GONE );
                }
                try {
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                } catch (NullPointerException ignored) {
                }
                break;
        }
    }

    @Override
    protected void onResume () {
        if(JTApp.isScreenWakeLockEnabled()) {
        	m_wakeLock.acquire();
        }
    	super.onResume ();

        if ( !JTApp.isAcknowledgeNewFeatures () ) {
            showDialog ( DIALOG_NEW_FEATURES );
        }
        if ( JTApp.isSentenceBuilderEnabled () ) {
            m_sentenceContainer.setVisibility ( View.VISIBLE );            
        } else {
            m_sentenceContainer.setVisibility ( View.GONE );
        }
        
        toggleGridView();
        
        // Setup scroll speed
        m_ideogramGrid.setFriction(JTApp.getScrollSpeed());
        
    }

    @Override
    protected void onActivityResult ( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult(requestCode, resultCode, data);		
		if(resultCode != RESULT_CANCELED) {
			switch(requestCode) {
				case ACTIVITY_RESULT_MANAGE:
					if(data != null && data.hasExtra(JTApp.INTENT_EXTRA_CLEAR_MANAGE_STACK)) {
						showManageActivity();
					} else {
				        finish ();
				        Intent i = new Intent ( this, MainActivity.class );
				        startActivity ( i );						
					}
			}
		} 

    }

    @Override
    public boolean onCreateOptionsMenu ( Menu menu ) {
        super.onCreateOptionsMenu ( menu );
        MenuInflater inflater = getMenuInflater ();
        inflater.inflate ( R.menu.menu, menu );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected ( MenuItem item ) {
        switch ( item.getItemId () ) {
            case R.id.menu_item_manage:
            	if(JTApp.isAccessCodeRequired()) {
            		showDialog ( DIALOG_CHALLENGE );
            	} else {
            		showManageActivity();
            	}
                return true;
            default:
                return true;
        }
    }

    @Override
    public void CategorySelected ( Ideogram category, boolean touchEvent ) {
        m_selectedCategoryId = category.getId ();
        if ( ( category.getAudioPath () != null || category.isSynthesizeButton () ) && touchEvent
                && JTApp.isCategoryAudioEnabled () ) {
        	m_currentlySpeaking = category;
            beginPlayIdeogram(category);          
        }
        toggleGridView();
    }

    @Override
    public void WordSelected ( Ideogram word, boolean isSentenceWord ) {
        if ( word.getAudioPath () != null || word.isSynthesizeButton () ) {
            if ( JTApp.isWordAudioEnabled () ) {
            	m_currentlySpeaking = word;
            	beginPlayIdeogram(word);
            }
        }
        
        if ( JTApp.isSentenceBuilderEnabled () && word.getType () == Type.Word
                && !isSentenceWord ) {
            View lastViewOnBoard = m_sentenceBoard.getChildAt ( m_sentenceBoard
                    .getChildCount () - 1 );
            Ideogram lastWord = lastViewOnBoard != null ? ( Ideogram ) lastViewOnBoard
                    .getTag () : null;
            if ( m_sentenceBoard.getChildCount () < JTApp.getSentenceWordLimitPreference ()
                    && ( lastWord == null || !lastWord.equals ( word ) ) ) {
                addWordToSentenceBoard ( word, true );                    
            }
        }        
    }

    @Override
    public void SpeechComplete () {
    	if(JTApp.isDisplayPhraseEnabled() || m_currentlySpeaking == null) {
	        runOnUiThread ( new Runnable () {
	            public void run () {
	                endPlayIdeogram();
	            }
	        } );
    	}
    }
    
    @Override
    public void DataStoreUpdated () {
        m_gridAdapter.notifyDataSetChanged ();
        m_selectedCategoryId = null;
        clearSentenceBoard ();
    }    
    
    @Override
    public void OnFrameResized ( int width, int height ) {
        if ( !m_isGridSized ) {
            m_isGridSized = true;
            m_gridWidth = width;
            setGridSize ();
            initializeSentenceBuilder ();
            if(m_boardWords != null && m_boardWords.length > 0) {
            	for(String id : m_boardWords) {
            		Ideogram g = JTApp.getDataStore().getIdeogram(id);
            		if(g != null) {
            			addWordToSentenceBoard(g, false);
            		}
            	}
            	m_boardWords = null;
            	m_sentenceScroll.fullScroll ( HorizontalScrollView.FOCUS_RIGHT );
            }            
        }
    }    
    
    private void toggleGridView() {
    	Ideogram selected = getSelectedIdeogram();
    	
        if( selected != null && selected.getParentId() == null && selected.getChildren(false).size() == 0) {    
        	m_emptyText.setText(R.string.empty_category);
        	m_emptyLayout.setVisibility(View.VISIBLE);
        	m_ideogramGrid.setVisibility(View.GONE);
        } else if(selected != null && selected.getParentId() != null && selected.getChildren(false).size() == 0){
        	m_emptyText.setText(R.string.empty_word);
        	m_emptyLayout.setVisibility(View.VISIBLE);
        	m_ideogramGrid.setVisibility(View.GONE);
        } else {
        	m_emptyLayout.setVisibility(View.GONE);
        	m_ideogramGrid.setVisibility(View.VISIBLE);        	
        }    	
    }
    
    private void beginPlayIdeogram(Ideogram gram) {        
        if(gram.getPhrase() != null && gram.getPhrase().trim().length() > 0) {
            if(JTApp.isDisplayPhraseEnabled() ) {
            	m_speakDialog.setMessage ( gram.getPhrase () );
            	m_speakDialog.show ();
            }
            JTApp.play ( gram );
        }
        
        if(!JTApp.isDisplayPhraseEnabled()) {
        	endPlayIdeogram();
        }    	
    }
    
    private void endPlayIdeogram() {
        m_speakDialog.dismiss ();               	
    }

    private void startAnimation () {
        RelativeLayout v = ( RelativeLayout ) m_sentenceBoard.getChildAt ( m_sentenceBoard
                .getChildCount () - 1 );
        float x = getResources ().getDimension ( R.dimen.sentence_frame_width ) / 2;
        Animation animation = new TranslateAnimation ( x, 0f, 0.0f, 0.0f );
        animation.setDuration ( 1000 );
        animation.setInterpolator ( new BounceInterpolator () );
        animation.setAnimationListener(new AnimationListener() {
			
			@Override
			public void onAnimationStart(Animation animation) {
				m_sentenceScroll.fullScroll ( HorizontalScrollView.FOCUS_RIGHT );				
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {			
			}
		});
        v.startAnimation ( animation );
        

    }

    private void removeSentenceWordView ( final View v ) {

        float from = 1.0f;
        float to = 0.0f;

        Animation animation3 = new AlphaAnimation ( from, to );
        animation3.setDuration ( 500 );
        animation3.setAnimationListener ( new AnimationListener () {

            public void onAnimationStart ( Animation animation ) {
            }

            public void onAnimationRepeat ( Animation animation ) {
            }

            public void onAnimationEnd ( Animation animation ) {
                m_handler.post ( new Runnable () {

                    public void run () {
                        m_sentenceBoard.removeView ( v );
                        if ( m_sentenceBoard.getChildCount () < 1 ) {
                            toggleSentenceTip ( true );
                        }
                    }
                } );
            }
        } );
        v.startAnimation ( animation3 );
    }

    private boolean unlockManageActivity ( String answer ) {
        m_incorrect_count++;
        return answer.equals(m_challenge);
    }

    private void resetChallengeState () {
        m_challenge_incorrect = false;
        m_incorrect_count = 0;
    }

    private int generateNumber () {
        int x = 0;
        while ( x < 1000 || x > 9999 ) {
            x = ( int ) ( Math.random () * 9999 ) + 1000;
        }
        return x;
    }

    private void showManageActivity () {
    	showManageActivity(JTApp.getDataStore().getRootCategory().getId());
    }
    
    private void showManageActivity(String id) {
        JTApp.getDataStore ().clearCache ();
        Intent intent = new Intent ( this, ManageActivity.class );
        intent.putExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID, id);
        intent.putExtra(JTApp.INTENT_EXTRA_CALLED_FROM_MAIN, true);
        startActivityForResult ( intent, ACTIVITY_RESULT_MANAGE );    	
    }

    private void initializeSentenceBuilder () {
        int containerHeight = JTApp.getPictureDimensions ( PictureSize.SentencePicture, false )
                .getHeight () + 10;
        m_sentenceContainer.getLayoutParams ().height = containerHeight;

        m_sentenceBoard = ( LinearLayout ) findViewById ( R.id.sentenceBoard );       
        
        RelativeLayout clearButton = getSentenceButtonLayout ( getString(R.string.button_clear), R.drawable.clear );
        RelativeLayout speakButton = getSentenceButtonLayout ( getString(R.string.button_speak), R.drawable.go );

        LinearLayout sentenceButtonsLayout = ( LinearLayout ) findViewById ( R.id.sentence_buttons );
        sentenceButtonsLayout.addView ( speakButton );
        sentenceButtonsLayout.addView ( clearButton );  
        
        m_sentenceBoardTip.setOnAutoResizeTextListener(new IAutoResizeTextListener() {
			
			public void onAutoResizeLayoutChanged(int width, int height) {
				m_sentenceBoardTip.setTextSize(26f);
				m_sentenceBoardTip.resizeText(width, height);
			}
		});
        
        clearButton.setOnClickListener ( new View.OnClickListener () {

            public void onClick ( View v ) {
                v.performHapticFeedback ( HapticFeedbackConstants.LONG_PRESS );
                clearSentenceBoard ();
                toggleSentenceTip ( true );
            }
        } );

        speakButton.setOnClickListener ( new View.OnClickListener () {

            public void onClick ( View v ) {
                v.performHapticFeedback ( HapticFeedbackConstants.LONG_PRESS );
                readSentenceBoard ();
            }
        } );
    }

    private RelativeLayout getSentenceButtonLayout ( String text, int iconId) {

        RelativeLayout pictureView;
        pictureView = new RelativeLayout ( this );
        pictureView.setClickable ( true );
        PictureFrameDimensions desiredSize = JTApp.getPictureDimensions (
                PictureSize.SentencePicture, false );
        LinearLayout.LayoutParams pictureLayout = new LinearLayout.LayoutParams (
                desiredSize.getWidth (), desiredSize.getHeight () );

        pictureLayout.rightMargin = 10;
        pictureView.setLayoutParams ( pictureLayout );
        pictureView.setBackgroundDrawable ( getResources ().getDrawable (
                com.jabstone.jabtalk.basic.R.drawable.sentence_action_background ) );

        MarginLayoutParams ivParams = new MarginLayoutParams ( 200, 150 );
        ivParams.setMargins ( 0, 0, 0, 0 );
        RelativeLayout.LayoutParams ivLayout = new RelativeLayout.LayoutParams ( ivParams );
        ImageView iv = new ImageView ( this );
        iv.setImageDrawable( getResources().getDrawable(iconId) );
        iv.setId(R.id.IMAGEVIEW_ID);
        iv.setScaleType ( ScaleType.FIT_CENTER );        
        ivLayout.addRule ( RelativeLayout.CENTER_IN_PARENT );
        pictureView.addView ( iv, ivLayout );
        return pictureView;
    }

    private void toggleSentenceTip ( boolean showTip ) {
        if ( showTip ) {
        	m_sentenceScroll.setVisibility ( View.GONE );
        	m_sentenceBoardTip.setVisibility ( View.VISIBLE );            
        } else {
            m_sentenceBoardTip.setVisibility ( View.GONE );
            m_sentenceScroll.setVisibility ( View.VISIBLE );
        }
    }

    private void addWordToSentenceBoard ( Ideogram word, boolean animate ) {

        toggleSentenceTip ( false );

        RelativeLayout pictureView = JTApp.getPictureLayout ( this, Type.Word,
                PictureSize.SentencePicture, word.isTextButton () );
        Bitmap jpg = word.getImage ();
        ImageView picture = (ImageView) pictureView.findViewById(R.id.IMAGEVIEW_ID);
        AutoResizeTextView title = ( AutoResizeTextView ) pictureView
                .findViewById(R.id.TEXTVIEW_ID);
        picture.setImageBitmap ( jpg );
        title.setText ( word.getLabel () );
      
        //Adjust font size on sentence bar words
        int fsize = (int)JTApp.getTitleFontSize();
        float sbsize = JTApp.getSentencePictureSizePreference();
        int ffsize = 10;
        switch(fsize) {
	        case 10:
	        	ffsize = 9;
	        	break;
	        case 13:
	        	ffsize = 11;
	        	break;
	        case 15:
	        	ffsize = 12;
	        	break;
	        case 18:
	        	ffsize = 13;
	        	break;
	        case 20:
	        	ffsize = 14;
	        	break;
	        case 25:
	        	ffsize = 15;
	        	break;
	        case 30:
	        	ffsize = 16;
	        	break;
        }
        
        if(sbsize < .60f) {
        	ffsize--;
        }
        if(sbsize > .60f) {
        	ffsize++;
        }
        
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ffsize);
        if ( word.isTextButton () ) {
            PictureFrameDimensions dm = JTApp.getPictureDimensions ( PictureSize.SentencePicture,
                    true );
            title.resizeText ( dm.getWidth (), dm.getHeight () );
        }
        pictureView.setTag ( word );
        final Ideogram gram = word;
        pictureView.setOnClickListener ( new View.OnClickListener () {

            public void onClick ( View v ) {
                v.performHapticFeedback ( HapticFeedbackConstants.LONG_PRESS );
                JTApp.fireWordSelected ( gram, true );
            }
        } );

        pictureView.setOnTouchListener ( new View.OnTouchListener () {

            public boolean onTouch ( View v, MotionEvent event ) {

                if ( event.getAction () == MotionEvent.ACTION_DOWN ) {
                    m_beginMove = event.getY ();
                }
                if ( event.getAction () == MotionEvent.ACTION_UP
                        || event.getAction () == MotionEvent.ACTION_CANCEL ) {
                    if ( ( event.getY () - m_beginMove ) > 100 ) {
                        removeSentenceWordView ( v );
                        return true;
                    }
                }

                return false;
            }
        } );
        m_sentenceBoard.addView ( pictureView );
        if(animate) {
        	startAnimation ();
        }
    }

    private void readSentenceBoard () {
        StringBuilder buff = new StringBuilder();
        if ( m_sentenceBoard.getChildCount () > 0 ) {
            List<Ideogram> wordList = new LinkedList<>();

            for ( int i = 0; i < m_sentenceBoard.getChildCount (); i++ ) {
                View picture = m_sentenceBoard.getChildAt(i);
                Ideogram w = ( Ideogram ) picture.getTag ();
                if ( w != null ) {
                    buff.append ( i > 0 ? w.getPhrase ().toLowerCase () + " " : w.getPhrase ()
                            + " " );
                    wordList.add ( w );
                }
            }
            m_speakDialog.setMessage ( buff.toString () );
            m_speakDialog.show ();
            m_currentlySpeaking = null;
            JTApp.play ( wordList );
        } else {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Could not find audio files for sentend: " + buff.toString () );
        }
    }

    private void clearSentenceBoard () {
        // Remove all other words from sentence board
        if ( JTApp.isSentenceBuilderEnabled () && m_sentenceBoard != null) {
            m_sentenceBoard.removeAllViews ();
        }
    }    

    private void setGridSize () {
        int handleWidth = getResources ().getDimensionPixelSize ( R.dimen.navigationButtonSize );
        DisplayMetrics displaymetrics = new DisplayMetrics ();
        getWindowManager ().getDefaultDisplay ().getMetrics ( displaymetrics );
        int sheight = displaymetrics.heightPixels;
        int swidth = displaymetrics.widthPixels;
        boolean isLandscape = swidth > sheight;
        float pictureWidthHeightRatio = .92f;
        int hspacing = 10;
        int vspacing = 10;
        int tpadding = 10;
        int rpadding = 0;
        int viewWidth = m_gridWidth;
        int columns = JTApp.getPicturesPerRow ( isLandscape );

        // If we have never selected a preferred grid size, determine number of
        // columns based on default pictures width
        if ( columns == 0 ) {
            if ( isLandscape ) {
                viewWidth = m_gridWidth - handleWidth;
            }
            int defaultPictureWidth = getResources ().getDimensionPixelSize (
                    R.dimen.picture_frame_width_default );
            columns = ( int ) Math.floor ( viewWidth / defaultPictureWidth );
            if ( columns == 0 ) {
                columns = 4;
            }
            JTApp.setPicturesPerRow ( columns, isLandscape );
        }

        int lpadding = 10;
        viewWidth = m_gridWidth - ( hspacing * ( columns + 1 ) ) - 5;         

        int desiredPictureWidth = ( int ) Math.floor ( viewWidth / columns );
        int desiredPictureHeight = ( int ) Math.floor ( desiredPictureWidth
                * pictureWidthHeightRatio );
        JTApp.setPictureDimensions ( desiredPictureWidth, desiredPictureHeight );

        m_ideogramGrid.setHorizontalSpacing ( hspacing );
        m_ideogramGrid.setVerticalSpacing ( vspacing );
        m_ideogramGrid.setPadding ( lpadding, tpadding, rpadding, 5 );
        m_ideogramGrid.setNumColumns ( columns );
        m_ideogramGrid.setColumnWidth ( desiredPictureWidth );

    }
    
    private void navigateBackPressed() {

        Ideogram gram = getSelectedIdeogram();
        if ( gram != null && gram.getParentId () != null && gram.getParentId ().length () > 0 ) {
            Ideogram parent = JTApp.getDataStore ().getIdeogram ( gram.getParentId () );
            if ( parent != null ) {
                JTApp.fireCategorySelected ( parent, false );
            }
        } else {
        	navigateHomePressed();
        }        
    }
    
    private void navigateHomePressed() {
    	if(!getSelectedIdeogram().equals(JTApp.getDataStore().getRootCategory())) {
    		JTApp.fireCategorySelected(JTApp.getDataStore().getRootCategory(), false);
    	}
    }    
    
    private Ideogram getSelectedIdeogram() {
    	Ideogram g = JTApp.getDataStore().getIdeogram(m_selectedCategoryId);
    	if(g != null && g.getType() == Type.Category) {
    		return g;
    	} else {
    		return JTApp.getDataStore().getRootCategory();
    	}
    }

}