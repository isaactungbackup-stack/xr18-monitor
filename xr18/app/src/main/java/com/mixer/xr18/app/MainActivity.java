package com.mixer.xr18.app;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        TextView tv = findViewById(R.id.tv_status);
        tv.setText("XR18 Test App V1.0008 - If you see this, it works!");
    }
}
