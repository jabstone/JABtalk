package com.jabstone.jabtalk.basic.activity;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.jabstone.jabtalk.basic.ClipBoard;
import com.jabstone.jabtalk.basic.ClipBoard.Operation;
import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.adapters.ManageParentListAdapter;
import com.jabstone.jabtalk.basic.adapters.RestoreBackupListAdapter;
import com.jabstone.jabtalk.basic.exceptions.JabException;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;

import java.io.File;
import java.util.LinkedList;


public class ManageActivity extends Activity {

    private static String TAG = ManageActivity.class.getSimpleName();
    private final int DIALOG_DELETE_IDEOGRAM_CONFIRMATION = 4001;
    private final int DIALOG_ERROR = 4002;
    private final int DIALOG_EXPLAIN_WRITE_PERMISSIONS_BACKUP_FULL = 4003;
    private final int DIALOG_GENERIC = 4004;
    private final int DIALOG_EXIT_MANAGE_SCREEN = 4005;
    private final int DIALOG_ACTION_ADD = 4006;
    private final int DIALOG_BACKUP_DATASTORE_FULL = 4007;
    private final int DIALOG_RESTORE_DATASTORE_FULL = 4008;
    private final int DIALOG_EXPLAIN_READ_PERMISSIONS_RESTORE_FULL = 4009;
    private final int DIALOG_BACKUP_DATASTORE_PARTIAL = 4010;
    private final int DIALOG_RESTORE_DATASTORE_PARTIAL = 4011;
    private final int DIALOG_EXPLAIN_WRITE_PERMISSIONS_BACKUP_PARTIAL = 4012;
    private final int DIALOG_EXPLAIN_READ_PERMISSIONS_RESTORE_PARTIAL = 4013;

    private final int BACKUP_PERMISSIONS = 6000;
    private final int RESTORE_PERMISSIONS_FULL = 6001;
    private final int RESTORE_PERMISSIONS_PARTIAL = 6002;

    private final String STATE_IDEOGRAM = "ideogram";
    private final int ADD_CATEGORY = 0;
    private final int ADD_WORD = 1;
    private final int ACTIVITY_RESULT_PREFERENCE = 5000;
    private final int ACTIVITY_EDIT_IDEOGRAM = 5001;
    private final int ACTIVITY_EXPAND_CATEGORY = 5002;
    private ManageParentListAdapter m_adapter = null;
    private RestoreBackupListAdapter m_restoreAdapter = null;
    private RestoreTask restoreTask = null;
    private BackupTask backupTask = null;
    private SaveDataStoreTask saveTask = null;
    private ProgressDialog progressDialog = null;
    private Ideogram m_ideogram = null;
    private Ideogram m_selectedGram = null;
    private boolean madeChanges = false;
    private ListView m_listView = null;
    private int m_selectedItemToRestore = -1;

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
        m_restoreAdapter = new RestoreBackupListAdapter(this);

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
        if (m_ideogram == null
                || !JTApp.getDataStore().getIdeogramMap().containsKey(m_ideogram.getId())) {
            finish();
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
        if (m_ideogram != null && m_ideogram.getChildren(true).size() < 1) {
            showDialog(DIALOG_ACTION_ADD);
        }
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
                requestWritePermissions(DIALOG_BACKUP_DATASTORE_PARTIAL, false);
                break;
            case R.id.context_menu_restore_category:
                requestReadPermissions(DIALOG_RESTORE_DATASTORE_PARTIAL, false);
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
                requestWritePermissions(DIALOG_BACKUP_DATASTORE_FULL, false);
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
                requestReadPermissions(DIALOG_RESTORE_DATASTORE_FULL, false);
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
            }

