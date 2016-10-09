package com.jabstone.jabtalk.basic.storage;

import com.jabstone.jabtalk.basic.JTApp;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Set;

/**
 * Created by joebu on 10/8/16.
 */

public class PartialDataStoreFileFilter implements FilenameFilter {

    private Set<String> m_idList;

    public PartialDataStoreFileFilter(Set<String> idList) {
        m_idList = idList;
    }

    @Override
    public boolean accept(File file, String s) {
        return s.equals(DataStore.FILE_JSON_DATASET_PARTIAL) || s.equals("images") || s.equals("audio")
                || ((file.equals(JTApp.getDataStore().getImageDirectory()) || file.equals(JTApp.getDataStore().getAudioDirectory())) && m_idList.contains(removeFileExtention(s)));
    }

    private String removeFileExtention(String fileName) {
        int pos = fileName.lastIndexOf(".");
        if (pos >= 0) {
            return fileName.substring(0, pos);
        }
        return null;
    }
}
