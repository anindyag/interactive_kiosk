package com.example.keerthanaa.kioskapp;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.clover.sdk.util.CloverAccount;
import com.clover.sdk.v3.inventory.InventoryConnector;
import com.clover.sdk.v3.inventory.Item;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity implements
        RecognitionListener {

  private String TAG = MainActivity.class.getSimpleName();
  static final String NAME_EXTERNAL_ACTIVITY = "com.clover.android.extdisplay.ExternalActivity";
  static final String ACTION_EXT_START_ACTIVITY = NAME_EXTERNAL_ACTIVITY + ".START_ACTIVITY";

  // this applies only to service wrapper
  private InventoryConnector inventoryConnector;

  private static List<Item> menuItemsList = new ArrayList<Item>();

  static private final int STATE_START = 0;
  static private final int STATE_READY = 1;
  static private final int STATE_DONE = 2;
  static private final int STATE_FILE = 3;
  static private final int STATE_MIC = 4;

  private Model model;
  private SpeechService speechService;
  private SpeechStreamService speechStreamService;

  /* Used to handle permission request */
  private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
        // Set the content to appear under the system bars so that the
        // content doesn't resize when the system bars hide and show.
        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        // Hide the nav bar and status bar
        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_FULLSCREEN);

    setContentView(R.layout.activity_main);

    inventoryConnector = new InventoryConnector(this, CloverAccount.getAccount(this), null);
    inventoryConnector.connect();

    startActivityOnSecondaryDisplay();

    // Check if user has given permission to record audio, init the model after permission is granted
    int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
    } else {
      initModel();
    }

    Button orderButton = (Button) findViewById(R.id.order_button);
    orderButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        toInventoryItemsActivityScreen();
        //startActivity(new Intent(MainActivity.this, InventoryItemsActivity.class));
        //startActivity(new Intent(MainActivity.this, VoskActivity.class));
      }
    });
  }

  private void initModel() {
    StorageService.unpack(this, "model-en-us", "model",
            (model) -> {
              this.model = model;
              setUiState(STATE_READY);
            },
            (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        initModel();
      } else {
        finish();
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    menuItemsList.clear();
    fetchObjectsFromServiceConnector();
  }

  private void fetchObjectsFromServiceConnector() {
    if (inventoryConnector != null) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          Item item = null;
          try {
            List<String> items = inventoryConnector.getItemIds();
            if (items != null) {
              for (int i = 0; i < items.size(); i++) {
                String itemId = items.get(i);
                // just print out the first few to the console
                if (i > 15) {
                  break;
                }
                item = inventoryConnector.getItem(itemId);
                menuItemsList.add(item);
                Log.v(TAG, "item = " + dumpItem(item));
              }
            }
          } catch (Exception e) {
            Log.e(TAG, "Error ", e);
          }
          return null;
        }

      }.execute();
    }
  }

  public static List<Item> getMenuItemsList() {
    return menuItemsList;
  }

  private String dumpItem(Item item) {
    return item != null ? String.format("%s{id=%s, name=%s, price=%d}", Item.class.getSimpleName(), item.getId(), item.getName(), item.getPrice()) : null;
  }

  @Override
  protected void onDestroy() {
    if (inventoryConnector != null) {
      inventoryConnector.disconnect();
      inventoryConnector = null;
    }
    super.onDestroy();

    if (speechService != null) {
      speechService.stop();
      speechService.shutdown();
    }

    if (speechStreamService != null) {
      speechStreamService.stop();
    }
  }

  @Override
  public void onResult(String hypothesis) {
    /*parseSpeech(hypothesis);
    setUiState(STATE_DONE);
    if (speechStreamService != null) {
      speechStreamService = null;
    }*/
  }

  @Override
  public void onFinalResult(String hypothesis) {
    /*parseSpeech(hypothesis);
    setUiState(STATE_DONE);
    if (speechStreamService != null) {
      speechStreamService = null;
    }*/
  }

  @Override
  public void onPartialResult(String hypothesis) {
    parseSpeech(hypothesis);
    setUiState(STATE_DONE);
    if (speechStreamService != null) {
      speechStreamService = null;
    }
  }

  @Override
  public void onError(Exception e) {
    setErrorState(e.getMessage());
  }

  @Override
  public void onTimeout() {
    setUiState(STATE_DONE);
  }

  private void setUiState(int state) {
    switch (state) {
      case STATE_START:
        //resultView.setText(R.string.preparing);
        //resultView.setMovementMethod(new ScrollingMovementMethod());
        //findViewById(R.id.recognize_file).setEnabled(false);
        //findViewById(R.id.recognize_mic).setEnabled(false);
        //findViewById(R.id.pause).setEnabled((false));
        break;
      case STATE_READY:
        recognizeMicrophone();
        //resultView.setText(R.string.ready);
        //((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        //findViewById(R.id.recognize_file).setEnabled(true);
        //findViewById(R.id.recognize_mic).setEnabled(true);
        //findViewById(R.id.pause).setEnabled((false));
        break;
      case STATE_DONE:
        //((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
        //((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        //findViewById(R.id.recognize_file).setEnabled(true);
        //findViewById(R.id.recognize_mic).setEnabled(true);
        //findViewById(R.id.pause).setEnabled((false));
        break;
      case STATE_FILE:
        //((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
        //resultView.setText(getString(R.string.starting));
        //findViewById(R.id.recognize_mic).setEnabled(false);
        //findViewById(R.id.recognize_file).setEnabled(true);
        //findViewById(R.id.pause).setEnabled((false));
        break;
      case STATE_MIC:
        //((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
        //resultView.setText(getString(R.string.say_something));
        //findViewById(R.id.recognize_file).setEnabled(false);
        //findViewById(R.id.recognize_mic).setEnabled(true);
        //findViewById(R.id.pause).setEnabled((true));
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + state);
    }
  }

  private void setErrorState(String message) {
    //resultView.setText(message);
    //((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
    //findViewById(R.id.recognize_file).setEnabled(false);
    //findViewById(R.id.recognize_mic).setEnabled(false);
  }
  private void recognizeMicrophone() {
    if (speechService != null) {
      setUiState(STATE_DONE);
      speechService.stop();
      speechService = null;
    } else {
      setUiState(STATE_MIC);
      try {
        Recognizer rec = new Recognizer(model, 16000.0f);
        speechService = new SpeechService(rec, 16000.0f);
        speechService.startListening(this);
      } catch (IOException e) {
        setErrorState(e.getMessage());
      }
    }
  }

  private void toInventoryItemsActivityScreen() {
    startActivity(new Intent(MainActivity.this, InventoryItemsActivity.class));
  }

  private void parseSpeech(String speech) {
    if (speech.contains("start an order")) {
      if (speechService != null) {
        speechService.stop();
        speechService.shutdown();
      }

      if (speechStreamService != null) {
        speechStreamService.stop();
      }
      toInventoryItemsActivityScreen();
    }
  }

  private void startActivityOnSecondaryDisplay() {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName("com.example.keerthanaa.kioskapp", "com.example.keerthanaa.kioskapp.ExternalDisplayActivity"));
    Intent extIntent = new Intent(ACTION_EXT_START_ACTIVITY);
    extIntent.putExtra("activity_intent", intent);
    sendBroadcast(extIntent);
  }
}
