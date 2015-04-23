package com.cuberob.elderlysupport;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.cuberob.WatchConfigKeys;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;


public class WatchFaceConfigActivity extends BaseActivity {

    private static final String TAG = "WatchFaceConfig";

    String mPeerId;
    EditText mDailyStepGoalEditText;
    ColorPicker mColorPicker;
    CheckBox mOverrideMenuCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); //Handles setup of GoogleApiClient
        setContentView(R.layout.activity_watch_face_config);

        mPeerId = getIntent().getStringExtra("android.support.wearable.watchface.extra.PEER_ID"); //WatchFaceCompanion.EXTRA_PEER_ID
        mDailyStepGoalEditText = (EditText) findViewById(R.id.dailyStepGoaldEditText);
        mOverrideMenuCheckBox = (CheckBox) findViewById(R.id.override_menu_checkBox);

        mColorPicker = (ColorPicker) findViewById(R.id.picker);
        mColorPicker.setShowOldCenterColor(false);
        mColorPicker.addSaturationBar((SaturationBar) findViewById(R.id.satBar));
        mColorPicker.addValueBar((ValueBar) findViewById(R.id.valBar));

        mColorPicker.setOnColorSelectedListener(new ColorPicker.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int i) {
                Log.d(TAG, "Color selected: " + mColorPicker.getColor());
                updateData();
            }
        });

        findViewById(R.id.sync_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateData();
            }
        });

    }

    @Override
    public void onConnected(Bundle bundle) {
        super.onConnected(bundle);

        new Thread(new Runnable() {
            @Override
            public void run() {
                Uri.Builder builder = new Uri.Builder();
                Uri uri = builder.scheme("wear").path(WatchConfigKeys.ACTIVITY_WATCH_CONFIG_PATH).authority(getLocalNodeId()).build();
                Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess() && dataItemResult.getDataItem() != null) {
                            DataItem configDataItem = dataItemResult.getDataItem();
                            DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
                            DataMap config = dataMapItem.getDataMap();
                            restoreSettings(config);
                        }
                    }
                });
            }
        }).start();
    }

    private String getLocalNodeId() {
        NodeApi.GetLocalNodeResult nodeResult = Wearable.NodeApi.getLocalNode(mGoogleApiClient).await();
        return nodeResult.getNode().getId();
    }

    private void restoreSettings(DataMap config) {
        mColorPicker.setColor(config.getInt(WatchConfigKeys.CLOCK_COLOR, -25344));
        mDailyStepGoalEditText.setText(Integer.toString(config.getInt(WatchConfigKeys.DAILY_STEP_GOAL, 1000)));
        mOverrideMenuCheckBox.setChecked(config.getBoolean(WatchConfigKeys.OVERRIDE_MENU, false));
    }

    @Override
    public void finish() {
        updateData();
        super.finish();
    }

    private void updateData(){
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WatchConfigKeys.ACTIVITY_WATCH_CONFIG_PATH);
        DataMap configToPut = putDataMapRequest.getDataMap();

        configToPut.putInt(WatchConfigKeys.DAILY_STEP_GOAL, Integer.parseInt(mDailyStepGoalEditText.getText().toString()));
        configToPut.putInt(WatchConfigKeys.CLOCK_COLOR, mColorPicker.getColor());
        configToPut.putBoolean(WatchConfigKeys.OVERRIDE_MENU, mOverrideMenuCheckBox.isChecked());
        configToPut.putLong("FORCE_SYNC", System.currentTimeMillis());

        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "putDataItem result status: " + dataItemResult.getStatus());
                        }
                    }
                });
    }

}
