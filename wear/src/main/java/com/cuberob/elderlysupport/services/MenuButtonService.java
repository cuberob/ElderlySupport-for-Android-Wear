package com.cuberob.elderlysupport.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.cuberob.elderlysupport.R;
import com.cuberob.elderlysupport.activities.ElderlyMenuActivity;

public class MenuButtonService extends Service {

    public static final String TAG = "MenuButtonService";

    View mMenuButtonView;

    public MenuButtonService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; //Not used
    }

    @Override
    public void onCreate() {
        setupButton();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if(mMenuButtonView != null){
            manager.removeView(mMenuButtonView);
        }
        super.onDestroy();
    }

    private void setupButton(){
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mMenuButtonView = inflater.inflate(R.layout.service_menu_button, null);
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        mMenuButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MenuButtonService.this, ElderlyMenuActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        });

        manager.addView(mMenuButtonView, params);
    }
}
