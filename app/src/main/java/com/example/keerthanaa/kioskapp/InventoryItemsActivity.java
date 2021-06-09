package com.example.keerthanaa.kioskapp;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.clover.common2.orders.v3.OrderUtils;
import com.clover.sdk.util.CloverAccount;
import com.clover.sdk.v1.printer.job.PrintJob;
import com.clover.sdk.v1.printer.job.StaticOrderPrintJob;
import com.clover.sdk.v1.printer.job.TestOrderPrintJob;
import com.clover.sdk.v3.inventory.Item;
import com.clover.sdk.v3.order.LineItem;
import com.clover.sdk.v3.order.Order;
import com.clover.sdk.v3.order.OrderConnector;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.content.ContentValues.TAG;

public class InventoryItemsActivity extends Activity implements
        RecognitionListener {
  private String TAG = InventoryItemsActivity.class.getSimpleName();
  private LineItem lineItem = null;
  private OrderConnector orderConnector;
  private Account account;
  Order order;
  private static List<LineItem> lineItemList;
  String orderId;
  private int menuQuantity = 1;
  int minMenuQuantity = 1;
  double totalPrice = 0;
  Double menuPrice = 0.0;
  String menuName, menuId;
  int menuImageId;
  ArrayList<CustomMenu> customMenus;
  TextView menuQuantityView;
  Button addToCartView;
  Button proceedView;
  LinearLayout menuQuantityLayout;

  /* Used to handle permission request */
  private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

  private Model model;
  private SpeechService speechService;

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
    setContentView(R.layout.activity_menu);


    // Create an ArrayList of customMenu objects
    customMenus = new ArrayList<CustomMenu>();
    List<Item> menuList = MainActivity.getMenuItemsList();
    customMenus.add(new CustomMenu(menuList.get(0).getName(), menuList.get(0).getPrice(), R.drawable.blt_burger, (menuList.get(0).getId())));
    customMenus.add(new CustomMenu(menuList.get(1).getName(), menuList.get(1).getPrice(), R.drawable.cheese_burger, (menuList.get(1).getId())));
    customMenus.add(new CustomMenu(menuList.get(2).getName(), menuList.get(2).getPrice(), R.drawable.chicken_burger, (menuList.get(2).getId())));
    customMenus.add(new CustomMenu(menuList.get(3).getName(), menuList.get(3).getPrice(), R.drawable.chicken_fingers, (menuList.get(3).getId())));
    customMenus.add(new CustomMenu(menuList.get(4).getName(), menuList.get(4).getPrice(), R.drawable.chicken_meal, (menuList.get(4).getId())));
    customMenus.add(new CustomMenu(menuList.get(5).getName(), menuList.get(5).getPrice(), R.drawable.combo_meal, (menuList.get(5).getId())));
    customMenus.add(new CustomMenu(menuList.get(6).getName(), menuList.get(6).getPrice(), R.drawable.finger_meal, (menuList.get(6).getId())));
    customMenus.add(new CustomMenu(menuList.get(7).getName(), menuList.get(7).getPrice(), R.drawable.fries, (menuList.get(7).getId())));
    customMenus.add(new CustomMenu(menuList.get(8).getName(), menuList.get(8).getPrice(), R.drawable.frosted_coke, (menuList.get(8).getId())));
    customMenus.add(new CustomMenu(menuList.get(9).getName(), menuList.get(9).getPrice(), R.drawable.water, (menuList.get(9).getId())));
//  customMenus.add(new CustomMenu(menuList.get(10).getName(), menuList.get(10).getPrice(), R.drawable.mushroom_burger, (menuList.get(10).getId())));
//  customMenus.add(new CustomMenu(menuList.get(11).getName(), menuList.get(11).getPrice(), R.drawable.vanilla_coffe, (menuList.get(11).getId())));


    // Create an {@link CustomMenuAdapter}, whose data source is a list of
    // {@link customMenus}s. The adapter knows how to create list item views for each item
    // in the list.
    CustomMenuAdapter menuAdapter = new CustomMenuAdapter(this, customMenus);

    // Get a reference to the ListView, and attach the adapter to the listView.
    GridView gridView = (GridView) findViewById(R.id.gridview_menu);
    gridView.setAdapter(menuAdapter);

    account = CloverAccount.getAccount(this);
    orderConnector = new OrderConnector(this, account, null);
    orderConnector.connect();


    ImageButton incrementButton = (ImageButton) findViewById(R.id.increment);
    ImageButton decrementButton = (ImageButton) findViewById(R.id.decrement);
    menuQuantityView = (TextView) findViewById(R.id.menu_quantity);
    addToCartView = (Button) findViewById(R.id.add_cart_text);
    proceedView = (Button) findViewById(R.id.proceed);
    menuQuantityLayout = (LinearLayout) findViewById(R.id.menu_quantity_layout);

    gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        itemClickAction(parent, view, position, id);
      }
    });

    incrementButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        menuQuantity = menuQuantity + 1;
        menuQuantityView.setText(String.valueOf(menuQuantity));
        if (addToCartView.getVisibility() == View.GONE) {
          addToCartView.setVisibility(View.VISIBLE);
        }
        totalPrice = menuQuantity * menuPrice;
        addToCartView.setText(getResources().getString(R.string.add_items_cart, menuQuantity, totalPrice));
      }
    });

    decrementButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (menuQuantity > minMenuQuantity) {
          menuQuantity = menuQuantity - 1;
          menuQuantityView.setText(String.valueOf(menuQuantity));
          if (addToCartView.getVisibility() == View.GONE) {
            addToCartView.setVisibility(View.VISIBLE);
          }
          totalPrice = menuQuantity * menuPrice;
          addToCartView.setText(getResources().getString(R.string.add_items_cart, menuQuantity, totalPrice));
        }
      }
    });

    addToCartView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        addToCartAction();
      }
    });

    proceedView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        proceedAction();
      }
    });

    createOrder();
  }

  private void createOrder() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... voids) {
        try {
          if (order == null) {
            order = orderConnector.createOrder(new Order());
            // orderId = order.getId();
          }
        } catch (Exception e) {
          Log.w(TAG, "create order failed", e);
        }
        return null;
      }
    }.execute();

  }

  private void addLineItemsToOrder(String name, String id) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... voids) {
        try {
          lineItem = orderConnector.addFixedPriceLineItem(order.getId(), id, name, null);
            lineItem.setUnitQty(menuQuantity);
          if (order.hasLineItems()) {
            lineItemList = new ArrayList<LineItem>(order.getLineItems());
          } else {
            lineItemList = new ArrayList<LineItem>();
          }

          if (lineItem != null) {
            lineItemList.add(lineItem);
            String lineItemId = lineItem.getId();

            if (menuQuantity > 1) {
              Log.d(TAG, "menu quantity > 1 : " + menuQuantity);
              List<String> lineItemIds = new ArrayList<String>();

              for (int j = 0; j < menuQuantity - 1; j++) {
                lineItemIds.add(lineItemId);
              }
              Map<String, List<LineItem>> newLineItems = orderConnector.createLineItemsFrom(order.getId(), order.getId(), lineItemIds);
              lineItemList.addAll(newLineItems.get(lineItemId));
            }
          }

          order = orderConnector.getOrder(order.getId());
          order.setLineItems(lineItemList);
          orderId = order.getId();
        } catch (Exception e) {
          Log.w(TAG, "create order failed", e);
        }
        return null;
      }
    }.execute();
  }

  private String dumpItem(Item item) {
    return item != null ? String.format("%s{id=%s, name=%s, price=%d}", Item.class.getSimpleName(), item.getId(), item.getName(), item.getPrice()) : null;
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
    if (lineItemList != null) {
      lineItemList.clear();
    }

    if (speechService != null) {
      speechService.stop();
      speechService.shutdown();
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

  public static List<LineItem> getLineItemsList() {
    return lineItemList;
  }

  private void sendOrderToPrinter(final Order order) {
    int printerFlag = OrderUtils.isAllItemsPrinted(order, null) ? PrintJob.FLAG_REPRINT : PrintJob.FLAG_NONE;
    PrintJob pj = new StaticOrderPrintJob.Builder().markPrinted(true).order(order).flag( PrintJob.FLAG_REPRINT ).build();
    print(pj);
  }

  public void print(PrintJob printJob) {
    printJob.print(this, CloverAccount.getAccount(this));
  }

  private void addToCartAction() {
    addLineItemsToOrder(menuName, menuId);
    Intent menuIntent = new Intent(InventoryItemsActivity.this, SingleMenuActivity.class);
    Bundle extras = new Bundle();
    extras.putString("Name", menuName);
    extras.putDouble("Price", menuPrice);
    extras.putInt("imageId", menuImageId);
    extras.putString("orderId", orderId);

    menuIntent.putExtras(extras);
    startActivity(menuIntent);
  }

  private void proceedAction() {
    Intent orderIntent = new Intent(InventoryItemsActivity.this, OrderActivity.class);
    orderIntent.putExtra("orderId", orderId);
    startActivity(orderIntent);
  }

  private void itemClickAction(AdapterView<?> parent, View view, int position, long id) {
    CustomMenu menu = customMenus.get(position);
    Log.d(TAG, menu.getMenuName() + menu.getMenuPrice());
    menuQuantity = 1;
    menuQuantityView.setText(String.valueOf(menuQuantity));
    menuPrice = (menu.getMenuPrice()) / 100;
    totalPrice = menuQuantity * menuPrice;
    addToCartView.setVisibility(View.VISIBLE);
    if (menuQuantityLayout.getVisibility() == View.GONE) {
      menuQuantityLayout.setVisibility(View.VISIBLE);
    }
    proceedView.setVisibility(View.VISIBLE);
    addToCartView.setText(getResources().getString(R.string.add_items_cart, menuQuantity, totalPrice));
    menuName = menu.getMenuName();
    menuImageId = menu.getImageResourceId();
    menuId = menu.getMenuId();
  }

  private void parseSpeech(String speech) {
    if (speech.contains("chicken burger")) {
      Log.d(TAG, "chicken burger detected");
      itemClickAction(null, null, 2, 0);
    } else if(speech.contains("shake")) {
      itemClickAction(null, null, 8, 0);
      Log.d(TAG, "shake detected");
    } else if(speech.contains("add to cart")) {
      Log.d(TAG, "add to cart detected");
      if (speechService != null) {
        speechService.stop();
        speechService.shutdown();
        speechService = null;
      }
      addToCartAction();
    } else if(speech.contains("proceed without selecting")) {
      Log.d(TAG, "proceed without selecting detected");
      if (speechService != null) {
        speechService.stop();
        speechService.shutdown();
        speechService = null;
      }
      proceedAction();
    }
  }

}



