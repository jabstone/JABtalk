package com.jabstone.jabtalk.basic.activity;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.PictureSize;
import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.audio.JTAudioRecorder;
import com.jabstone.jabtalk.basic.exceptions.JabException;
import com.jabstone.jabtalk.basic.listeners.ISpeechCompleteListener;
import com.jabstone.jabtalk.basic.storage.DataStore;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;
import com.jabstone.jabtalk.basic.widgets.AutoResizeTextView;
import com.jabstone.jabtalk.basic.widgets.PictureFrameDimensions;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;


public class EditIdeogramActivity extends Activity implements OnCancelListener,
        ISpeechCompleteListener {

    private final int DIALOG_IMAGE_SOURCE = 1;
    private final int DIALOG_AUDIO_SOURCE = 2;
    private final int DIALOG_AUDIO_RECORD = 3;
    private final int DIALOG_DELETE_CONFIRMATION = 4;
    private final int DIALOG_GENERIC = 5;
    private final int DIALOG_SPEECH_DATA_NOT_FOUND = 6;
    private final int DIALOG_EXPLAIN_AUDIO_PERMISSIONS = 7;
    private final int DIALOG_EXPLAIN_CAMERA_PERMISSIONS = 8;
    private final int SOURCE_GALLERY = 0;
    private final int SOURCE_WEB = 1;
    private final int SOURCE_TEXT = 2;
    private final int SOURCE_CAMERA = 3;
    private final int SOURCE_NO_AUDIO = 1;
    private final int SOURCE_IMPORT_AUDIO = 2;
    private final int SOURCE_RECORDER = 4;
    private final int SOURCE_SYNTHESIZER = 8;
    private final int ACTIVITY_RESULT_CAMERA = 1000;
    private final int ACTIVITY_RESULT_GALLERY = 1001;
    private final int ACTIVITY_RESULT_WEB = 1002;
    private final int ACTIVITY_RESULT_MUSIC = 1003;
    private final int ACTIVITY_RESULT_SPEECH_DATA = 1004;
    private final int ACTIVITY_RESULT_RECORD_AUDIO = 1005;
    private final int AUDIO_PERMISSIONS = 5000;
    private final int CAMERA_PERMISSIONS = 5001;
    private final String STATE_IMAGE = "tempimage";
    private final String STATE_AUDIO = "tempaudio";
    private final String STATE_IDEOGRAM = "ideogram";
    private final String STATE_IDEOGRAM_ID = "parentId";
    private String TAG = EditIdeogramActivity.class.getSimpleName();
    private ProgressDialog progressDialog = null;
    private int m_audio_bitmask = 0;
    private Ideogram m_ideogram = null;
    private EditText m_label = null;
    private EditText m_phrase = null;
    private CheckBox m_hidden = null;
    private RelativeLayout m_previewContainer = null;
    private ImageButton m_cameraButton = null;
    private ImageButton m_audioButton = null;

    private boolean isRecording = false;

    private JTAudioRecorder m_recorder = null;
    private ProgressDialog m_speakDialog = null;
    private Dialog m_recordDialog = null;
    private SaveDataStoreTask saveTask = null;

    private File tempAudio = null;
    private File tempImage = null;

    public void onCancel(DialogInterface dialog) {
        if (isRecording) {
            stopRecording();
        }
    }

    public void SpeechComplete() {
        runOnUiThread(new Runnable() {

            public void run() {
                m_speakDialog.dismiss();
                RelativeLayout preview = (RelativeLayout) m_previewContainer
                        .findViewById(R.id.RELATIVELAYOUT_ID);
                if (m_ideogram.getType() == Type.Word) {
                    preview.setBackgroundDrawable(getResources().getDrawable(
                            R.drawable.word_background));
                } else {
                    preview.setBackgroundDrawable(getResources().getDrawable(
                            R.drawable.category_background));
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_ideogram);

        String ideogramId = getIntent().getStringExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);

        m_speakDialog = new ProgressDialog(this);
        m_speakDialog.setCancelable(true);
        m_speakDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {

            public void onDismiss(DialogInterface dialog) {
                JTApp.stopPlayback();
            }
        });

        m_label = (EditText) findViewById(R.id.edit_label);
        m_phrase = (EditText) findViewById(R.id.edit_phrase);
        m_previewContainer = (RelativeLayout) findViewById(R.id.edit_preview_container);
        m_hidden = (CheckBox) findViewById(R.id.check_hidden);
        Button cancelButton = (Button) findViewById(R.id.edit_ideogram_cancel);
        Button saveButton = (Button) findViewById(R.id.edit_ideogram_save);
        Button deleteButton = (Button) findViewById(R.id.edit_ideogram_delete);

        if (getIntent().getAction().equals(Intent.ACTION_INSERT)) {
            Type type = (Type) getIntent().getSerializableExtra(JTApp.INTENT_EXTRA_TYPE);
            m_ideogram = new Ideogram(type);
            deleteButton.setVisibility(View.GONE);
            m_ideogram.setId(UUID.randomUUID().toString());
            m_ideogram.setParentId(ideogramId);
        } else {
            Ideogram gram = JTApp.getDataStore().getIdeogram(ideogramId);
            m_ideogram = new Ideogram(gram);
            m_label.setText(m_ideogram.getLabel());
            m_phrase.setText(m_ideogram.getPhrase());
            m_hidden.setChecked(m_ideogram.isHidden());
        }

        createPreviewContainer(m_ideogram.isTextButton());

        JTApp.addSpeechCompleteListener(this);

        // Setup image capture button
        m_cameraButton = (ImageButton) findViewById(R.id.edit_image);
        m_cameraButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showDialog(DIALOG_IMAGE_SOURCE);
            }
        });

        // Setup audio capture button
        m_audioButton = (ImageButton) findViewById(R.id.edit_audio);
        m_audioButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showDialog(DIALOG_AUDIO_SOURCE);
            }
        });

        // Setup cancel button
        cancelButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                exitActivity();
            }
        });

        // Setup save button
        saveButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                try {
                    saveIdeogram();
                } catch (JabException e) {
                    JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_save_results));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
            }
        });

        // Setup delete button
        deleteButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showDialog(DIALOG_DELETE_CONFIRMATION);
            }
        });

        // Setup Keyboard listener for Phrase
        m_phrase.addTextChangedListener(new TextWatcher() {

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
                m_ideogram.setPhrase(s.toString());
            }
        });

        m_hidden.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                m_ideogram.setHidden(m_hidden.isChecked());
            }
        });

        // Check to see if speech resource is found
        if (!JTApp.isSpeechResourceFound() && JTApp.isNetworkConnected()) {
            checkForSpeechData();
        }

        restoreProgressDialog();
    }


    @Override
    protected void onResume() {
        super.onResume();

        Type type = (Type) getIntent().getSerializableExtra(JTApp.INTENT_EXTRA_TYPE);
        RelativeLayout previewLayout = (RelativeLayout) m_previewContainer
                .findViewById(R.id.RELATIVELAYOUT_ID);
        switch (type) {
            case Category:
                previewLayout.setBackgroundResource(R.drawable.category_background);
                m_previewContainer.setBackgroundResource(R.drawable.ideogram_preview);
                m_label.setHint(R.string.ideogram_activity_category_label_hint);
                break;
            case Word:
                previewLayout.setBackgroundResource(R.drawable.word_background);
                m_previewContainer.setBackgroundResource(R.drawable.ideogram_preview);
                m_label.setHint(R.string.ideogram_activity_word_label_hint);
                break;
        }

        // Set Activity Title
        if (getIntent().getAction().equals(Intent.ACTION_INSERT)) {
            switch (type) {
                case Category:
                    setTitle(R.string.ideogram_activity_title_insert_category);
                    break;
                case Word:
                    setTitle(R.string.ideogram_activity_title_insert_word);
                    break;
            }
        } else {
            switch (type) {
                case Category:
                    setTitle(R.string.ideogram_activity_title_update_category);
                    break;
                case Word:
                    setTitle(R.string.ideogram_activity_title_update_word);
                    break;
            }
        }

        toggleImageButtons();
        restoreProgressDialog();
    }

    private void checkForSpeechData() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, ACTIVITY_RESULT_SPEECH_DATA);
    }

    private void restoreProgressDialog() {
        if (saveTask != null && saveTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressDialog.setMessage(getString(R.string.dialog_message_saving));
            progressDialog.show();
        }
    }

    private void toggleImageButtons() {
//        if ( ( m_ideogram.getImageExtention () != null && m_ideogram.getImageExtention ().length () > 0 )
//                || tempImage != null ) {
//            m_cameraButton.setCompoundDrawablesWithIntrinsicBounds ( null, getResources ()
//                    .getDrawable ( R.drawable.camera_on ), null, null );
//        } else {
//            m_cameraButton.setCompoundDrawablesWithIntrinsicBounds ( null, getResources ()
//                    .getDrawable ( R.drawable.camera_off ), null, null );
//        }
//
//        if ( ( m_ideogram.getAudioExtention () != null && m_ideogram.getAudioExtention ().length () > 0 )
//                || tempAudio != null ) {
//            m_audioButton.setCompoundDrawablesWithIntrinsicBounds ( null, getResources ()
//                    .getDrawable ( R.drawable.ic_microphone_on ), null, null );
//        } else {
//            m_audioButton.setCompoundDrawablesWithIntrinsicBounds ( null, getResources ()
//                    .getDrawable ( R.drawable.ic_microphone_off ), null, null );
//        }
    }

    private void requestAudioPermissions(boolean suppressRationalDialog) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!suppressRationalDialog && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                showDialog(DIALOG_EXPLAIN_AUDIO_PERMISSIONS);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSIONS);
            }
        } else {
            showDialog(DIALOG_AUDIO_RECORD);
        }
    }

    private void requestCameraPermissions(boolean suppressRationalDialog) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!suppressRationalDialog && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                showDialog(DIALOG_EXPLAIN_CAMERA_PERMISSIONS);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSIONS);
            }
        } else {
            getCameraImage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case AUDIO_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showDialog(DIALOG_AUDIO_RECORD);
                }
                break;
            case CAMERA_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCameraImage();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        JTApp.removeSpeechCompleteListener(this);
    }

    private void exitActivity() {
        finish();
    }

    @Override
    public void onBackPressed() {
        exitActivity();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog alert = null;
        AlertDialog.Builder builder;
        EditIdeogramActivity activity = this;
        switch (id) {
            case DIALOG_IMAGE_SOURCE:
                final CharSequence[] imageSource = JTApp.isCameraAvailable() ? new CharSequence[4]
                        : new CharSequence[3];
                imageSource[SOURCE_GALLERY] = getString(R.string.dialog_item_import);
                imageSource[SOURCE_WEB] = getString(R.string.dialog_item_import_web);
                imageSource[SOURCE_TEXT] = getString(R.string.dialog_item_imagesource_text);
                if (JTApp.isCameraAvailable()) {
                    imageSource[SOURCE_CAMERA] = getString(R.string.dialog_item_imagesource_camera);
                }

                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_image_source));
                builder.setItems(imageSource, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int item) {
                        getImage(item);
                    }
                });
                alert = builder.create();
                break;
            case DIALOG_AUDIO_SOURCE:
                m_audio_bitmask = SOURCE_NO_AUDIO | SOURCE_IMPORT_AUDIO;
                int sourceCount = 2;

                if (JTApp.isMicrophoneAvailable()) {
                    m_audio_bitmask = m_audio_bitmask | SOURCE_RECORDER;
                    sourceCount++;
                }
                if (JTApp.isSpeechResourceFound()) {
                    m_audio_bitmask = m_audio_bitmask | SOURCE_SYNTHESIZER;
                    sourceCount++;
                }

                final CharSequence[] audioSource = new CharSequence[sourceCount];
                if (JTApp.isSpeechResourceFound()) {
                    audioSource[--sourceCount] = getString(R.string.dialog_item_audiosource_synthesizer);
                }
                if (JTApp.isMicrophoneAvailable()) {
                    audioSource[--sourceCount] = getString(R.string.dialog_item_audiosource_recorder);
                }
                audioSource[--sourceCount] = getString(R.string.dialog_item_import);
                audioSource[--sourceCount] = getString(R.string.dialog_item_no_audio);

                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_audio_source));
                builder.setItems(audioSource, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int item) {
                        getAudio(item);
                    }
                });
                alert = builder.create();
                break;
            case DIALOG_AUDIO_RECORD:
                alert = new Dialog(activity);
                m_recordDialog = alert;
                alert.setCancelable(true);
                alert.setOnCancelListener(activity);
                alert.setContentView(R.layout.audio_recorder);
                final Button action = (Button) alert.findViewById(R.id.recorder_action);
                action.setOnClickListener(new View.OnClickListener() {

                    public void onClick(View v) {
                        if (isRecording) {
                            stopRecording();
                            action.setText(R.string.button_start_recording);
                        } else {
                            startRecording();
                            action.setText(R.string.button_stop_recording);
                        }
                    }
                });
                break;
            case DIALOG_DELETE_CONFIRMATION:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_delete_confirmation));
                if (m_ideogram.getType().equals(Type.Word)) {
                    builder.setMessage(getString(R.string.dialog_message_delete_word));
                } else {
                    builder.setMessage(getString(R.string.dialog_message_delete_category));
                }
                builder.setPositiveButton(R.string.button_yes,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    deleteIdeogram();
                                } catch (JabException je) {
                                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                                            getString(R.string.dialog_title_error));
                                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                                            je.getMessage());
                                    showDialog(DIALOG_GENERIC);
                                }
                            }
                        });
                builder.setNegativeButton(R.string.button_no,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_DELETE_CONFIRMATION);
                            }
                        });
                alert = builder.create();
                break;
            case DIALOG_SPEECH_DATA_NOT_FOUND:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_missing_speech_data));
                builder.setMessage(getString(R.string.dialog_message_missing_speech_data));
                builder.setPositiveButton(R.string.button_yes,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                exitActivity();
                                Intent installIntent = new Intent();
                                installIntent
                                        .setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                                startActivity(installIntent);
                            }
                        });
                builder.setNegativeButton(R.string.button_no,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                m_ideogram.setAudioExtention(null);
                                toggleImageButtons();
                                dismissDialog(DIALOG_SPEECH_DATA_NOT_FOUND);
                            }
                        });
                alert = builder.create();
                break;
            case DIALOG_GENERIC:
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
                            }
                        });
                alert = builder.create();
                break;
            case DIALOG_EXPLAIN_AUDIO_PERMISSIONS:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_permission_explanation_title));
                builder.setMessage(getString(R.string.dialog_permission_explanation_audio_message));
                builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dismissDialog(DIALOG_EXPLAIN_AUDIO_PERMISSIONS);
                        requestAudioPermissions(true);
                    }
                });
                alert = builder.create();
                break;
            case DIALOG_EXPLAIN_CAMERA_PERMISSIONS:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_permission_explanation_title));
                builder.setMessage(getString(R.string.dialog_permission_explanation_camera_message));
                builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dismissDialog(DIALOG_EXPLAIN_CAMERA_PERMISSIONS);
                        requestCameraPermissions(true);
                    }
                });
                alert = builder.create();
                break;
        }
        return alert;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        switch (id) {
            case DIALOG_GENERIC:
                dialog.setTitle(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_TITLE));
                ((AlertDialog) dialog).setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE));
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTIVITY_RESULT_CAMERA:
                if (resultCode != RESULT_OK) {
                    resetTempImage();
                    break;
                }
                if (tempImage == null || !tempImage.exists()) {
                    try {
                        Thread.sleep(2000);
                    } catch (Exception ignored) {
                    }
                }
                try {
                    if (tempImage != null && isImageFileValid(tempImage.getAbsolutePath())) {
                        createPreviewContainer(false);
                        resizePhoto(tempImage.getAbsolutePath());
                    } else {
                        throw new JabException(
                                getString(R.string.dialog_message_invald_image_format));
                    }
                } catch (JabException je) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_error));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, je.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
            case ACTIVITY_RESULT_GALLERY:
                if (resultCode != RESULT_OK) {
                    resetTempImage();
                    break;
                }

                try {
                    Uri selectedImageURI = data.getData();
                    InputStream is = getContentResolver().openInputStream(selectedImageURI);
                    Bitmap bm = BitmapFactory.decodeStream(is, null, null);
                    tempImage = new File(JTApp.getDataStore().getTempDirectory(),
                            m_ideogram.getId() + ".jpg");
                    JTApp.getDataStore().saveScaledImage(tempImage, bm);

                    if (tempImage != null) {
                        createPreviewContainer(false);
                        resizePhoto(tempImage.getAbsolutePath());
                    } else {
                        throw new JabException(
                                getString(R.string.dialog_message_invald_image_format));
                    }
                } catch (Exception e) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_error));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
                    showDialog(DIALOG_GENERIC);
                }

                break;
            case ACTIVITY_RESULT_WEB:
                if (resultCode != RESULT_OK) {
                    resetTempImage();
                    break;
                }
                Uri fileUri = data.getData();
                String webFile = fileUri.getPath();

                try {
                    if (webFile != null && isImageFileValid(webFile)) {
                        createPreviewContainer(false);
                        resizePhoto(webFile);
                    } else {
                        throw new JabException(
                                getString(R.string.dialog_message_invald_image_format));
                    }
                } catch (JabException je) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_error));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, je.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
            case ACTIVITY_RESULT_MUSIC:
                if (resultCode != RESULT_OK) {
                    resetTempAudio();
                    break;
                }

                Uri audioUri = data.getData();
                String audioPath;
                String filePath = audioUri.getPath();

                String[] audioProj = {MediaStore.Audio.Media.DATA};
                Cursor audioCursor = managedQuery(audioUri, audioProj, null, null, null);
                if (audioCursor != null) {
                    int audio_col_index = audioCursor
                            .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                    audioCursor.moveToFirst();
                    audioPath = audioCursor.getString(audio_col_index);
                } else {
                    audioPath = filePath;
                }
                try {
                    if (audioPath != null && isAudioFileValid(audioPath)) {
                        tempAudio = new File(audioPath);
                        if (!tempAudio.exists() || tempAudio.length() < 1) {
                            tempAudio = null;
                        }
                    } else {
                        throw new JabException(
                                getString(R.string.dialog_message_invald_audio_format));
                    }
                } catch (JabException je) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_error));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, je.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
            case ACTIVITY_RESULT_SPEECH_DATA:
                if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    JTApp.setSpeechResourceFound(true);
                    JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO,
                            "Text-to-Speech language data found.");
                } else {
                    JTApp.setSpeechResourceFound(false);
                    JTApp.logMessage(
                            TAG,
                            JTApp.LOG_SEVERITY_INFO,
                            "Text-to-Speech language data not found. Speech synthesizer will not work correctly until language files are installed on device.");
                }
                break;
            case ACTIVITY_RESULT_RECORD_AUDIO:
                if (resultCode != RESULT_OK) {
                    resetTempAudio();
                    break;
                }
                try {
                    String fp = getRealPathFromURI(data.getData());

                    if (fp != null && isAudioFileValid(fp)) {
                        // Copy audio recording to cache directory and cleanup
                        // recording file
                        tempAudio = new File(fp);
                        File ta = new File(JTApp.getDataStore().getTempDirectory(),
                                m_ideogram.getId()
                                        + getFileExtention(tempAudio.getName(), true));
                        JTApp.getDataStore().copyFile(tempAudio, ta);
                        tempAudio.delete();
                        tempAudio = ta;
                        if (!tempAudio.exists() || tempAudio.length() < 1) {
                            tempAudio = null;
                        }
                    } else {
                        throw new JabException(
                                getString(R.string.dialog_message_invald_audio_format));
                    }
                } catch (JabException je) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_error));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, je.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
        }
    }

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = managedQuery(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    private void resetTempAudio() {
        if (tempAudio != null) {
            if (tempAudio.exists()) {
                tempAudio.delete();
            }
        }
        tempAudio = null;
    }

    private void resetTempImage() {
        if (tempImage != null) {
            if (tempImage.exists()) {
                tempImage.delete();
            }
        }
        tempImage = null;
    }

    private void getCameraImage() {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        tempImage = new File(JTApp.getDataStore().getTempDirectory(),
                m_ideogram.getId() + ".jpg");
        Uri outputUri = Uri.fromFile(tempImage);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        startActivityForResult(cameraIntent, ACTIVITY_RESULT_CAMERA);
    }

    private void getImage(int item) {
        switch (item) {
            case SOURCE_CAMERA:
                requestCameraPermissions(false);
                break;
            case SOURCE_GALLERY:
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, ACTIVITY_RESULT_GALLERY);
                break;
            case SOURCE_WEB:
                Intent webIntent = new Intent(this, BrowserActivity.class);
                String url = m_label.getText().toString();
                try {
                    url = URLEncoder.encode(url, "UTF-8");
                } catch (UnsupportedEncodingException ignored) {
                }
                webIntent.putExtra(JTApp.INTENT_EXTRA_SEARCH_TERM, url);
                startActivityForResult(webIntent, ACTIVITY_RESULT_WEB);
                break;
            case SOURCE_TEXT:
                m_ideogram.setImageExtention(JTApp.EXTENSION_TEXT_IMAGE);
                tempImage = null;
                createPreviewContainer(true);
                ImageView imgView = (ImageView) m_previewContainer
                        .findViewById(R.id.IMAGEVIEW_ID);
                imgView.setImageDrawable(getResources().getDrawable(R.drawable.chalkboard));
                toggleImageButtons();
                break;
        }
    }

    private void getAudio(int item) {
        switch (item) {
            case 0:
                resetTempAudio();
                m_ideogram.setAudioExtention(null);
                break;
            case 1:
                Intent galleryIntent = new Intent();
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                galleryIntent.setType("audio/*");
                startActivityForResult(Intent.createChooser(galleryIntent,
                        getString(R.string.dialog_title_audio_source)), ACTIVITY_RESULT_MUSIC);
                break;
            case 2:
                if ((m_audio_bitmask & SOURCE_RECORDER) == SOURCE_RECORDER) {
                    requestAudioPermissions(false);
                } else {
                    m_ideogram.setAudioExtention(JTApp.EXTENSION_SYNTHESIZER);
                    tempAudio = null;
                    toggleImageButtons();
                }
                break;
            case 3:
                m_ideogram.setAudioExtention(JTApp.EXTENSION_SYNTHESIZER);
                tempAudio = null;
                toggleImageButtons();
                break;
            default:
                break;
        }
    }

    private boolean isAudioFileValid(String path) {
        String ext = getFileExtention(path, false);
        return ext != null
                && (ext.equalsIgnoreCase("3gp") || ext.equalsIgnoreCase("mp4")
                || ext.equalsIgnoreCase("m4a") || ext.equalsIgnoreCase("mp3")
                || ext.equalsIgnoreCase("ogg") || ext.equalsIgnoreCase("wav")
                || ext.equalsIgnoreCase("amr") || ext.equalsIgnoreCase("3gpp"));
    }

    private boolean isImageFileValid(String path) {
        String ext = getFileExtention(path, false);
        return ext != null
                && (ext.equalsIgnoreCase("jpeg") || ext.equalsIgnoreCase("jpg")
                || ext.equalsIgnoreCase("gif") || ext.equalsIgnoreCase("png") || ext
                .equalsIgnoreCase("bmp"));
    }

    private void startRecording() {
        try {
            tempAudio = new File(JTApp.getDataStore().getTempDirectory(), m_ideogram.getId() + ".wav");
            m_recorder = new JTAudioRecorder(tempAudio.getAbsolutePath());
            m_recorder.startRecording();
            isRecording = true;
            ImageView mic = (ImageView) m_recordDialog.findViewById(R.id.recorder_image);
            mic.setBackgroundResource(R.drawable.microphone_on);
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
            isRecording = false;
        }
    }

    private void stopRecording() {
        m_recorder.stopRecording();
        isRecording = false;
        ImageView mic = (ImageView) m_recordDialog.findViewById(R.id.recorder_image);
        mic.setBackgroundResource(R.drawable.microphone);
        dismissDialog(DIALOG_AUDIO_RECORD);
        toggleImageButtons();
    }

    private void previewIdeogram() {

        m_ideogram.setLabel(m_label.getText().toString());
        m_ideogram.setPhrase(m_phrase.getText().toString());

        if (tempAudio != null) {
            m_ideogram.setTempAudioPath(tempAudio.getAbsolutePath());
            m_ideogram
                    .setAudioExtention(getFileExtention(tempAudio.getAbsolutePath(), false));
        }

        if (tempImage != null) {
            m_ideogram.setTempImagePath(tempImage.getAbsolutePath());
            m_ideogram
                    .setImageExtention(getFileExtention(tempImage.getAbsolutePath(), false));
        }

        if (!validateForm(m_ideogram)) {
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                    getString(R.string.dialog_title_invalid_field));
            showDialog(DIALOG_GENERIC);
        } else {
            if (m_ideogram.getPhrase() != null && m_ideogram.getPhrase().trim().length() > 0) {
                if (JTApp.isDisplayPhraseEnabled()) {
                    m_speakDialog.setMessage(m_ideogram.getPhrase());
                    m_speakDialog.show();
                    JTApp.play(m_ideogram);
                }
            }
        }
    }

    private boolean validateForm(Ideogram gram) {
        if (gram.getLabel() == null || gram.getLabel().length() < 1) {
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                    getString(R.string.dialog_message_invalid_label));
            return false;
        }

        if ((gram.getImageExtention() == null || gram.getImageExtention().length() < 1)
                || (!gram.getImageExtention().equals(JTApp.EXTENSION_TEXT_IMAGE) && ((tempImage == null && !new File(
                gram.getImagePath()).exists()) || (tempImage != null && !tempImage
                .exists())))) {
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                    getString(R.string.dialog_message_invalid_picture));
            return false;
        }

        if (gram.getPhrase() == null || gram.getPhrase().trim().length() < 1) {
            //Toast.makeText(this, "Audio not available. Display phrase or audio source is empty.", Toast.LENGTH_LONG).show();
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                    getString(R.string.dialog_message_invalid_phrase));
            return false;
        }

        return true;
    }

    private void saveIdeogram() throws JabException {
        DataStore store = JTApp.getDataStore();

        if (tempImage != null) {
            m_ideogram.setImageExtention(getFileExtention(tempImage.getName(), false));
        }
        if (tempAudio != null) {
            m_ideogram.setAudioExtention(getFileExtention(tempAudio.getName(), false));
        }

        if (!validateForm(m_ideogram)) {
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                    getString(R.string.dialog_title_invalid_field));
            showDialog(DIALOG_GENERIC);
            return;
        }

        if (tempImage != null) {
            File destination = new File(store.getImageDirectory(), m_ideogram.getId()
                    + getFileExtention(tempImage.getName(), true));
            boolean result = JTApp.getDataStore().copyFile(tempImage, destination);
            if (!result) {
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Failed to save image file to "
                        + destination.getAbsolutePath());
                throw new JabException(
                        "Failed to copy image file. Click the menu and select Log to view error details.");
            }

        }

        if (tempAudio != null) {
            File destination = new File(JTApp.getDataStore().getAudioDirectory(),
                    m_ideogram.getId() + getFileExtention(tempAudio.getName(), true));
            boolean result = JTApp.getDataStore().copyFile(tempAudio, destination);
            if (!result) {
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Failed to save audio file to "
                        + destination.getAbsolutePath());
                throw new JabException(
                        "Failed to copy audio file. Click the menu and select Log to view error details.");
            }
        }

        if (getIntent().getAction().equals(Intent.ACTION_INSERT)) {
            Ideogram parentCategory = JTApp.getDataStore().getIdeogram(m_ideogram.getParentId());
            Ideogram gram = new Ideogram(m_ideogram);
            parentCategory.getChildren(true).add(gram);
            JTApp.getDataStore().getIdeogramMap().put(gram.getId(), gram);
        } else {
            Ideogram gram = JTApp.getDataStore().getIdeogram(m_ideogram.getId());
            if (gram != null) {
                gram.setLabel(m_ideogram.getLabel());
                gram.setPhrase(m_ideogram.getPhrase());
                gram.setAudioExtention(m_ideogram.getAudioExtention());
                gram.setImageExtention(m_ideogram.getImageExtention());
                gram.setHidden(m_ideogram.isHidden());
            }
        }
        m_ideogram.setTempImagePath(null);
        m_ideogram.setTempAudioPath(null);

        persistChanges();
    }

    private void persistChanges() {
        if (saveTask == null || saveTask.getStatus() == Status.FINISHED) {
            saveTask = new SaveDataStoreTask();
            saveTask.execute();
        } else {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "SaveDataStore Task in invalid state");
        }
    }

    private void deleteIdeogram() throws JabException {
        JTApp.getDataStore().deleteIdeogram(m_ideogram.getId());
        persistChanges();
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

    private void createPreviewContainer(boolean isTextButton) {
        if (m_previewContainer.getChildCount() > 1) {
            m_previewContainer.removeViewAt(1);
        }

        // Add preview layout container
        RelativeLayout preview = JTApp.getPictureLayout(this, m_ideogram.getType(),
                PictureSize.GridPicture, isTextButton);
        preview.setId(R.id.RELATIVELAYOUT_ID);
        preview.setClickable(true);
        PictureFrameDimensions pfd = JTApp.getPictureDimensions(PictureSize.GridPicture, false);
        MarginLayoutParams previewMargins = new MarginLayoutParams(pfd.getWidth(),
                pfd.getHeight());
        previewMargins.topMargin = 5;
        RelativeLayout.LayoutParams previewParams = new RelativeLayout.LayoutParams(
                previewMargins);
        previewParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        previewParams.addRule(RelativeLayout.BELOW, R.id.click_preview);
        m_previewContainer.addView(preview, previewParams);
        m_previewContainer.invalidate();

        ImageView previewImage = (ImageView) m_previewContainer
                .findViewById(R.id.IMAGEVIEW_ID);
        AutoResizeTextView previewLabel = (AutoResizeTextView) m_previewContainer.findViewById(R.id.TEXTVIEW_ID);

        // Set Label
        if (m_label.getText().toString().length() > 0) {
            previewLabel.setText(m_label.getText().toString());
            previewLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, JTApp.getTitleFontSize());
        }

        // Set Image
        if (m_ideogram.getImageExtention() != null) {
            previewImage.setImageBitmap(m_ideogram.getImage());
        } else {
            previewImage.setImageDrawable(getResources().getDrawable(R.drawable.no_picture));
        }

        // Set text size for text button
        if (m_ideogram.isTextButton()) {
            previewLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, JTApp.MAX_TEXT_SIZE);
            PictureFrameDimensions tpfd = JTApp.getPictureDimensions(PictureSize.GridPicture,
                    true);
            previewLabel.resizeText(tpfd.getWidth(), tpfd.getHeight());
        }

        // Setup listeners
        preview.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                previewIdeogram();
            }
        });

        preview.setOnLongClickListener(new View.OnLongClickListener() {

            public boolean onLongClick(View v) {
                previewIdeogram();
                return true;
            }
        });

        // Setup Keyboard listener for Label
        m_label.addTextChangedListener(new TextWatcher() {

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
                updatePreviewLabel(s.toString());
                m_ideogram.setLabel(s.toString());
            }
        });
    }

    private void updatePreviewLabel(String text) {
        AutoResizeTextView previewLabel = (AutoResizeTextView) m_previewContainer
                .findViewById(R.id.TEXTVIEW_ID);
        previewLabel.setText(text);
        if (m_ideogram.isTextButton()) {
            PictureFrameDimensions dm = JTApp.getPictureDimensions(PictureSize.GridPicture, true);
            previewLabel.resizeText(dm.getWidth(), dm.getHeight());
        } else {
            previewLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, JTApp.getTitleFontSize());
        }
    }

    private void resizePhoto(String imagePath) throws JabException {
        String ext = getFileExtention(imagePath, true);
        File scaledFile = new File(JTApp.getDataStore().getTempDirectory(), m_ideogram.getId()
                + ext);
        Bitmap optimizedBmp;

        // Get width and height of image
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, opts);
        int height = opts.outHeight;
        int width = opts.outWidth;

        // Find dimensions for standard landscape photo
        int standardLandWidth = getResources().getInteger(
                R.integer.image_standard_landscape_width);
        int standardLandHeight = getResources().getInteger(
                R.integer.image_standard_landscape_height);

        // Find dimensions for standard portrait photo
        int standardPortWidth = getResources().getInteger(
                R.integer.image_standard_portrait_width);
        int standardPortHeight = getResources().getInteger(
                R.integer.image_standard_portrait_height);

        if (width > standardLandWidth || height > standardLandWidth) {

            int targetWidth;
            int targetHeight;
            float scalePercentage;

            //Create scaled down version of image so we don't run out of memory
            BitmapFactory.Options scaledOpts = new BitmapFactory.Options();
            if (width > (standardLandWidth * 2) || height > (standardLandWidth * 2)) {
                scaledOpts.inSampleSize = 2;
            }
            Bitmap scaledBmp = BitmapFactory.decodeFile(imagePath, scaledOpts);

            //Determine target width and height and scaling percentage 
            if (width >= height) {
                scalePercentage = (float) standardLandHeight / scaledBmp.getHeight();
                targetWidth = standardLandWidth;
                targetHeight = standardLandHeight;
            } else {
                scalePercentage = (float) standardPortWidth / scaledBmp.getWidth();
                targetWidth = standardPortWidth;
                targetHeight = standardPortHeight;
            }

            //Resize bitmap to best height and width before cropping is necessary
            Bitmap resizedBmp;
            if (scalePercentage < 1) {
                Matrix matrix = new Matrix();
                matrix.postScale(scalePercentage, scalePercentage);
                resizedBmp = Bitmap.createBitmap(scaledBmp, 0, 0, scaledBmp.getWidth(),
                        scaledBmp.getHeight(), matrix, false);
            } else {
                resizedBmp = Bitmap.createBitmap(scaledBmp);
            }

            //Setup image for cropping
            Bitmap croppedBmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(croppedBmp);

            //Only crop if necessary
            if (resizedBmp.getWidth() > targetWidth || resizedBmp.getHeight() > standardLandWidth) {
                Rect cropRect = new Rect(Math.abs((resizedBmp.getWidth() - targetWidth)) / 2, (Math.abs(resizedBmp.getHeight() - targetHeight)) / 2, targetWidth, targetHeight);
                Rect destRect = new Rect(0, 0, targetWidth, targetHeight);
                canvas.drawBitmap(resizedBmp, cropRect, destRect, new Paint());
            } else {
                //Cropping is not necessary...just center resized image in canvas
                canvas.drawBitmap(resizedBmp, Math.abs(resizedBmp.getWidth() - targetWidth) / 2, Math.abs(resizedBmp.getHeight() - targetHeight) / 2, new Paint());
            }
            optimizedBmp = Bitmap.createBitmap(croppedBmp);
        } else {
            optimizedBmp = BitmapFactory.decodeFile(imagePath);
        }

        //See if image needs to be rotated
        int rotate = getCameraPhotoOrientation(imagePath);
        if (rotate > 0) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, "Rotating image: " + imagePath + " " + rotate + " degrees.");
            Matrix matrix = new Matrix();
            matrix.postRotate(rotate);
            optimizedBmp = Bitmap.createBitmap(optimizedBmp, 0, 0, optimizedBmp.getWidth(), optimizedBmp.getHeight(), matrix, true);
        }

        // Save image to cache storage
        JTApp.getDataStore().saveScaledImage(scaledFile, optimizedBmp);
        tempImage = scaledFile;
        if (!tempImage.exists() || tempImage.length() < 1) {
            tempImage = null;
        }
        setImagePreview(optimizedBmp);
    }

    private int getCameraPhotoOrientation(String imagePath) {
        int rotate = 0;
        try {
            File imageFile = new File(imagePath);

            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    break;
            }

            if (rotate > 0) {
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, "Image for \"" + m_label.getText().toString() + "\" is being rotated " + rotate + " degrees");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return rotate;
    }

    private void setImagePreview(Bitmap optimizedBmp) {
        ImageView previewImage = (ImageView) m_previewContainer
                .findViewById(R.id.IMAGEVIEW_ID);
        previewImage.setImageBitmap(optimizedBmp);
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


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (tempImage == null && savedInstanceState.containsKey(STATE_IMAGE)) {
            tempImage = new File(savedInstanceState.getString(STATE_IMAGE));
            if (!tempImage.exists()) {
                tempImage = null;
            } else {
                Bitmap prevBmp = BitmapFactory.decodeFile(tempImage.getAbsolutePath());
                setImagePreview(prevBmp);
            }
        }
        if (tempAudio == null && savedInstanceState.containsKey(STATE_AUDIO)) {
            tempAudio = new File(savedInstanceState.getString(STATE_AUDIO));
            if (!tempAudio.exists()) {
                tempAudio = null;
            }
        }
        if (savedInstanceState.containsKey(STATE_IDEOGRAM)) {
            m_ideogram = (Ideogram) savedInstanceState.getSerializable(STATE_IDEOGRAM);
        }

        toggleImageButtons();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (tempImage != null) {
            outState.putString(STATE_IMAGE, tempImage.getAbsolutePath());
        }
        if (tempAudio != null) {
            outState.putString(STATE_AUDIO, tempAudio.getAbsolutePath());
        }
        outState.putSerializable(STATE_IDEOGRAM, m_ideogram);
    }

    private class SaveDataStoreTask extends AsyncTask<Void, Void, Void> {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        private boolean errorFlag = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            progressDialog.setMessage(getString(R.string.dialog_message_saving));
            progressDialog.show();
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Save");
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                JTApp.getDataStore().saveDataStore();
            } catch (JabException e) {
                errorFlag = true;
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE, getString(R.string.dialog_title_save_results));
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);
            unlockScreenOrientation();
            if (errorFlag) {
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_FINISH_ON_DISMISS, true);
                showDialog(DIALOG_GENERIC);
            } else {
                JTApp.getDataStore().clearCache();
                JTApp.fireDataStoreUpdated();
            }
            progressDialog.dismiss();
            if (m_wakeLock.isHeld()) {
                m_wakeLock.release();
            }

            exitActivity();
        }
    }

}