package cat.xoriguer.notificacions;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NotificacionsPlugin.class); // abans de super.onCreate
        super.onCreate(savedInstanceState);
    }
}
