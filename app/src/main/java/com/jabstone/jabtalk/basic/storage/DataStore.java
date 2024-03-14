package com.jabstone.jabtalk.basic.storage;


import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.exceptions.JabException;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


public class DataStore {

    public static final String JSON_ID = "id";
    public static final String JSON_PARENT_ID = "pid";
    public static final String JSON_IMAGE_EXT = "ie";
    public static final String JSON_AUDIO_EXT = "ae";
    public static final String JSON_LABEL = "l";
    public static final String JSON_PHRASE = "p";
    public static final String JSON_TYPE = "t";
    public static final String JSON_CHILDREN = "ch";
    public static final String JSON_TYPE_CATEGORY = "c";
    public static final String JSON_TYPE_WORD = "w";
    public static final String JSON_TYPE_ACTION = "a";
    public static final String JSON_HIDDEN = "h";
    public static final String FILE_JSON_DATASET = "jabtalk.json";
    public static final String FILE_JSON_DATASET_PARTIAL = "jabtalk.temp";

    // Legacy Tags
    public static final String JSON_CATEGORY_LIST = "cl";
    public static final String JSON_IDEOGRAM_LIST = "il";
    private static String TAG = DataStore.class.getSimpleName();

    private final String VERSION = "versionId";
    private final String JSON_IDEOGRAMS = "rig";
    private Map<String, Ideogram> m_ideogramMap = new HashMap<>();
    private Ideogram m_rootCategory = null;

