package be.ugent.zeus.resto.client;

import be.ugent.zeus.resto.client.menu.MenuView;
import be.ugent.zeus.resto.client.data.MenuProvider;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ViewFlipper;
import be.ugent.zeus.resto.client.data.Menu;
import be.ugent.zeus.resto.client.menu.MenuUnavailableView;
import be.ugent.zeus.resto.client.menu.RestoClosedView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.util.Calendar;
import java.util.List;

/**
 *
 * @author Thomas Meire
 */
public class RestoMenu extends Activity {

  private static final int SWIPE_MIN_DISTANCE = 120;

  private static final int SWIPE_MAX_OFF_PATH = 250;

  private static final int SWIPE_THRESHOLD_VELOCITY = 200;

  private MenuProvider provider;

  private GestureDetector gestureDetector;

  private ViewFlipper flipper;

  private Animation slideLeftIn;

  private Animation slideLeftOut;

  private Animation slideRightIn;

  private Animation slideRightOut;

  private ServiceConnection connection = new ServiceConnection() {

    public void onServiceConnected(ComponentName cn, IBinder service) {
      provider = ((MenuProvider.LocalBinder) service).getService();
      updateMenuViews();
    }

    public void onServiceDisconnected(ComponentName cn) {
      provider = null;
    }
  };

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // start & bind to the data providere service
    bindService(new Intent("be.ugent.zeus.resto.client.data.MenuProvider"), connection, Context.BIND_AUTO_CREATE);

    setContentView(R.layout.main);

    flipper = (ViewFlipper) findViewById(R.id.flipper);
    flipper.setAnimateFirstView(true);

    gestureDetector = new GestureDetector(new MyGestureDetector());

    slideLeftIn = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
    slideLeftOut = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
    slideRightIn = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
    slideRightOut = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    unbindService(connection);
  }

  private MenuUpdateReceiver menuUpdateReceiver = new MenuUpdateReceiver();

  @Override
  public void onResume() {
    super.onResume();

    // register a broadcast receiver to get updated menu's
    registerReceiver(menuUpdateReceiver, new IntentFilter(RestoMenu.MenuUpdateReceiver.class.getName()));
  }

  @Override
  public void onPause() {
    super.onPause();

    // register a broadcast receiver to get updated menu's
    unregisterReceiver(menuUpdateReceiver);
  }

  private void updateMenuViews() {
    flipper.removeAllViews();

    if (provider != null) {
      for (Calendar calendar : getViewableDates()) {
        Log.i("[RestoMenu]", new SimpleDateFormat("EEEE").format(calendar.getTime()));
        Menu menu = provider.getMenu(calendar);

        if (menu != null) {
          if (menu.open) {
            MenuView view = new MenuView(this, calendar, menu);
            view.addTouchListener(new View.OnTouchListener() {
              public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
              }
            });
            flipper.addView(view);
          } else {
            // Add "resto closed" view
            RestoClosedView view = new RestoClosedView(this, calendar);
            flipper.addView(view);
          }
        } else {
          // maybe use a simple inflated view
          MenuUnavailableView view = new MenuUnavailableView(this, calendar);
          flipper.addView(view);
        }
      }
    }
  }

  private List<Calendar> getViewableDates() {
    List<Calendar> days = new ArrayList<Calendar>();

    Calendar instance = Calendar.getInstance();


    for (int i = 0; i
            < 5; i++) {
      if (instance.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
        instance.add(Calendar.DATE, 2);
      }
      if (instance.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
        instance.add(Calendar.DATE, 1);
      }
      days.add((Calendar) instance.clone());
      instance.add(Calendar.DATE, 1);
    }
    return days;
  }

  @Override
  public boolean onCreateOptionsMenu(android.view.Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.viewmap, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle item selection
    switch (item.getItemId()) {
      case R.id.show_map:
        // trigger intent for RestoMap Activity
        startActivity(new Intent(this, RestoMap.class));
        return true;
      case R.id.show_about:
        showAboutDialog();
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * About dialog based on code from Mobile Vikings for Android by Ben Van Daele
   */
  public void showAboutDialog() {
    Builder builder = new Builder(this);
    builder.setIcon(android.R.drawable.ic_dialog_info);
    builder.setTitle(getString(R.string.about));
    builder.setMessage(getAboutMessage());
    builder.setPositiveButton(getString(android.R.string.ok), null);
    AlertDialog dialog = builder.create();
    dialog.show();
  }

  public CharSequence getAboutMessage() {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(getString(R.string.app_name));
    stringBuilder.append(" ");
    stringBuilder.append(getVersionName());
    stringBuilder.append("\n\n");
    stringBuilder.append("http://github.com/blackskad/Resto-menu\n\n");
    return stringBuilder;
  }

  private String getVersionName() {
    try {
      ComponentName componentName = new ComponentName(this, RestoMenu.class);
      PackageInfo info = getPackageManager().getPackageInfo(componentName.getPackageName(), 0);
      return info.versionName;
    } catch (NameNotFoundException e) {
      // Won't happen, versionName is present in the manifest!
      return "";
    }
  }

  private boolean canFlipLeft() {
    return flipper.indexOfChild(flipper.getCurrentView()) > 0;
  }

  private boolean canFlipRight() {
    return flipper.indexOfChild(flipper.getCurrentView()) < flipper.getChildCount() - 1;
  }

  class MyGestureDetector extends SimpleOnGestureListener {

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      // only catch horizontal flings
      try {
        if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
          return false;
        }
        // right to left swipe
        if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY && canFlipRight()) {
          flipper.setInAnimation(slideLeftIn);
          flipper.setOutAnimation(slideLeftOut);
          flipper.showNext();
        } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY && canFlipLeft()) {
          flipper.setInAnimation(slideRightIn);
          flipper.setOutAnimation(slideRightOut);
          flipper.showPrevious();
        }
      } catch (Exception e) {
        // nothing
      }
      return false;
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return gestureDetector.onTouchEvent(event);
  }

  public class MenuUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context cntxt, Intent intent) {
      updateMenuViews();
    }
  }
}
