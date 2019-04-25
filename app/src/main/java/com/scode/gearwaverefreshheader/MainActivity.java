package com.scode.gearwaverefreshheader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.scwang.smartrefresh.layout.SmartRefreshLayout;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onRefresh(View view) {
        SmartRefreshLayout layout = findViewById(R.id.refresh);
        layout.autoRefresh();
    }
}
