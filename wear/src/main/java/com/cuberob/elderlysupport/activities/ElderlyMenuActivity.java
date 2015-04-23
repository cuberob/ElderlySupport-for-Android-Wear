package com.cuberob.elderlysupport.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import com.cuberob.elderlysupport.R;


public class ElderlyMenuActivity extends Activity {

    Button mPreviousButton, mNextButton;
    TextView mActionTextView;
    String[] mMenuItems = new String[] {"Berichten", "Hartslag", "Meer"};
    int mSelectedMenuItem = 0;
    static Vibrator v;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_elderly_menu);

        v = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        mPreviousButton = (Button) findViewById(R.id.previous_button);
        mNextButton = (Button) findViewById(R.id.next_button);
        mActionTextView = (TextView) findViewById(R.id.action_textView);
        mActionTextView.setText(mMenuItems[mSelectedMenuItem]);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoNext();
            }
        });
        mPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoPrev();
            }
        });
    }

    private void gotoPrev() {
        mSelectedMenuItem -= (mSelectedMenuItem == 0) ? 0 : 1;
        v.vibrate(50);
        Animation.AnimationListener listener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mActionTextView.setText(mMenuItems[mSelectedMenuItem]);
                mActionTextView.startAnimation(AnimationUtils.loadAnimation(ElderlyMenuActivity.this, android.R.anim.slide_in_left));
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };
        Animation animation = AnimationUtils.loadAnimation(ElderlyMenuActivity.this, android.R.anim.slide_out_right);
        animation.setAnimationListener(listener);
        mActionTextView.startAnimation(animation);
    }

    private void gotoNext() {
        mSelectedMenuItem += (mSelectedMenuItem < (mMenuItems.length-1)) ? 1 : -mSelectedMenuItem;
        v.vibrate(50);
        Animation.AnimationListener listener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mActionTextView.setText(mMenuItems[mSelectedMenuItem]);
                mActionTextView.startAnimation(AnimationUtils.loadAnimation(ElderlyMenuActivity.this, R.anim.slide_in_right));
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };
        Animation animation = AnimationUtils.loadAnimation(ElderlyMenuActivity.this, R.anim.slide_out_left);
        animation.setAnimationListener(listener);
        mActionTextView.startAnimation(animation);
    }
}
