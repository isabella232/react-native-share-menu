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
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
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

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        ContentResolver cr = mReactContext.getContentResolver();
        InputStream stream = null;
        try {
          stream = cr.openInputStream(fileUri);
          BitmapFactory.decodeStream(stream, null, options);
          int imageHeight = options.outHeight;
          int imageWidth = options.outWidth;
          String imageType = options.outMimeType;
          if (stream != null) {
            stream.close();
          }

          dict.putInt("width", imageWidth);
          dict.putInt("height", imageHeight);
          dict.putString("mimeType", imageType);

        } catch (Exception e) {
          e.printStackTrace();
        }
        dict.putString("url", fileUri.toString());
        dict.putString("thumbnail", "");
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

          BitmapFactory.Options options = new BitmapFactory.Options();
          options.inJustDecodeBounds = true;

          ContentResolver cr = mReactContext.getContentResolver();
          InputStream stream = null;
          try {
            stream = cr.openInputStream(uri);
            BitmapFactory.decodeStream(stream, null, options);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;
            String imageType = options.outMimeType;
            if (stream != null) {
              stream.close();
            }

            dict.putInt("width", imageWidth);
            dict.putInt("height", imageHeight);
            dict.putString("mimeType", imageType);

          } catch (Exception e) {
            e.printStackTrace();
          }
          dict.putString("url", uri.toString());
          dict.putString("thumbnail", "");
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

    Intent intent = currentActivity.getIntent();

    ReadableMap shared = extractShared(intent);
    successCallback.invoke(shared);
    clearSharedText();
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
