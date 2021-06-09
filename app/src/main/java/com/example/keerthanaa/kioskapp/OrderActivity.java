package com.example.keerthanaa.kioskapp;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.clover.common2.orders.v3.OrderUtils;
import com.clover.common2.payments.PayIntent;
import com.clover.sdk.util.CloverAccount;
import com.clover.sdk.v1.Intents;
import com.clover.sdk.v1.printer.job.PrintJob;
import com.clover.sdk.v1.printer.job.StaticOrderPrintJob;
import com.clover.sdk.v3.order.LineItem;
import com.clover.sdk.v3.order.Order;
import com.clover.sdk.v3.order.OrderConnector;
import com.clover.sdk.v3.payments.DataEntryLocation;
import com.clover.sdk.v3.payments.Payment;
import com.clover.sdk.v3.payments.TransactionSettings;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static com.clover.sdk.v1.Intents.ACTION_SECURE_PAY;

public class OrderActivity extends Activity implements
        RecognitionListener {
  List<LineItem> menuOrderList;
  Double subtotal = 0.0;
  Double total = 0.0;
  String orderId;
  Button exitButton;
  Button backButton;
  Button payButton;
  Button kitchenDisplayOrder;
  private OrderConnector orderConnector;
  private Account account;

  private String TAG = OrderActivity.class.getSimpleName();

  static final String ACTION_START_SECURE_PAYMENT = "clover.intent.action.START_SECURE_PAYMENT_EXTERNAL_DISPLAY";
  static final String ACTION_SECURE_PAYMENT_FINISH = "clover.intent.action.SECURE_PAYMENT_EXTERNAL_DISPLAY_FINISH";

  /* Used to handle permission request */
  private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

  private Model model;
  private SpeechService speechService;

  private class ExtDispLaunchResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction() == null) {
        return;
      }
      Log.d(TAG, "Got action " + intent.getAction());
      if (ACTION_SECURE_PAYMENT_FINISH.equals(intent.getAction())) {
        exitButton.setVisibility(View.VISIBLE);
        showPayFinishDialog();
      }
    }
  }

  private BroadcastReceiver extDispLaunchResultReceiver;

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
    setContentView(R.layout.activity_order);
    Intent orderIntent = getIntent();
    orderId = orderIntent.getStringExtra("orderId");
    int imageId = orderIntent.getIntExtra("imageId", 0);

    account = CloverAccount.getAccount(this);
    orderConnector = new OrderConnector(this, account, null);
    orderConnector.connect();
    Log.d(TAG, "order id " + orderId);
    Double tax;

    // Create an {@link MenuOrderAdapter}, whose data source is a list of
    // {@link MenuOrder}s. The adapter knows how to create list item views for each item
    // in the list.

    String menuName = "";
    ArrayList<MenuOrder> menuOrders = new ArrayList<MenuOrder>();
    menuOrderList = InventoryItemsActivity.getLineItemsList();
    if (menuOrderList != null) {
      for (int i = 0; i < menuOrderList.size(); i++) {
        Log.d(TAG, "item name : " + menuOrderList.get(i).getName());
        if (menuName.equals(menuOrderList.get(i).getName())) {

        } else {
          menuOrders.add(new MenuOrder(menuOrderList.get(i).getName(), (menuOrderList.get(i).getPrice()) * (menuOrderList.get(i).getUnitQty()), menuOrderList.get(i).getUnitQty()));
          subtotal = subtotal + (menuOrderList.get(i).getPrice()) * (menuOrderList.get(i).getUnitQty());
        }
        menuName = menuOrderList.get(i).getName();
      }
    }

    subtotal = subtotal / 100;
    tax = subtotal * .15;
    total = subtotal + tax;
    MenuOrderAdapter orderAdapter = new MenuOrderAdapter(this, menuOrders);

    // Get a reference to the ListView, and attach the adapter to the listView.
    ListView orderListView = (ListView) findViewById(R.id.listview_order);
    orderListView.setAdapter(orderAdapter);

    TextView subtotalView = (TextView) findViewById(R.id.subtotal);
    subtotalView.setText(getResources().getString(R.string.order_price, subtotal));

    TextView taxView = (TextView) findViewById(R.id.tax);
    taxView.setText(getResources().getString(R.string.tax, tax));

    TextView totalView = (TextView) findViewById(R.id.total);
    totalView.setText(getResources().getString(R.string.total, total));

    exitButton = (Button) findViewById(R.id.exit_button);
    exitButton.setVisibility(View.GONE);

    backButton = (Button) findViewById(R.id.back_button);
    backButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
    kitchenDisplayOrder = (Button) findViewById(R.id.kitchen_display_order);
    payButton = (Button) findViewById(R.id.pay);
    payButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        payAction();
      }
    });

    exitButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
        finishAffinity();
      }
    });


    kitchenDisplayOrder.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... voids) {
            try {
              sendOrderToPrinter(orderConnector.getOrder(orderId));
              orderConnector.fire2(orderId, false);
            } catch (Exception e) {
              Log.w(TAG, "send order to printer failed", e);
            }
            return null;
          }
        }.execute();

      }
    });

  }

  private void initModel() {
    StorageService.unpack(this, "model-en-us", "model",
            (model) -> {
              this.model = model;
              recognizeMicrophone();
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
    if (orderConnector != null) {
      orderConnector.disconnect();
      orderConnector = null;
    }
    if (speechService != null) {
      speechService.stop();
      speechService.shutdown();
      speechService = null;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Check if user has given permission to record audio, init the model after permission is granted
    int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
    } else {
      initModel();
    }
  }

  @Override
  public void onResult(String hypothesis) {
    parseSpeech(hypothesis);
  }

  @Override
  public void onFinalResult(String hypothesis) {
  }

  @Override
  public void onPartialResult(String hypothesis) {
  }

  @Override
  public void onError(Exception e) {
    setErrorState(e.getMessage());
  }

  @Override
  public void onTimeout() {
  }

  private void setErrorState(String message) {
  }
  private void recognizeMicrophone() {
    if (speechService != null) {
      speechService.stop();
      speechService = null;
    } else {
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
    if(speech.contains("go back")) {
      Log.d(TAG, "go back detected");
      this.finish();
    } else if(speech.contains("pay")) {
      Log.d(TAG, "pay detected");
      if (speechService != null) {
        speechService.stop();
        speechService.shutdown();
        speechService = null;
      }
      payAction();
    }
  }

  private void showPayFinishDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage("Payment Completed");
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });
    AlertDialog dialog = builder.create();
    dialog.show();
  }

  private void showPayInProgressDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage("Payment in Progress");
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });
    AlertDialog dialog = builder.create();
    dialog.show();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (extDispLaunchResultReceiver == null) {
      extDispLaunchResultReceiver = new ExtDispLaunchResultReceiver();
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_SECURE_PAYMENT_FINISH);
      registerReceiver(extDispLaunchResultReceiver, filter);
    }
  }

  @Override
  protected void onStop() {
    if (extDispLaunchResultReceiver != null) {
      unregisterReceiver(extDispLaunchResultReceiver);
      extDispLaunchResultReceiver = null;
    }
    super.onStop();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == 100 && resultCode == RESULT_OK) {
      Log.d(TAG, "on activity result");
      Payment payment = data.getParcelableExtra(Intents.EXTRA_PAYMENT);
      String paymentId = data.getStringExtra(Intents.EXTRA_PAYMENT_ID);
      String orderId = data.getStringExtra(Intents.EXTRA_ORDER_ID);

      // Toast.makeText(this, "Payment: " + payment.getJSONObject().toString(), Toast.LENGTH_LONG).show();
      Intent payIntent = new Intent(OrderActivity.this, PaymentActivity.class);
      payIntent.putExtra("total", total);
      startActivity(payIntent);
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void sendOrderToPrinter(final Order order) {
    int printerFlag = OrderUtils.isAllItemsPrinted(order, null) ? PrintJob.FLAG_REPRINT : PrintJob.FLAG_NONE;
    PrintJob pj = new StaticOrderPrintJob.Builder().markPrinted(true).order(order).flag(PrintJob.FLAG_REPRINT).build();
    print(pj);
  }

  public void print(PrintJob printJob) {
    printJob.print(this, CloverAccount.getAccount(this));
  }

  private void payAction(){
    payButton.setEnabled(false);
    backButton.setEnabled(false);
    kitchenDisplayOrder.setVisibility(View.GONE);

    Intent extDisaplyIntent = new Intent(ACTION_START_SECURE_PAYMENT);
    extDisaplyIntent.putExtra("orderId", orderId);
    extDisaplyIntent.putExtra("total", total);
    showPayInProgressDialog();
    securePay();
   }

  private void securePay() {
    Log.d(TAG, "orderid" + orderId);
    Intent intent = new Intent(ACTION_SECURE_PAY);
    PayIntent payIntent = new PayIntent.Builder()
        .amount((new Double(total * 100)).longValue())
        .orderId(orderId)
        .cardEntryMethods(Intents.CARD_ENTRY_METHOD_ALL)
        .build();
    payIntent.addTo(intent);
    startActivityForResult(intent, 100);
  }
}