    public DataStore() {
        this.refreshStore();
        if (getDataDirectory().equals(JTApp.getInstance().getFilesDir())) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, "Using internal storage");
        } else {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, "Using legacy external storage");
        }
    }

    public void refreshStore() {
        m_ideogramMap.clear();
        m_rootCategory = createRootCategory();
        File f = new File(getDataDirectory(), FILE_JSON_DATASET);
        try {
            if (f.exists()) {
                m_rootCategory = loadJsonFromFile(f, true);
            }
            m_ideogramMap.put(m_rootCategory.getId(), m_rootCategory);
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to load DataStore. Error Details: " + getStackTrace(e));
        }
    }

    public Ideogram getRootCategory() {
        return m_rootCategory;
    }

    private Ideogram createRootCategory() {
        Ideogram root = new Ideogram(Type.Category);
        root.setId(UUID.randomUUID().toString());
        return root;
    }

    public Map<String, Ideogram> getIdeogramMap() {
        return m_ideogramMap;
    }

    public Ideogram getIdeogram(String id) {
        Ideogram gram = m_ideogramMap.get(id);
        return gram;
    }

    // Remove ideogram from datastructure but don't commit changes until
    // commitDelete is called
    public void deleteIdeogram(String id) {

        Ideogram gram = m_ideogramMap.get(id);
        if (gram != null) {
            List<Ideogram> deleteList = new LinkedList<>();
            Ideogram parent = m_ideogramMap.get(gram.getParentId());
            deleteList.addAll(getAllIdeogramsForCategory(gram));
            if(parent != null && parent.getChildren(true) != null) {
                parent.getChildren(true).remove(gram);
            }

            // Delete all files for ideogram and ideogram's children
            for (Ideogram g : deleteList) {
                m_ideogramMap.remove(g.getId());
            }
        }
    }

    public void clearTempDirectory() {
        try {
            File path = getTempDirectory();
            if (path != null && path.exists()) {
                File[] files = path.listFiles();
                for (File f : files) {
                    deleteDir(f);
                }
            }
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to delete cache directory.  Error Details: " + getStackTrace(e));
        }
    }

    public File getTempDirectory() {
        File f = new File(JTApp.getInstance().getExternalCacheDir(), "jabcache");
        if (!f.exists()) {
            f.mkdirs();
        }
        File tempAudio = new File(f, "audio");
        if (!tempAudio.exists()) {
            tempAudio.mkdirs();
        }
        File tempImages = new File(f, "images");
        if (!tempImages.exists()) {
            tempImages.mkdirs();
        }

        return f;
    }


    public File getImageDirectory() {
        File imageDir = new File(getDataDirectory(), "images");
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }
        return imageDir;

    }

    public File getAudioDirectory() {
        File audioDir = new File(getDataDirectory(), "audio");
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        return audioDir;
    }

    public File getDataDirectory() {
        File f = JTApp.getInstance().getExternalFilesDir(null);
        File existingInstall = new File(f, FILE_JSON_DATASET);
        if (!existingInstall.exists()) {
            f = JTApp.getInstance().getFilesDir();
        }

        return f;
    }

    public File getExternalStorageDirectory() {
        File dir = Environment.getExternalStorageDirectory();
        return dir;
    }

    public File getExternalDownloadDirectory() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return dir;
    }

    public File getExternalDocumentsDirectory() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        return dir;
    }

    public void copyIdeogram(String sourceId, String targetId) throws JabException {

        Ideogram source = m_ideogramMap.get(sourceId);
        if (source == null) {
            throw new JabException(JTApp.getInstance().getApplicationContext()
                    .getString(com.jabstone.jabtalk.basic.R.string.copy_error_source_not_found));
        }

        Ideogram target = null;
        if (targetId != null) {
            target = m_ideogramMap.get(targetId);
        }
        cloneIdeogram(source, getDataDirectory(), target);
    }

    public void cutIdeogram(String sourceId, String targetId) throws JabException {

        Ideogram source = m_ideogramMap.get(sourceId);
        if (source == null) {
            throw new JabException(JTApp.getInstance().getApplicationContext()
                    .getString(com.jabstone.jabtalk.basic.R.string.copy_error_source_not_found));
        }

        Ideogram sourceParent = m_ideogramMap.get(source.getParentId());
        Ideogram target = null;
        if (targetId != null) {
            target = m_ideogramMap.get(targetId);
        }

        // Prevent pasting to invalid sources
        if (!isSafeToMove(source, target)) {
            throw new JabException(JTApp.getInstance().getApplicationContext()
                    .getString(com.jabstone.jabtalk.basic.R.string.cut_error_invalid_target));
        }

        // move ideogram to new target and remove from old parent
        switch (source.getType()) {
            case Category:
                source.setType(Type.Category);
                // Move item to new category
                source.setParentId(targetId);
                if (target != null) {
                    target.getChildren(true).add(source);
                }

                // Remove item from old parent
                if (sourceParent != null) {
                    sourceParent.getChildren(true).remove(source);
                }
                break;
            case Word:
                if (target == null) {
                    throw new JabException(
                            JTApp.getInstance()
                                    .getApplicationContext()
                                    .getString(
                                            com.jabstone.jabtalk.basic.R.string.cut_error_invalid_target_for_source));
                }
                source.setParentId(target.getId());
                target.getChildren(true).add(source);
                sourceParent.getChildren(true).remove(source);
                break;
        }
    }

    public boolean isSafeToMove(Ideogram source, Ideogram target) {
        return !((target.getType() == Type.Word) || //Don't allow pasting anything to a word
                (source.getId().equals(target.getId())) ||  //Don't allow pasting to itself
                (source.getParentId().equals(target.getId())) || //Don't allow pasting to parent twice
                (JTApp.getDataStore().getAllChildCategories(source).contains(target)));
    }

    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : children) {
                deleteDir(new File(dir, child));
            }
        }
        return dir.delete();
    }

    private void cloneIdeogram(Ideogram source, File baseDirectory, Ideogram target) throws JabException {
        String newId = UUID.randomUUID().toString();
        Ideogram clone = new Ideogram(source);

        clone.setId(newId);
        clone.setParentId(target.getId());

        if (source.getImagePath(baseDirectory) != null) {
            File imgSource = new File(source.getImagePath(baseDirectory));
            if (imgSource.exists()) {
                File imgDestination = new File(getImageDirectory(), clone.getId() + "."
                        + source.getImageExtension());
                copyFile(imgSource, imgDestination);
            }
        }
        if (source.getAudioPath(baseDirectory) != null) {
            File audSource = new File(source.getAudioPath(baseDirectory));
            if (audSource.exists()) {
                File audDestination = new File(getAudioDirectory(), clone.getId() + "."
                        + source.getAudioExtension());
                copyFile(audSource, audDestination);
            }
        }

        target.getChildren(true).add(clone);

        for (Ideogram child : source.getChildren(true)) {
            cloneIdeogram(child, baseDirectory, clone);
        }

        m_ideogramMap.put(clone.getId(), clone);
    }

    public boolean copyFile(File source, File destination) throws JabException {
        boolean result = false;
        if (source.exists()) {
            BufferedInputStream bis = null;
            BufferedOutputStream bos = null;
            try {
                bis = new BufferedInputStream(new FileInputStream(source));
                bos = new BufferedOutputStream(new FileOutputStream(destination));
                byte[] data = new byte[1024];
                int size;
                while ((size = bis.read(data, 0, 1024)) > -1) {
                    bos.write(data, 0, size);
                }
                bis.close();
                bos.flush();
                bos.close();
                result = true;
            } catch (IOException io) {
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                        "Failed to copy file.  Error Details: " + getStackTrace(io));
                throw new JabException("Failed to import file \"" + source.getName()
                        + "\". Click the menu and select Log to view error details.");
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
        return result;
    }

    public boolean saveScaledImage(File fileName, Bitmap bmp) throws JabException {

        boolean result;
        try {
            String ext = getFileExtention(fileName.getName(), false);
            if (ext != null && ext.equalsIgnoreCase("png")) {
                result = bmp.compress(Bitmap.CompressFormat.PNG, 85, new FileOutputStream(
                        fileName));
            } else {
                result = bmp.compress(Bitmap.CompressFormat.JPEG, 85, new FileOutputStream(
                        fileName));
            }
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Error scaling image. Error Details: " + getStackTrace(e));
            throw new JabException(
                    "Error shrinking image for use in JABtalk. Click the menu and select Log to view error details.");
        }
        return result;
    }

    public void saveDataStore() throws JabException {
        saveDataStore(new File(getDataDirectory(), FILE_JSON_DATASET), m_rootCategory);
    }

    public void saveDataStore(File output, Ideogram parent) throws JabException {
        removeOrphans();
        clearTempDirectory();

        JSONObject jsonObject = new JSONObject();
        OutputStreamWriter writer = null;
        try {
            jsonObject.put(VERSION, JTApp.DATASTORE_VERSION);
            JSONObject graph = new JSONObject(parent.toString());
            jsonObject.put(JSON_IDEOGRAMS, graph);
            writer = new OutputStreamWriter(new FileOutputStream(output), "UTF-8");
            writer.write(jsonObject.toString());
            writer.flush();
            writer.close();
        } catch (JSONException jse) {
            throw new JabException(
                    "Failed to save changes. Click the menu and select Log to view error details.");
        } catch (IOException io) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to save DataStore.  Error Details: " + getStackTrace(io));
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                        "It appears your SDCard is not currently accessible or is in read-only mode");
            }
            throw new JabException(
                    "Failed to save changes. Click the menu and select Log to view error details.");
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    public void backupDataStore(Uri fileUri, Ideogram category) throws JabException {
        ZipOutputStream zos = null;
        Set<String> ideogramIds = null;
        File categoryStore = null;

        try {

            //Write category as json object so it's included in backup
            if (!category.isRoot()) {
                String originalParent = category.getParentId();
                categoryStore = new File(getDataDirectory(), FILE_JSON_DATASET_PARTIAL);
                Ideogram root = createRootCategory();
                category.setParentId(root.getId());
                root.getChildren(true).add(category);
                saveDataStore(categoryStore, root);
                ideogramIds = getAllIdsForCategory(root);
                category.setParentId(originalParent);
            }

            OutputStream outputStream = JTApp.getInstance().getApplicationContext().getContentResolver().openOutputStream(fileUri);
            if(outputStream != null) {
                zos = new ZipOutputStream(outputStream);
                zos.setLevel(Deflater.DEFAULT_COMPRESSION);

                File dataDir = getDataDirectory();
                zipDirectory(dataDir, zos, ideogramIds);
                zos.flush();
                zos.close();

                if (categoryStore != null && categoryStore.exists()) {
                    categoryStore.delete();
                }

                if (!isBackupValid(fileUri)) {
                    throw new JabException("The backup file failed the post-backup validation check.");
                }
            } else {
                throw new JabException("Unable to get OutputStream for the provided URI: " + fileUri.toString());

            }
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to backup DataStore.  Error Details: " + getStackTrace(e));
            throw new JabException(
                    "There was a problem backing up your data. View to log for details.");
        } finally {
            try {
                if (zos != null) {
                    zos.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private boolean isBackupValid(final Uri uri) {
        ZipFile zipfile = null;
        InputStream inputStream = null;
        File tempFile = null;

        try {
            ContentResolver contentResolver = JTApp.getInstance().getContentResolver();
            inputStream = contentResolver.openInputStream(uri);

            if (inputStream != null) {
                // Create a temporary file to copy the content from the URI
                tempFile = createTempFileFromUri(uri);

                if (tempFile != null && tempFile.exists()) {
                    zipfile = new ZipFile(tempFile);
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (zipfile != null) {
                    zipfile.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete(); // Delete the temporary file once done
                }
            } catch (IOException ignored) {
            }
        }
    }

    // Method to create a temporary file from the Uri
    private File createTempFileFromUri(Uri uri) {
        File tempFile = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            ContentResolver contentResolver = JTApp.getInstance().getContentResolver();
            inputStream = contentResolver.openInputStream(uri);

            if (inputStream != null) {
                tempFile = File.createTempFile("temp", ".zip", JTApp.getInstance().getCacheDir());
                outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException ignored) {
            }
        }
        return tempFile;
    }



    public void restoreFullDataStore(Uri sourceFileUri) throws JabException {
        restoreDataStore(sourceFileUri, getDataDirectory(), true);
        refreshStore();
    }

    public void restorePartialDataStore(Uri sourceFileUri, Ideogram parent) throws JabException {
        try {
            clearTempDirectory();
            restoreDataStore(sourceFileUri, getTempDirectory(), false);
            Ideogram tempGram = loadJsonFromFile(new File(getTempDirectory(), FILE_JSON_DATASET), false);
            if (tempGram != null) {
                for (Ideogram child : tempGram.getChildren(true)) {
                    cloneIdeogram(child, getTempDirectory(), parent);
                }
                saveDataStore();
            } else {
                throw new JabException("Unable to restore backup from file. Unable to load json file.");
            }
        } catch (Exception e) {
            throw new JabException(getStackTrace(e));
        }
    }

    private void restoreDataStore(Uri sourceFileUri, File destinationDirectory, boolean clearDataStoreDirectory) throws JabException {

        byte[] buffer = new byte[1024];
        InputStream in = null;
        FileOutputStream out = null;

        try {
            String sourceFileName = sourceFileUri.getPath();

            if (!isBackupValid(sourceFileUri)) {
                throw new JabException("The backup file appears to be corrupt. Restore operation terminated.");
            }

            if (clearDataStoreDirectory) {
                deleteAllFiles();
            }

            File tempFile = createTempFileFromUri(sourceFileUri);
            ZipFile zipFile = new ZipFile(tempFile);

            Enumeration<? extends ZipEntry> entries;
            File dataDir = destinationDirectory;
            entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    File newDir = new File(dataDir, entry.getName());
                    if (!newDir.exists()) {
                        newDir.mkdirs();
                    }
                    continue;
                }

                int len;
                in = zipFile.getInputStream(entry);
                File target = new File(dataDir, entry.getName());
                if(!target.getCanonicalPath().startsWith(dataDir.getCanonicalPath())) {
                    throw new SecurityException("File being unzipped contains illegal path traversal characters.");
                } else {
                    out = new FileOutputStream(target);
                    while ((len = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, len);
                    }
                    in.close();
                    out.flush();
                    out.close();
                }
            }

            zipFile.close();

        } catch (Exception ioe) {
            throw new JabException(
                    "Failed to restore backup.  Error Details: " + getStackTrace(ioe));
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignore) {
            }
        }
    }


    private void deleteAllFiles() throws Exception {
        try {
            clearTempDirectory();
            File[] files = getDataDirectory().listFiles();
            for (File f : files) {
                if (!f.isDirectory()) {
                    f.delete();
                }
            }
            files = getAudioDirectory().listFiles();
            for (File f : files) {
                if (!f.isDirectory()) {
                    f.delete();
                }
            }
            files = getImageDirectory().listFiles();
            for (File f : files) {
                if (!f.isDirectory()) {
                    f.delete();
                }
            }
        } catch (NullPointerException np) {
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                throw new Exception(
                        "Error clearing files. It appears your SDCard is not currently accessible or is in read-only mode");
            }
        }
    }


    private void zipDirectory(File directory, ZipOutputStream zos, Set<String> ids) throws Exception {

        String[] dirList = directory.list(ids == null ? new DataStoreFileFilter() : new PartialDataStoreFileFilter(ids));

        byte[] readBuffer = new byte[1024];
        int bytesIn;

        for (String item : dirList) {
            File f = new File(directory, item);
            if (f.isDirectory()) {
                zipDirectory(f, zos, ids);
                continue;
            }

            FileInputStream fis = new FileInputStream(f);
            ZipEntry anEntry;
            if (f.getPath().contains(getImageDirectory().getAbsolutePath())
                    || f.getPath().contains(getAudioDirectory().getAbsolutePath())) {
                anEntry = new ZipEntry(f.getParentFile().getName() + File.separator
                        + f.getName());
            } else {
                anEntry = new ZipEntry(f.getName().equals(FILE_JSON_DATASET_PARTIAL) ? FILE_JSON_DATASET : f.getName());
            }
            zos.putNextEntry(anEntry);
            while ((bytesIn = fis.read(readBuffer)) != -1) {
                zos.write(readBuffer, 0, bytesIn);
            }
            fis.close();
        }
    }

    private Ideogram loadJsonFromFile(File source, boolean addItemsToMap) throws Exception {
        InputStreamReader reader = null;
        Ideogram parent = null;

        try {
            if (source != null && source.exists() && source.isFile() && source.length() > 0) {

                // Read file into character array
                char[] data = new char[(int) source.length()];

                reader = new InputStreamReader(new FileInputStream(source), "UTF-8");
                reader.read(data, 0, data.length);

                // Turn JSON String into object graph
                JSONObject jsonObject = new JSONObject(new String(data));
                String versionId = getJSONString(jsonObject, VERSION);

                if (!versionId.equals(JTApp.DATASTORE_VERSION)) {
                    // Convert store to new format
                    parent = createRootCategory();
                    LinkedList<Ideogram> categoryList = loadLegacyData(jsonObject);
                    parent.getChildren(true).addAll(categoryList);
                } else {
                    JSONObject jsonRoot = jsonObject.getJSONObject(JSON_IDEOGRAMS);
                    parent = inflateJSONCategory(jsonRoot, addItemsToMap);
                }
            }
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore) {
            }
        }
        return parent;
    }

    private LinkedList<Ideogram> loadLegacyData(JSONObject jsonObject) throws JSONException {
        String CATEGORYLIST = "categoryList";
        LinkedList<Ideogram> categoryList = new LinkedList<>();
        JSONArray jsonChildren = jsonObject.getJSONArray(CATEGORYLIST);
        for (int i = 0; i < jsonChildren.length(); i++) {
            JSONObject jsonChild = jsonChildren.getJSONObject(i);
            Ideogram category = inflateLegacyJSONCategory(jsonChild);
            category.setParentId(createRootCategory().getId());
            categoryList.add(category);
        }
        return categoryList;
    }

    private Ideogram inflateJSONCategory(JSONObject jsonCategory, boolean addItemsToMap) throws JSONException {
        Ideogram category = new Ideogram(Type.Category);
        category.setId(getJSONString(jsonCategory, DataStore.JSON_ID));
        category.setAudioExtension(getJSONString(jsonCategory, DataStore.JSON_AUDIO_EXT));
        category.setImageExtension(getJSONString(jsonCategory, DataStore.JSON_IMAGE_EXT));
        category.setLabel(getJSONString(jsonCategory, DataStore.JSON_LABEL));
        category.setPhrase(getJSONString(jsonCategory, DataStore.JSON_PHRASE));
        category.setParentId(getJSONString(jsonCategory, DataStore.JSON_PARENT_ID));
        category.setHidden(getJSONBoolean(jsonCategory, DataStore.JSON_HIDDEN));
        if (jsonCategory.has(DataStore.JSON_CHILDREN)) {
            JSONArray jsonChildren = jsonCategory.getJSONArray(DataStore.JSON_CHILDREN);
            for (int i = 0; i < jsonChildren.length(); i++) {
                JSONObject jsonChild = jsonChildren.getJSONObject(i);
                String type = getJSONString(jsonChild, DataStore.JSON_TYPE);
                if (Type.getType(type) == Type.Category) {
                    Ideogram subCategory = inflateJSONCategory(jsonChild, addItemsToMap);
                    category.getChildren(true).add(subCategory);
                } else {
                    Ideogram word = new Ideogram(Type.Word);
                    word.setId(getJSONString(jsonChild, DataStore.JSON_ID));
                    word.setAudioExtension(getJSONString(jsonChild, DataStore.JSON_AUDIO_EXT));
                    word.setImageExtension(getJSONString(jsonChild, DataStore.JSON_IMAGE_EXT));
                    word.setLabel(getJSONString(jsonChild, DataStore.JSON_LABEL));
                    word.setPhrase(getJSONString(jsonChild, DataStore.JSON_PHRASE));
                    word.setParentId(getJSONString(jsonChild, DataStore.JSON_PARENT_ID));
                    word.setHidden(getJSONBoolean(jsonChild, DataStore.JSON_HIDDEN));
                    category.getChildren(true).add(word);
                    if (addItemsToMap) {
                        m_ideogramMap.put(word.getId(), word);
                    }
                }

            }
        }
        if (addItemsToMap) {
            m_ideogramMap.put(category.getId(), category);
        }
        return category;
    }

    private Ideogram inflateLegacyJSONCategory(JSONObject jsonCategory) throws JSONException {
        Ideogram category = new Ideogram(Type.Category);
        category.setId(getJSONString(jsonCategory, DataStore.JSON_ID));
        category.setAudioExtension(getJSONString(jsonCategory, DataStore.JSON_AUDIO_EXT));
        category.setImageExtension(getJSONString(jsonCategory, DataStore.JSON_IMAGE_EXT));
        category.setLabel(getJSONString(jsonCategory, DataStore.JSON_LABEL));
        category.setPhrase(getJSONString(jsonCategory, DataStore.JSON_PHRASE));
        category.setParentId(getJSONString(jsonCategory, DataStore.JSON_PARENT_ID));
        category.setHidden(getJSONBoolean(jsonCategory, DataStore.JSON_HIDDEN));
        if (jsonCategory.has(DataStore.JSON_CATEGORY_LIST)) {
            JSONArray jsonCategoryList = jsonCategory.getJSONArray(DataStore.JSON_CATEGORY_LIST);
            for (int c = 0; c < jsonCategoryList.length(); c++) {
                JSONObject jsonSubcategory = jsonCategoryList.getJSONObject(c);
                Ideogram subCategory = inflateLegacyJSONCategory(jsonSubcategory);
                category.getChildren(true).add(subCategory);
            }
        }

        if (jsonCategory.has(DataStore.JSON_IDEOGRAM_LIST)) {
            JSONArray jsonIdeogramList = jsonCategory.getJSONArray(DataStore.JSON_IDEOGRAM_LIST);
            for (int i = 0; i < jsonIdeogramList.length(); i++) {
                JSONObject jsonIdeogram = jsonIdeogramList.getJSONObject(i);
                Ideogram word = new Ideogram(Type.Word);
                word.setId(getJSONString(jsonIdeogram, DataStore.JSON_ID));
                word.setAudioExtension(getJSONString(jsonIdeogram, DataStore.JSON_AUDIO_EXT));
                word.setImageExtension(getJSONString(jsonIdeogram, DataStore.JSON_IMAGE_EXT));
                word.setLabel(getJSONString(jsonIdeogram, DataStore.JSON_LABEL));
                word.setPhrase(getJSONString(jsonIdeogram, DataStore.JSON_PHRASE));
                word.setParentId(getJSONString(jsonIdeogram, DataStore.JSON_PARENT_ID));
                word.setHidden(getJSONBoolean(jsonIdeogram, DataStore.JSON_HIDDEN));
                category.getChildren(true).add(word);
                m_ideogramMap.put(word.getId(), word);
            }
        }

        m_ideogramMap.put(category.getId(), category);
        return category;
    }

    private String getJSONString(JSONObject obj, String key) {
        String value = "";
        if (obj.has(key)) {
            try {
                value = obj.getString(key);
            } catch (JSONException e) {
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                        "Failed to load JSON string from key: " + key);
            }
        }
        return value;
    }

    private boolean getJSONBoolean(JSONObject obj, String key) {
        boolean value = false;
        if (obj.has(key)) {
            try {
                value = obj.getBoolean(key);
            } catch (JSONException e) {
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                        "Failed to load JSON string from key: " + key);
            }
        }
        return value;
    }

    private Set<String> getAllIdsForCategory(Ideogram category) {
        Set<String> set = new HashSet<>();
        set.add(category.getId());
        for (Ideogram child : category.getChildren(true)) {
            if (child.getType() == Type.Category) {
                set.addAll(getAllIdsForCategory(child));
            } else {
                set.add(child.getId());
            }
        }
        return set;
    }

    private List<Ideogram> getAllIdeogramsForCategory(Ideogram category) {
        List<Ideogram> ideogramList = new LinkedList<>();
        ideogramList.add(category);
        for (Ideogram child : category.getChildren(true)) {
            if (child.getType() == Type.Category) {
                ideogramList.addAll(getAllIdeogramsForCategory(child));
            } else {
                ideogramList.add(child);
            }
        }
        return ideogramList;
    }

    private List<Ideogram> getAllChildCategories(Ideogram category) {
        List<Ideogram> ideogramList = new LinkedList<>();
        ideogramList.add(category);
        for (Ideogram child : category.getChildren(true)) {
            if (child.getType() == Type.Category) {
                ideogramList.addAll(getAllIdeogramsForCategory(child));
            }
        }
        return ideogramList;
    }

    private void removeOrphans() {
        File[] audioList = getAudioDirectory().listFiles();
        File[] imageList = getImageDirectory().listFiles();

        for (File f : audioList) {
            String name = f.getName();
            int ext = name.lastIndexOf(".");
            if (ext > 0) {
                name = name.substring(0, ext);
            }
            if (!m_ideogramMap.containsKey(name)
                    || m_ideogramMap.get(name).isSynthesizeButton()) {
                f.delete();
            }
        }

        for (File f : imageList) {
            String name = f.getName();
            int ext = name.lastIndexOf(".");
            if (ext > 0) {
                name = name.substring(0, ext);
            }
            if (!m_ideogramMap.containsKey(name) || m_ideogramMap.get(name).isTextButton()) {
                f.delete();
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

    public String getStackTrace(Exception e) {
        if (e.getMessage() != null) {
            return e.getMessage();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

}