            if (data != null && data.hasExtra(JTApp.INTENT_EXTRA_CLEAR_MANAGE_STACK)) {
                setResult(RESULT_OK, data);
                finish();
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
            case DIALOG_RESTORE_DATASTORE_FULL:
            case DIALOG_RESTORE_DATASTORE_PARTIAL:
                builder = new AlertDialog.Builder(this);
                LayoutInflater restoreInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                final View restoreLayout = restoreInflater.inflate(R.layout.backup_restore_dialog,
                        (ViewGroup) findViewById(R.id.restore_linear_layout));
                final ListView listView = (ListView) restoreLayout.findViewById(R.id.restore_list);
                final CheckBox restoreOver = (CheckBox) restoreLayout.findViewById(R.id.chkRestoreOverData);

                final Button btnCancelRestore = (Button) restoreLayout.findViewById(R.id.btn_cancelRestore);
                btnCancelRestore.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dismissDialog(id);

                        //Ugly hack to get around selection not clearing on cancel
                        if (m_selectedItemToRestore >= 0) {
                            getIntent().putExtra(JTApp.INTENT_EXTRA_CLEAR_MANAGE_STACK, true);
                            setResult(RESULT_OK, getIntent());
                            finish();
                        }
                    }
                });
                final Button btnRestore = (Button) restoreLayout.findViewById(R.id.btn_saveRestore);
                btnRestore.setEnabled(false);
                btnRestore.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (m_selectedItemToRestore >= 0) {
                            Object obj = m_restoreAdapter.getItem(m_selectedItemToRestore);
                            if (obj instanceof File) {
                                File f = (File) obj;
                                if (restoreTask == null
                                        || restoreTask.getStatus() == Status.FINISHED) {
                                    dismissDialog(id);
                                    restoreTask = new RestoreTask(restoreOver.isChecked());
                                    restoreTask.execute(f.getPath());
                                } else {
                                    JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                                            "RestoreTask in invalid state");
                                }
                            }
                        }
                    }
                });

                listView.setAdapter(m_restoreAdapter);
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        btnRestore.setEnabled(true);
                        m_selectedItemToRestore = i;
                    }
                });

                builder.setTitle(R.string.dialog_title_restore_dataset);
                builder.setIcon(R.drawable.ic_action_restore);
                builder.setView(restoreLayout);
                builder.setCancelable(true);

                alert = builder.create();
                break;
            case DIALOG_BACKUP_DATASTORE_FULL:
            case DIALOG_BACKUP_DATASTORE_PARTIAL:
                builder = new AlertDialog.Builder(this);
                LayoutInflater backupInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                final View layout = backupInflater.inflate(R.layout.backup_save,
                        (ViewGroup) findViewById(R.id.backup_save_layout));
                Button btnCancelBackup = (Button) layout.findViewById(R.id.btn_cancelBackup);
                final Button btnBackup = (Button) layout.findViewById(R.id.btn_Backup);

                builder.setTitle(id == DIALOG_BACKUP_DATASTORE_FULL ? R.string.dialog_title_backup_save_as : R.string.dialog_title_backup_partial_save_as);
                builder.setIcon(R.drawable.ic_action_save);
                builder.setView(layout);
                builder.setCancelable(true);

                final EditText fileName = (EditText) layout.findViewById(R.id.backup_filename);
                InputFilter fileNameFilter = new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                        if (source.length() > 0) {
                            char last = source.charAt(source.length() - 1);
                            String reservedChars = "?:\"*|/\\<>";
                            if (reservedChars.indexOf(last) > -1) {
                                return source.subSequence(0, source.length() - 1);
                            }
                        }
                        return null;
                    }
                };
                fileName.setFilters(new InputFilter[]{fileNameFilter});
                fileName.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        btnBackup.setEnabled(fileName.getText().toString().trim().length() > 0);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {

                    }
                });

                btnCancelBackup.setOnClickListener(new View.OnClickListener() {

                    public void onClick(View v) {
                        dismissDialog(id);
                    }
                });

                btnBackup.setOnClickListener(new View.OnClickListener() {

                    public void onClick(View v) {
                        dismissDialog(id);
                        String ext = getString(R.string.dialog_label_backup_extention_bak);
                        backupData(fileName.getText().toString().trim() + ext);
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
            case DIALOG_EXPLAIN_WRITE_PERMISSIONS_BACKUP_FULL:
            case DIALOG_EXPLAIN_WRITE_PERMISSIONS_BACKUP_PARTIAL:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_permission_explanation_title));
                builder.setMessage(getString(R.string.dialog_permission_explanation_write_message));
                builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dismissDialog(DIALOG_EXPLAIN_WRITE_PERMISSIONS_BACKUP_FULL);
                        requestWritePermissions(id, true);
                    }
                });
                alert = builder.create();
                break;
            case DIALOG_EXPLAIN_READ_PERMISSIONS_RESTORE_FULL:
            case DIALOG_EXPLAIN_READ_PERMISSIONS_RESTORE_PARTIAL:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_permission_explanation_title));
                builder.setMessage(getString(R.string.dialog_permission_explanation_read_message));
                builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dismissDialog(id);
                        requestReadPermissions(id, true);
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
            case DIALOG_BACKUP_DATASTORE_FULL:
            case DIALOG_BACKUP_DATASTORE_PARTIAL:
                EditText fileName = (EditText) dialog.findViewById(R.id.backup_filename);
                TextView fileExt = (TextView) dialog.findViewById(R.id.backup_filename_ext);
                fileExt.setText(getString(R.string.dialog_label_backup_extention_bak));
                fileName.setText(id == DIALOG_BACKUP_DATASTORE_FULL ? "jabtalk" : m_selectedGram.getLabel());
                Button btnBackup = (Button) dialog.findViewById(R.id.btn_Backup);
                btnBackup.setEnabled(true);
                int tl = fileName.getText().length();
                fileName.setSelection(0, tl);
                try {
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                } catch (NullPointerException ignored) {
                }
                break;
            case DIALOG_RESTORE_DATASTORE_FULL:
                m_selectedItemToRestore = -1;
                CheckBox chkRestore = (CheckBox) dialog.findViewById(R.id.chkRestoreOverData);
                chkRestore.setChecked(false);
                m_restoreAdapter.refresh(getString(R.string.dialog_label_backup_extention_bak));
                break;
            case DIALOG_RESTORE_DATASTORE_PARTIAL:
                m_selectedItemToRestore = -1;
                CheckBox chkRestoreOver = (CheckBox) dialog.findViewById(R.id.chkRestoreOverData);
                chkRestoreOver.setVisibility(View.GONE);
                chkRestoreOver.setChecked(false);
                m_restoreAdapter.refresh(getString(R.string.dialog_label_backup_extention_bak));
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

    private void requestWritePermissions(int dialog, boolean suppressRationalDialog) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (!suppressRationalDialog && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                showDialog(DIALOG_EXPLAIN_WRITE_PERMISSIONS_BACKUP_FULL);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, BACKUP_PERMISSIONS);
            }
        } else {
            showDialog(dialog);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void requestReadPermissions(int dialog, boolean suppressRationalDialog) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (!suppressRationalDialog && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                showDialog(dialog);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, dialog == DIALOG_RESTORE_DATASTORE_FULL ? RESTORE_PERMISSIONS_FULL : RESTORE_PERMISSIONS_PARTIAL);
            }
        } else {
            restoreData(dialog);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case BACKUP_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showDialog(DIALOG_BACKUP_DATASTORE_FULL);
                }
                break;
            case RESTORE_PERMISSIONS_FULL:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    restoreData(DIALOG_RESTORE_DATASTORE_FULL);
                }
                break;
            case RESTORE_PERMISSIONS_PARTIAL:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    restoreData(DIALOG_RESTORE_DATASTORE_PARTIAL);
                }
                break;
            default:
                break;
        }
    }

    private void backupData(String fileName) {
        if (backupTask == null || backupTask.getStatus() == Status.FINISHED) {
            backupTask = new BackupTask();
            backupTask.execute(fileName);
        } else {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "BackupTask in invalid state");
        }
    }

    private void restoreData(int dialog) {
        m_restoreAdapter.refresh(getString(R.string.dialog_label_backup_extention_bak));
        if (m_restoreAdapter.getCount() == 0) {
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                    getString(R.string.dialog_title_error));
            getIntent().putExtra(
                    JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                    getString(R.string.dialog_message_restore_file_not_found, JTApp
                            .getDataStore().getExternalStorageDirectory().getPath()));
            showDialog(DIALOG_GENERIC);
        } else {
            showDialog(dialog);
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


    private class RestoreTask extends AsyncTask<String, Void, Void> {

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
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Restore");
        }

        @Override
        protected Void doInBackground(String... params) {
            String fileName = params[0];
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

    private class BackupTask extends AsyncTask<String, Void, Void> {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        private boolean errorFlag = false;
        private String fileName = null;

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
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Backup");
        }

        @Override
        protected Void doInBackground(String... params) {
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
                        getString(R.string.dialog_message_backup_success, JTApp.getDataStore()
                                .getExternalStorageDirectory().getPath() + File.separator + fileName));
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
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Save");
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
