package com.example.keerthanaa.kioskapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SingleMenuActivity extends Activity implements
        RecognitionListener {

  private String TAG = SingleMenuActivity.class.getSimpleName();
  private String menuName, orderId;

  static private final int STATE_START = 0;
  static private final int STATE_READY = 1;
  static private final int STATE_DONE = 2;
  static private final int STATE_FILE = 3;
  static private final int STATE_MIC = 4;

  /* Used to handle permission request */
  private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

  private Model model;
  private SpeechService speechService;
  private SpeechStreamService speechStreamService;

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
    setContentView(R.layout.activity_single_menu);

    Intent menuIntent = getIntent();
    Bundle extras = menuIntent.getExtras();
    if (extras == null) {

    } else {
      menuName = extras.getString("Name");
      Double menuPrice = extras.getDouble("Price");
      orderId = extras.getString("orderId");
      int imageId = extras.getInt("imageId");

      ImageView menuImage = (ImageView) findViewById(R.id.single_menu_image);
      menuImage.setImageResource(imageId);

      TextView menuNameView = (TextView) findViewById(R.id.single_menu_name);
      menuNameView.setText(menuName);

      TextView menuPriceView = (TextView) findViewById(R.id.single_menu_price);
      menuPriceView.setText(getResources().getString(R.string.single_menu_price, menuPrice));
    }

    // Check if user has given permission to record audio, init the model after permission is granted
    int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
    } else {
      initModel();
    }

    Button checkout = (Button) findViewById(R.id.checkout);
    Button extraMenu = (Button) findViewById(R.id.choose_extra_menu);
    extraMenu.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    checkout.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        proceedAction();
      }
    });

  }

  private void proceedAction() {
    Intent orderIntent = new Intent(SingleMenuActivity.this, OrderActivity.class);
    orderIntent.putExtra("orderId", orderId);
    Log.d(TAG, "order id "+ orderId);
    startActivity(orderIntent);
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
  protected void onDestroy() {
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
    parseSpeech(hypothesis);
  }

  @Override
  public void onFinalResult(String hypothesis) {
    //parseSpeech(hypothesis);
  }

  @Override
  public void onPartialResult(String hypothesis) {
    //parseSpeech(hypothesis);
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

  private void parseSpeech(String speech) {
    if(speech.contains("choose more items")) {
      //FIXME: Need to close the current activity here
      //finish();
    } else if(speech.contains("proceed to check out")) {
      setUiState(STATE_DONE);
      if (speechService != null) {
        speechService.stop();
        speechService.shutdown();
      }
      if (speechStreamService != null) {
        speechStreamService.stop();
      }
      proceedAction();
    }
  }
}
