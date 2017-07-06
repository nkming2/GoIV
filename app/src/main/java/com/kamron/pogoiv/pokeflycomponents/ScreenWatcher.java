package com.kamron.pogoiv.pokeflycomponents;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.kamron.pogoiv.AutoAppraisal;
import com.kamron.pogoiv.Pokefly;
import com.kamron.pogoiv.ScreenGrabber;

/**
 * Created by johan on 2017-07-06.
 */

public class ScreenWatcher {

    private static final int SCREEN_SCAN_DELAY_MS = 1000;
    private static final int SCREEN_SCAN_RETRIES = 3;

    private LinearLayout touchView;
    private WindowManager.LayoutParams touchViewParams;
    private Point[] area = new Point[2];

    private Handler screenScanHandler;
    private Runnable screenScanRunnable;
    private int screenScanRetries;

    LinearLayout appraisalBox;
    private AutoAppraisal autoAppraisal;

    private Pokefly pokefly;
    private DisplayMetrics displayMetrics;

    public ScreenWatcher(Pokefly pokefly, LinearLayout appraisalBox, AutoAppraisal autoAppraisal) {
        this.pokefly = pokefly;
        this.displayMetrics = pokefly.getResources().getDisplayMetrics();
        this.appraisalBox = appraisalBox;
        this.autoAppraisal = autoAppraisal;

        initMarkerPixels();
    }

    /**
     * Initiates which pixels should be scanned during a screen scan to determine if the user is on the pokemon screen
     */
    private void initMarkerPixels() {
        area[0] = new Point(                // these values used to get "white" left of "power up"
                (int) Math.round(displayMetrics.widthPixels * 0.041667),
                (int) Math.round(displayMetrics.heightPixels * 0.8046875));
        area[1] = new Point(                // these values used to get greenish color in transfer button
                (int) Math.round(displayMetrics.widthPixels * 0.862445),
                (int) Math.round(displayMetrics.heightPixels * 0.9004));
    }

    /**
     * Scan a screen every time a user presses the screen, and trigger the quickIvPreview and IVButton to show if
     * he's on a pokemon screen.
     */
    public void watchScreen() {


        screenScanHandler = new Handler();
        screenScanRunnable = new ScreenScan();

        touchView = new LinearLayout(pokefly);
        touchViewParams = new WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSPARENT);
        touchViewParams.gravity = Gravity.LEFT | Gravity.TOP;

        touchView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { // Touch event outside of GoIV UI
                    // Let's check first to see if the user is performing an Appraisal
                    if (!pokefly.isBatterySaver() && appraisalBox.getVisibility() == View.VISIBLE) {
                        // Let autoAppraisal know that the user has touched the PokemonGo app while the
                        // appraisalBox was Visible.  This is our indication that the user has started a Pogo appraisal
                        autoAppraisal.screenTouched();
                    } else {
                        // Not appraising, let's check to see if they're looking at a pokemon screen.
                        // The postDelayed will wait SCREEN_SCAN_DELAY_MS after the user touches the screen before
                        // performing a scan of the screen to detect the pixels associated with a Pokemon screen.
                        screenScanHandler.removeCallbacks(screenScanRunnable);
                        screenScanHandler.postDelayed(screenScanRunnable, SCREEN_SCAN_DELAY_MS);
                        screenScanRetries = SCREEN_SCAN_RETRIES;
                    }
                }
                return false;
            }
        });
        WindowManager windowManager = (WindowManager) pokefly.getSystemService(pokefly.WINDOW_SERVICE);
        windowManager.addView(touchView, touchViewParams);
    }

    /**
     * scanPokemonScreen
     * Scans the device screen to check area[0] for the white and area[1] for the transfer button.
     * If both exist then the user is on the pokemon screen.
     */
    private boolean scanPokemonScreen() {
        @ColorInt int[] pixels = ScreenGrabber.getInstance().grabPixels(area);

        if (pixels != null) {
            boolean shouldShow =
                    (pixels[0] == Color.rgb(250, 250, 250) || pixels[0] == Color.rgb(249, 249, 249))
                            && pixels[1] == Color.rgb(28, 135, 150);
            return shouldShow;
        }
        return false;
    }

    private class ScreenScan implements Runnable {

        @Override public void run() {
            if (screenScanRetries > 0) {
                boolean ret = scanPokemonScreen();
                if (ret) {
                    screenScanRetries = 0; //skip further retries.
                    pokefly.getIvPreviewPrinter().printIVPreview();
                    pokefly.getIvButton().setShown(true, pokefly.getInfoShownSent());
                } else {
                    screenScanRetries--;
                    screenScanHandler.postDelayed(screenScanRunnable, SCREEN_SCAN_DELAY_MS);
                }
            }
        }
    }


    /**
     * Undoes the effects of watchScreen.
     */
    public void unwatchScreen() {
        WindowManager windowManager = (WindowManager) pokefly.getSystemService(pokefly.WINDOW_SERVICE);
        windowManager.removeView(touchView);
        touchViewParams = null;
        touchView = null;
        screenScanHandler.removeCallbacks(screenScanRunnable);
        screenScanRunnable = null;
        screenScanHandler = null;
    }
}
