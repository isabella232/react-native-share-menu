package com.meedan;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShareMenuModule extends ReactContextBaseJavaModule implements ActivityEventListener {

  // Events
  final String NEW_SHARE_EVENT = "NewShareEvent";

  // Keys
  final String MIME_TYPE_KEY = "mimeType";
  final String DATA_KEY = "data";

  private final ReactApplicationContext mReactContext;

  public ShareMenuModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.mReactContext = reactContext;

    mReactContext.addActivityEventListener(this);
  }

  @NonNull
  @Override
  public String getName() {
    return "ShareMenu";
  }

  @Nullable
  private ReadableMap extractShared(Intent intent)  {
    String type = intent.getType();

    if (type == null) {
      return null;
    }

    String action = intent.getAction();

    WritableMap data = Arguments.createMap();
    data.putString(MIME_TYPE_KEY, type);

    if (Intent.ACTION_SEND.equals(action)) {
      if ("text/plain".equals(type)) {
        data.putString(DATA_KEY, intent.getStringExtra(Intent.EXTRA_TEXT));
        return data;
      }

      Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      if (fileUri != null) {
        WritableArray dataArr = Arguments.createArray();
        WritableMap dict = Arguments.createMap();

        UUID uuid = UUID.randomUUID();

        //get real path
        String realPath = GetRealPathFromURI(mReactContext, fileUri);
        if(realPath == null) {
          realPath = fileUri.toString();
        }

        //get mime
        ContentResolver cR = mReactContext.getContentResolver();
        String mimeType = cR.getType(fileUri);
        dict.putString("mimeType", mimeType);

        if(mimeType.contains("video")) {
          //get video data
          WritableMap metaDict = GetVideoMeta(fileUri);
          dict.merge(metaDict);

          //create thumbnail
          dict.putString("thumbnail", GetVideoThumbnailBase64(realPath));
        }
        else {
          //get image meta
          BitmapFactory.Options options = GetImageMeta(fileUri);
          int imageHeight = options.outHeight;
          int imageWidth = options.outWidth;

          dict.putInt("width", imageWidth);
          dict.putInt("height", imageHeight);
          dict.putString("thumbnail", "");
        }

        dict.putString("url", "file://".concat(realPath));
        dict.putString("preview_url", fileUri.toString());
        dict.putString("Id", uuid.toString());
        dataArr.pushMap(dict);

        data.putArray(DATA_KEY, dataArr);
        return data;
      }
    } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
      ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
      if (fileUris != null) {

        WritableArray dataArr = Arguments.createArray();
        for (Uri uri : fileUris) {
          WritableMap dict = Arguments.createMap();

          UUID uuid = UUID.randomUUID();

          //get real path
          String realPath = GetRealPathFromURI(mReactContext, uri);
          if(realPath == null) {
            realPath = uri.toString();
          }

          //get mime
          ContentResolver cR = mReactContext.getContentResolver();
          String mimeType = cR.getType(uri);
          dict.putString("mimeType", mimeType);

          if(mimeType.contains("video")) {
            //get video data
            WritableMap metaDict = GetVideoMeta(uri);
            dict.merge(metaDict);

            //create thumbnail
            dict.putString("thumbnail", GetVideoThumbnailBase64(realPath));
          }
          else {
            //get image meta
            BitmapFactory.Options options = GetImageMeta(uri);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;

            dict.putInt("width", imageWidth);
            dict.putInt("height", imageHeight);
            dict.putString("thumbnail", "");
          }

          dict.putString("url", "file://".concat(realPath));
          dict.putString("preview_url", uri.toString());
          dict.putString("Id", uuid.toString());
          dataArr.pushMap(dict);
        }
        data.putArray(DATA_KEY, dataArr);
        return data;
      }
    }

    return null;
  }

  @ReactMethod
  public void getSharedText(Callback successCallback) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      successCallback.invoke(null);
      return;
    }

    //if this isn't the roor activity (Google Files launches like this) then make sure it is
    if (!currentActivity.isTaskRoot()) {
      Intent newIntent = new Intent(currentActivity.getIntent());
      newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      currentActivity.startActivity(newIntent);

      ReadableMap shared = extractShared(newIntent);
      successCallback.invoke(shared);
      clearSharedText();
      currentActivity.finish();
      return;
    }

    Intent intent = currentActivity.getIntent();
    ReadableMap shared = extractShared(intent);
    successCallback.invoke(shared);
    clearSharedText();
  }

  private String GetVideoThumbnailBase64(String uri) {
    String output = "";

    try {
      Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(uri.toString(), MediaStore.Video.Thumbnails.MINI_KIND);
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
      byte[] byteArray = byteArrayOutputStream.toByteArray();

      output = Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return output;
  }

  private WritableMap GetVideoMeta(Uri uri) {

    WritableMap dict = Arguments.createMap();

    try {
      MediaMetadataRetriever retriever = new MediaMetadataRetriever();
      retriever.setDataSource(mReactContext, uri);
      int videoWidth = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
      int videoHeight = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
      String videoType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
      dict.putInt("width", videoWidth);
      dict.putInt("height", videoHeight);
      retriever.release();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return dict;
  }

  private BitmapFactory.Options GetImageMeta(Uri uri) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;

    ContentResolver cr = mReactContext.getContentResolver();
    InputStream stream = null;
    try {
      stream = cr.openInputStream(uri);
      BitmapFactory.decodeStream(stream, null, options);
      if (stream != null) {
        stream.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return options;
  }

  public String GetRealPathFromURI(Context context, Uri contentUri) {
    Cursor cursor = null;
    try {
      String[] proj = { MediaStore.MediaColumns.DATA };
      cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
      int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
      cursor.moveToFirst();
      return cursor.getString(column_index);
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  private void dispatchEvent(ReadableMap shared) {
    if (mReactContext == null || !mReactContext.hasActiveCatalystInstance()) {
      return;
    }

    mReactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(NEW_SHARE_EVENT, shared);
  }

  public void clearSharedText() {
    Activity mActivity = getCurrentActivity();
    
    if(mActivity == null) { return; }

    Intent intent = mActivity.getIntent();
    String type = intent.getType();

    if (type == null) {
      return;
    }

    if ("text/plain".equals(type)) {
      intent.removeExtra(Intent.EXTRA_TEXT);
      return;
    }

    intent.removeExtra(Intent.EXTRA_STREAM);
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    // DO nothing
  }

  @Override
  public void onNewIntent(Intent intent) {
    // Possibly received a new share while the app was already running

    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      return;
    }

    ReadableMap shared = extractShared(intent);
    dispatchEvent(shared);

    // Update intent in case the user calls `getSharedText` again
    currentActivity.setIntent(intent);
  }
}
