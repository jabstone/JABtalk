package com.jabstone.jabtalk.basic.activity;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;

import android.content.res.Configuration;

import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.PowerManager;

import android.provider.OpenableColumns;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.jabstone.jabtalk.basic.ClipBoard;
import com.jabstone.jabtalk.basic.ClipBoard.Operation;
import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.adapters.ManageParentListAdapter;
import com.jabstone.jabtalk.basic.exceptions.JabException;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;


public class ManageActivity extends Activity {

    private static final String TAG = ManageActivity.class.getSimpleName();
    private final int DIALOG_DELETE_IDEOGRAM_CONFIRMATION = 4001;
    private final int DIALOG_ERROR = 4002;
    private final int DIALOG_GENERIC = 4004;
    private final int DIALOG_EXIT_MANAGE_SCREEN = 4005;
    private final int DIALOG_ACTION_ADD = 4006;
    private final String STATE_IDEOGRAM = "ideogram";
    private final int ADD_CATEGORY = 0;
    private final int ADD_WORD = 1;
    private final int ACTIVITY_RESULT_PREFERENCE = 5000;
    private final int ACTIVITY_EDIT_IDEOGRAM = 5001;
    private final int ACTIVITY_EXPAND_CATEGORY = 5002;
    private final int ACTIVITY_SELECT_BACKUP_PATH = 5003;
    private final int ACTIVITY_SELECT_RESTORE_FILE = 5004;
    private ManageParentListAdapter m_adapter = null;
    private RestoreTask restoreTask = null;
    private BackupTask backupTask = null;
    private SaveDataStoreTask saveTask = null;
    private ProgressDialog progressDialog = null;
    private Ideogram m_ideogram = null;
    private Ideogram m_selectedGram = null;
    private boolean madeChanges = false;
    private ListView m_listView = null;
    private boolean isBackupRestoreClicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_activity);

        boolean fromMain = getIntent().hasExtra(JTApp.INTENT_EXTRA_CALLED_FROM_MAIN);

        String gramId = getIntent().getStringExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID);
        m_ideogram = JTApp.getDataStore().getIdeogram(gramId);
        m_selectedGram = m_ideogram;

        // See if activity was launched from main screen due to empty category
        if (!m_ideogram.isRoot() && m_ideogram.getType() == Type.Category && fromMain) {
            m_ideogram = JTApp.getDataStore().getRootCategory();
            m_selectedGram = JTApp.getDataStore().getIdeogram(gramId);
            expandCategory(m_selectedGram);
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        m_adapter = new ManageParentListAdapter(this, m_ideogram);
        m_adapter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Ideogram gram = (Ideogram) v.getTag();
                if (gram.getType() == Type.Category) {
                    expandCategory(gram);
                } else {
                    editIdeogram(gram);
                }
            }
        });
        setListAdapter(m_adapter);

        JTApp.addDataStoreListener(m_adapter);
        restoreProgressDialog();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(STATE_IDEOGRAM)) {
            m_ideogram = (Ideogram) savedInstanceState.getSerializable(STATE_IDEOGRAM);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_IDEOGRAM, m_ideogram);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(m_ideogram == null) {
            m_ideogram = JTApp.getDataStore().getRootCategory();
        }
        if (!m_ideogram.isRoot()) {
            this.setTitle(m_ideogram.getLabel());
        } else {
            this.setTitle(getString(R.string.manage_activity_title));
        }
        restoreProgressDialog();
        m_selectedGram = m_ideogram;
        invalidateOptionsMenu();

        // Display add item dialog if category is blank
        if (m_ideogram != null && m_ideogram.getChildren(true).size() < 1 && !isBackupRestoreClicked) {
            showDialog(DIALOG_ACTION_ADD);
        }
        isBackupRestoreClicked = false;
    }

    @Override
    protected void onPause() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        super.onPause();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        m_selectedGram = (Ideogram) v.getTag();
        int position = m_ideogram.getChildren(true).indexOf(m_selectedGram);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.manage_context_menu, menu);
        ClipBoard clip = JTApp.getClipBoard();
        MenuItem convert = menu.findItem(R.id.context_menu_item_convert);
        MenuItem paste = menu.findItem(R.id.context_menu_item_paste);
        paste.setEnabled(false);

        // Move menu items
        MenuItem moveUp = menu.findItem(R.id.context_menu_item_move_up);
        MenuItem moveFirst = menu.findItem(R.id.context_menu_item_move_beginning);
        MenuItem moveDown = menu.findItem(R.id.context_menu_item_move_down);
        MenuItem moveLast = menu.findItem(R.id.context_menu_item_move_end);
        moveUp.setEnabled(true);
        moveDown.setEnabled(true);
        moveFirst.setEnabled(true);
        moveLast.setEnabled(true);

        // Should we show the hide or unhide menu item
        MenuItem hide = menu.findItem(R.id.context_menu_item_hide);
        hide.setTitle(R.string.menu_hide);
        if (m_selectedGram.isHidden()) {
            hide.setTitle(R.string.menu_unhide);
        }

        // Should we show past menu item
        if (!clip.isEmpty() && m_selectedGram.getType() == Type.Category
                && JTApp.getDataStore().getIdeogramMap().containsKey(clip.getId())) {
            Ideogram source = JTApp.getDataStore().getIdeogram(clip.getId());
            if (source != null) {
                switch (clip.getOperation()) {
                    case CUT:
                        if (JTApp.getDataStore().isSafeToMove(source, m_selectedGram)) {
                            paste.setEnabled(true);
                        }
                        break;
                    case COPY:
                        paste.setEnabled(true);
                        break;
                }
            }
        }

        // Should we show backup category menu item
        MenuItem backupCategory = menu.findItem(R.id.context_menu_backup_category);
        MenuItem restoreCategory = menu.findItem(R.id.context_menu_restore_category);
        boolean isCategory = m_selectedGram.getType() == Type.Category;
        backupCategory.setEnabled(isCategory);
        restoreCategory.setEnabled(isCategory);

        // Determine state of move menu items
        if (position < 1 || m_ideogram.getChildren(true).size() < 2) {
            moveUp.setEnabled(false);
            moveFirst.setEnabled(false);
        }
        if (position == m_ideogram.getChildren(true).size() - 1
                || m_ideogram.getChildren(true).size() < 2) {
            moveDown.setEnabled(false);
            moveLast.setEnabled(false);
        }
        if (m_selectedGram.getType() == Type.Category) {
            convert.setEnabled(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (m_ideogram.isRoot()) {
            showDialog(DIALOG_EXIT_MANAGE_SCREEN);
        } else {
            finish();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ClipBoard clipboard = JTApp.getClipBoard();
        int position = m_ideogram.getChildren(true).indexOf(m_selectedGram);
        LinkedList<Ideogram> categoryList = m_ideogram.getChildren(true);

        switch (item.getItemId()) {
            case R.id.context_menu_item_edit:
                editIdeogram(m_selectedGram);
                break;
            case R.id.context_menu_item_delete:
                m_ideogram = m_selectedGram;
                if (m_ideogram != null) {
                    showDialog(DIALOG_DELETE_IDEOGRAM_CONFIRMATION);
                }
                break;
            case R.id.context_menu_item_copy:
                clipboard.setId(m_selectedGram.getId());
                clipboard.setOperation(Operation.COPY);
                invalidateOptionsMenu();
                break;
            case R.id.context_menu_item_cut:
                clipboard.setId(m_selectedGram.getId());
                clipboard.setOperation(Operation.CUT);
                invalidateOptionsMenu();
                break;
            case R.id.context_menu_item_paste:
                try {
                    JTApp.getClipBoard().paste(m_selectedGram.getId());
                    persistChanges(false);
                    invalidateOptionsMenu();
                } catch (JabException e) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_save_results));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
            case R.id.context_menu_item_move_up:
                categoryList.add(position - 1, m_selectedGram);
                categoryList.remove(position + 1);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_move_down:
                categoryList.remove(position);
                categoryList.add(position + 1, m_selectedGram);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_move_beginning:
                categoryList.addFirst(m_selectedGram);
                categoryList.remove(position + 1);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_move_end:
                categoryList.addLast(m_selectedGram);
                categoryList.remove(position);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_hide:
                m_selectedGram.setHidden(!m_selectedGram.isHidden());
                persistChanges(false);
                break;
            case R.id.context_menu_item_convert:
                Ideogram sub = new Ideogram(m_selectedGram);
                sub.setType(Type.Category);

                Ideogram parent = JTApp.getDataStore()
                        .getIdeogram(m_selectedGram.getParentId());
                parent.getChildren(true).add(sub);
                parent.getChildren(true).remove(m_selectedGram);
                JTApp.getDataStore().getIdeogramMap().remove(m_selectedGram);
                JTApp.getDataStore().getIdeogramMap().put(sub.getId(), sub);
                m_selectedGram = sub;
                persistChanges(false);
                break;
            case R.id.context_menu_backup_category:
                isBackupRestoreClicked = true;
                Date currentDate = new Date(System.currentTimeMillis());
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String formattedDate = dateFormat.format(currentDate);
                formattedDate = formattedDate.replaceAll("[^a-zA-Z0-9.-]", "_");

                m_ideogram = m_selectedGram;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "jabtalk_" + formattedDate + ".bak");
                startActivityForResult(intent, ACTIVITY_SELECT_BACKUP_PATH);
                break;
            case R.id.context_menu_restore_category:
                isBackupRestoreClicked = true;
                m_selectedGram = m_ideogram;
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, ACTIVITY_SELECT_RESTORE_FILE);
                break;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        JTApp.removeDataStoreListener(m_adapter);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.manage_menu, menu);

        // Only show edit action item if current item is a category
        MenuItem edit = menu.findItem(R.id.menu_item_edit_ideogram);
        edit.setVisible(false);
        if (!m_ideogram.isRoot()) {
            edit.setVisible(true);
        }

        MenuItem backup = menu.findItem(R.id.menu_item_backup);
        backup.setVisible(true);
        if (m_ideogram.isRoot() && m_ideogram.getChildren(true).size() == 0) {
            backup.setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu != null) {
            MenuItem paste = menu.findItem(R.id.menu_item_paste);
            ClipBoard clip = JTApp.getClipBoard();
            paste.setVisible(false);

            if (!clip.isEmpty()) {
                Ideogram source = JTApp.getDataStore().getIdeogram(clip.getId());
                if (source != null
                        && (clip.getOperation() == Operation.COPY || JTApp.getDataStore()
                        .isSafeToMove(source, m_selectedGram))) {
                    paste.setVisible(true);
                }
            }

        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_item_history:
                intent = new Intent(this, HistoryActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_item_log:
                intent = new Intent(this, LogActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_item_backup:
                isBackupRestoreClicked = true;
                Date currentDate = new Date(System.currentTimeMillis());
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String formattedDate = dateFormat.format(currentDate);
                formattedDate = formattedDate.replaceAll("[^a-zA-Z0-9.-]", "_");

                m_selectedGram = JTApp.getDataStore().getRootCategory();
                intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "jabtalk_" + formattedDate + ".bak");
                startActivityForResult(intent, ACTIVITY_SELECT_BACKUP_PATH);
                break;
            case R.id.menu_item_paste:
                try {
                    JTApp.getClipBoard().paste(m_ideogram.getId());
                    persistChanges(false);
                    invalidateOptionsMenu();
                } catch (JabException e) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_save_results));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
            case R.id.menu_item_restore:
                isBackupRestoreClicked = true;
                m_selectedGram = m_ideogram;
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, ACTIVITY_SELECT_RESTORE_FILE);
                break;
            case R.id.menu_item_help:
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(JTApp.URL_SUPPORT));
                startActivity(i);
                break;
            case R.id.menu_item_preferences:
                intent = new Intent(this, PreferenceActivity.class);
                startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCE);
                break;
            case R.id.menu_item_add_ideogram:
                showDialog(DIALOG_ACTION_ADD);
                break;
            case R.id.menu_item_edit_ideogram:
                editIdeogram(m_ideogram);
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case ACTIVITY_RESULT_PREFERENCE:
                    getIntent().putExtra(JTApp.INTENT_EXTRA_REFRESH, true);
                    setResult(RESULT_OK, getIntent());
                    break;
                case ACTIVITY_EDIT_IDEOGRAM:
                    boolean isDataDirty = data.getBooleanExtra(JTApp.INTENT_EXTRA_DIRTY_DATA,
                            false);
                    if (isDataDirty) {
                        madeChanges = true;
                        JTApp.fireDataStoreUpdated();
                        String id = getIntent().getStringExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID);
                        if (JTApp.getDataStore().getIdeogram(id) == null) {
                            exitActivity();
                        }
                    }
                    break;
                case ACTIVITY_SELECT_BACKUP_PATH:
                    Uri uri = null;
                    if (data != null) {
                        uri = data.getData();
                        String backupName = getFileNameFromUri(uri);
                        if(!backupName.endsWith(".bak")) {
                            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Backup files must end with a .bak file extension.");
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                                    getString(R.string.dialog_title_backup_results));
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, getString(R.string.dialog_message_backup_invalid_filename));
                            showDialog(DIALOG_GENERIC);
                        } else {
                            backupData(uri);
                        }
                    }
                    break;
                case ACTIVITY_SELECT_RESTORE_FILE:
                    Uri restoreUri = null;
                    if (data != null) {
                        restoreUri = data.getData();
                        String backupName = getFileNameFromUri(restoreUri);
                        if(!backupName.endsWith(".bak")) {
                            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Please choose a backup file with a .bak file extension.");
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                                    getString(R.string.dialog_title_restore_results));
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, getString(R.string.dialog_message_backup_invalid_filename));
                            showDialog(DIALOG_GENERIC);
                        } else {
                            if (restoreTask == null
                                    || restoreTask.getStatus() == Status.FINISHED) {
                                restoreTask = new RestoreTask(false);
                                restoreTask.execute(restoreUri);
                            } else {
                                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                                        "RestoreTask in invalid state");
                            }
                        }
                        
                    }
                    break;
            }
            
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        AlertDialog alert = null;
        AlertDialog.Builder builder;
        switch (id) {
            case DIALOG_ACTION_ADD:
                final CharSequence[] items = new CharSequence[2];
                items[ADD_CATEGORY] = getString(R.string.dialog_item_add_category);
                items[ADD_WORD] = getString(R.string.dialog_item_add_word);

                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.manage_activity_category_actions));
                builder.setItems(items, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int item) {
                        addIdeogram(item);
                    }
                });
                alert = builder.create();
                break;
            case DIALOG_DELETE_IDEOGRAM_CONFIRMATION:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_delete_confirmation));

                if (m_selectedGram.getType().equals(Type.Word)) {
                    builder.setMessage(R.string.dialog_message_delete_word);
                } else {
                    builder.setMessage(R.string.dialog_message_delete_category);
                }

                builder.setPositiveButton(R.string.button_yes,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                JTApp.getDataStore().deleteIdeogram(m_selectedGram.getId());
                                persistChanges(false);
                                invalidateOptionsMenu();
                                m_ideogram = JTApp.getDataStore().getRootCategory();
                                m_selectedGram = m_ideogram;
                            }
                        });
                builder.setNegativeButton(R.string.button_no,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_DELETE_IDEOGRAM_CONFIRMATION);
                            }
                        });
                alert = builder.create();
                break;

            case DIALOG_EXIT_MANAGE_SCREEN:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_exit_warning));
                builder.setMessage(R.string.dialog_message_leave_screen);

                builder.setPositiveButton(R.string.button_yes,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                exitActivity();
                            }
                        });
                builder.setNegativeButton(R.string.button_no,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_EXIT_MANAGE_SCREEN);
                            }
                        });
                alert = builder.create();
                break;
            case DIALOG_ERROR:
                final Activity thisActity = this;
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getIntent().getStringExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE));
                builder.setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE));

                builder.setPositiveButton(R.string.button_view_log,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_ERROR);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE);
                                Intent logIntent = new Intent(thisActity, LogActivity.class);
                                startActivity(logIntent);
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

        }
        return alert;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        switch (id) {
            case DIALOG_DELETE_IDEOGRAM_CONFIRMATION:
                if (m_selectedGram.getType().equals(Type.Word)) {
                    ((AlertDialog) dialog)
                            .setMessage(getString(R.string.dialog_message_delete_word));
                } else {
                    ((AlertDialog) dialog)
                            .setMessage(getString(R.string.dialog_message_delete_category));
                }
                break;
            case DIALOG_ERROR:
            case DIALOG_GENERIC:
                ((AlertDialog) dialog).setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_TITLE));
                ((AlertDialog) dialog).setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE));
                break;
        }
    }

    protected ListView getListView() {
        if (m_listView == null) {
            m_listView = (ListView) findViewById(android.R.id.list);
        }
        return m_listView;
    }

    protected ListAdapter getListAdapter() {
        return getListView().getAdapter();
    }

    protected void setListAdapter(ListAdapter adapter) {
        getListView().setAdapter(adapter);
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (uri.getScheme() != null && uri.getScheme().equals("file")) {
            fileName = new File(uri.getPath()).getName();
        }
        return fileName;
    }

    private void restoreProgressDialog() {
        if (restoreTask != null && restoreTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressDialog.setMessage(getString(R.string.dialog_message_restoring));
            progressDialog.show();
        }

        if (saveTask != null && saveTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressDialog.setMessage(getString(R.string.dialog_message_saving));
            progressDialog.show();
        }

        if (backupTask != null && backupTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressDialog.setMessage(getString(R.string.dialog_message_backup));
            progressDialog.show();
        }
    }

    private void expandCategory(Ideogram gram) {
        Intent intent = new Intent();
        intent.setClass(this, ManageActivity.class);
        intent.setAction(Intent.ACTION_EDIT);
        intent.putExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID, gram.getId());
        intent.putExtra(JTApp.INTENT_EXTRA_TYPE, Type.Category);
        startActivityForResult(intent, ACTIVITY_EXPAND_CATEGORY);
    }



    private void backupData(Uri fileName) {
        if (backupTask == null || backupTask.getStatus() == Status.FINISHED) {
            backupTask = new BackupTask();
            backupTask.execute(fileName);
        } else {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "BackupTask in invalid state");
        }
    }

    private void editIdeogram(Ideogram gram) {
        Intent intent;
        intent = new Intent();
        intent.setClass(this, EditIdeogramActivity.class);
        intent.putExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID, gram.getId());
        intent.putExtra(JTApp.INTENT_EXTRA_TYPE, gram.getType());
        intent.setAction(Intent.ACTION_EDIT);
        startActivityForResult(intent, ACTIVITY_EDIT_IDEOGRAM);
    }

    private void addIdeogram(int item) {
        Intent intent;
        intent = new Intent();
        intent.setClass(this, EditIdeogramActivity.class);
        intent.setAction(Intent.ACTION_INSERT);
        intent.putExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID, m_ideogram.getId());

        // Are we adding a word or category
        switch (item) {
            case ADD_CATEGORY:
                intent.putExtra(JTApp.INTENT_EXTRA_TYPE, Type.Category);
                break;
            case ADD_WORD:
                intent.putExtra(JTApp.INTENT_EXTRA_TYPE, Type.Word);
                break;
        }

        startActivityForResult(intent, ACTIVITY_EDIT_IDEOGRAM);
    }

    private void exitActivity() {
        setResult(RESULT_OK, getIntent());
        if (madeChanges) {
            persistChanges(true);
        } else {
            finish();
        }
    }

    private void persistChanges(boolean exitAfterSave) {
        if (saveTask == null || saveTask.getStatus() == Status.FINISHED) {
            saveTask = new SaveDataStoreTask();
            saveTask.execute(exitAfterSave);
        } else {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "SaveDataStore Task in invalid state");
        }
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


    private class RestoreTask extends AsyncTask<Uri, Void, Void> {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        private boolean errorFlag = false;
        private boolean restoreOverFlag = false;

        public RestoreTask(boolean removeExistingDataset) {
            restoreOverFlag = removeExistingDataset;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            progressDialog.setMessage(getString(R.string.dialog_message_restoring));
            progressDialog.show();
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Restore:jabtalkPWL");
        }

        @Override
        protected Void doInBackground(Uri... params) {
            Uri fileName = params[0];
            try {
                if (restoreOverFlag) {
                    JTApp.getDataStore().restoreFullDataStore(fileName);
                } else {
                    JTApp.getDataStore().restorePartialDataStore(fileName, m_selectedGram);
                }
            } catch (Exception e) {
                errorFlag = true;
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                        getString(R.string.dialog_title_restore_results));
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);

            unlockScreenOrientation();
            JTApp.getClipBoard().clear();
            progressDialog.dismiss();

            if (m_wakeLock.isHeld()) {
                m_wakeLock.release();
            }
            JTApp.fireDataStoreUpdated();

            if (errorFlag) {
                showDialog(DIALOG_ERROR);
            } else {
                getIntent().putExtra(JTApp.INTENT_EXTRA_CLEAR_MANAGE_STACK, true);
                setResult(RESULT_OK, getIntent());
                finish();
            }
        }
    }

    private class BackupTask extends AsyncTask<Uri, Void, Void> {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        private boolean errorFlag = false;
        private Uri fileName = null;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            if (madeChanges) {
                progressDialog.setMessage(getString(R.string.dialog_message_saving));
            } else {
                progressDialog.setMessage(getString(R.string.dialog_message_backup));
            }
            progressDialog.show();
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Backup:jabtalkPWL");
        }

        @Override
        protected Void doInBackground(Uri... params) {
            fileName = params[0];
            try {
                if (madeChanges) {
                    JTApp.getDataStore().saveDataStore();
                }

                JTApp.getDataStore().backupDataStore(fileName, m_selectedGram);

            } catch (Exception e) {
                errorFlag = true;
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);
            unlockScreenOrientation();
            progressDialog.dismiss();

            if (m_wakeLock.isHeld()) {
                m_wakeLock.release();
            }
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                    getString(R.string.dialog_title_backup_results));
            if (!errorFlag) {
                getIntent().putExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                        getString(R.string.dialog_message_backup_success));
                showDialog(DIALOG_GENERIC);
            } else {
                showDialog(DIALOG_ERROR);
            }
        }

    }

    private class SaveDataStoreTask extends AsyncTask<Boolean, Void, Void> {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        boolean exitAfterSave = false;
        private boolean errorFlag = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            progressDialog.setMessage(getString(R.string.dialog_message_saving));
            progressDialog.show();
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Save:jabtalkPWL");
        }

        @Override
        protected Void doInBackground(Boolean... params) {
            exitAfterSave = params[0];
            try {
                JTApp.getDataStore().saveDataStore();
            } catch (JabException e) {
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
            unlockScreenOrientation();
            progressDialog.dismiss();

            if (m_wakeLock.isHeld()) {
                m_wakeLock.release();
            }

            if (errorFlag) {
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_FINISH_ON_DISMISS, true);
                showDialog(DIALOG_ERROR);
            } else {
                JTApp.fireDataStoreUpdated();
            }
            if (exitAfterSave) {
                finish();
            }
        }
    }

}
