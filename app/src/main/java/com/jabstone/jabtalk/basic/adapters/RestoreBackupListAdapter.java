package com.jabstone.jabtalk.basic.adapters;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RestoreBackupListAdapter extends BaseAdapter {

    private static String TAG = RestoreBackupListAdapter.class.getSimpleName();
    DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());
    private Context m_context;
    private List<File> m_backupFileList = null;

    public RestoreBackupListAdapter(Context context) {
        m_context = context;
        refresh();
    }

    public void refresh() {
        m_backupFileList = JTApp.getDataStore().getBackupFiles();
    }

    public int getCount() {
        return m_backupFileList != null ? m_backupFileList.size() : 0;
    }

    public Object getItem(int position) {
        if (position < m_backupFileList.size()) {
            return m_backupFileList.get(position);
        } else {
            return null;
        }
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) m_context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater
                    .inflate(
                            R.layout.backup_restore_item,
                            parent, false);
        }

        try {

            TextView fileName = (TextView) convertView
                    .findViewById(R.id.restore_filename);
            TextView fileDate = (TextView) convertView
                    .findViewById(R.id.restore_filedate);
            TextView fileSize = (TextView) convertView
                    .findViewById(R.id.restore_filesize);

            File f = m_backupFileList.get(position);
            fileName.setText(f.getName());
            fileDate.setText(df.format(new Date(f.lastModified())));
            fileSize.setText(Formatter.formatShortFileSize(m_context, f.length()));

        } catch (Exception fnf) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, fnf.getMessage());
        }
        return convertView;
    }

}
