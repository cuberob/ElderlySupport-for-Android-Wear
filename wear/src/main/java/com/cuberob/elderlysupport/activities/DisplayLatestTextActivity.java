package com.cuberob.elderlysupport.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.cuberob.elderlysupport.R;
import com.cuberob.elderlysupport.views.ScrollingTextView;

public class DisplayLatestTextActivity extends Activity {

    ScrollingTextView mSmsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_latest_text);
        mSmsTextView = (ScrollingTextView) findViewById(R.id.sms_textview);
        mSmsTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Message Clicked
            }
        });
    }
}
