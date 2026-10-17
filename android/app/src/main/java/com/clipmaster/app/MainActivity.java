package com.clipmaster.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(VideoEnginePlugin.class);
        registerPlugin(RuStoreReviewPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
