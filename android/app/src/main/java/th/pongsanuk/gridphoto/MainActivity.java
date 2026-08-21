package th.pongsanuk.gridphoto;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaveImagePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
