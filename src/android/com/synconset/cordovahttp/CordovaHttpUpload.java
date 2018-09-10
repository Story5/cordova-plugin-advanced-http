/**
 * A HTTP plugin for Cordova / Phonegap
 */
package com.synconset.cordovahttp;

import java.io.File;
import java.io.InputStream;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLHandshakeException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.github.kevinsawicki.http.HttpRequest;
import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;

class CordovaHttpUpload extends CordovaHttp implements Runnable {
    private JSONArray filePaths;
    private String name;
    private Context context;
    private String filePath;
    private boolean singleUpload;

    public CordovaHttpUpload(String urlString, Object params, JSONObject headers, String filePath, String name, int timeout, CallbackContext callbackContext) {
        super(urlString, params, headers, timeout, callbackContext);
        this.filePath = filePath;
        this.name = name;
        this.singleUpload = true;
    }

    public CordovaHttpUploads(String urlString, Object params, String serializerName, JSONObject headers, JSONArray filePaths, String name, int timeout, CallbackContext callbackContext, Context context) {
        super(urlString, params, serializerName, headers, timeout, callbackContext);
        this.filePaths = filePaths;
        this.name = name;
        this.context = context;
        this.singleUpload = false;
    }

    @Override
    public void run() {
        try {
            HttpRequest request = HttpRequest.post(this.getUrlString());

            this.prepareRequest(request);

            Set<?> set = (Set<?>)this.getParamsMap().entrySet();
            Iterator<?> i = set.iterator();
            while (i.hasNext()) {
                Entry<?, ?> e = (Entry<?, ?>)i.next();
                String key = (String)e.getKey();
                Object value = e.getValue();
                if (value instanceof Number) {
                    request.part(key, (Number)value);
                } else if (value instanceof String) {
                    request.part(key, (String)value);
                } else {
                    this.respondWithError("All parameters must be Numbers or Strings");
                    return;
                }
            }

            if (this.singleUpload) {
              URI uri = new URI(filePath);

              int filenameIndex = filePath.lastIndexOf('/');
              String filename = filePath.substring(filenameIndex + 1);

              int extIndex = filePath.lastIndexOf('.');
              String ext = filePath.substring(extIndex + 1);

              MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
              String mimeType = mimeTypeMap.getMimeTypeFromExtension(ext);
              
              request.part(this.name, filename, mimeType, new File(uri));
            } else {

              for (int j = 0; j < filePaths.length(); j++) {
                  String filePath = filePaths.getString(j);
                  Uri uri = Uri.parse(filePath);
                  FileDetail fileDetail = getFileDetailFromUri(context, uri);

                  // File Scheme
                  if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                      request.part(this.name, fileDetail.fileName, fileDetail.mimeType, new File(new URI(filePath)));
                  }
                  // Content Scheme
                  else if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()))  {
                      InputStream inputStream = context.getContentResolver().openInputStream(uri);
                      request.part(this.name, fileDetail.fileName, fileDetail.mimeType, inputStream);
                  }
              }

            }
            this.returnResponseObject(request);
        } catch (URISyntaxException e) {
            this.respondWithError("There was an error loading the file");
        } catch (JSONException e) {
            this.respondWithError("There was an error generating the response");
        } catch (HttpRequestException e) {
            this.handleHttpRequestException(e);
        } catch (Exception e) {
          this.respondWithError(e.getMessage());
        }
    }

    private FileDetail getFileDetailFromUri(final Context context, final Uri uri) {
        FileDetail fileDetail = null;
        if (uri != null) {
            fileDetail = new FileDetail();
            // File Scheme
            if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                File file = new File(uri.getPath());
                fileDetail.fileName = file.getName();
            }
            // Content Scheme
            else if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
                if (returnCursor != null && returnCursor.moveToFirst()) {
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    fileDetail.fileName = returnCursor.getString(nameIndex);
                    returnCursor.close();
                }
            }
            fileDetail.mimeType = getMimeTypeFromFileName(fileDetail.fileName);
        }
        return fileDetail;
    }

    private String getMimeTypeFromFileName(String fileName) {
        String mimeType = null;
        if (fileName != null && fileName.contains(".")) {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            int index = fileName.trim().lastIndexOf('.') + 1;
            String extension = fileName.trim().substring(index).toLowerCase();
            mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
        }
        return mimeType;
    }

    private class FileDetail {
        public String fileName;
        public String mimeType;
    }
}
