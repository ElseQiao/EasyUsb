package com.example.justuseusb;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.justuseusb.usb.IUsbService;
import com.example.justuseusb.usb.USB;
import com.example.justuseusb.usb.base.IUsbDataListener;
import com.example.justuseusb.usb.base.UsbControlFactory;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    Button button;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button=findViewById(R.id.test);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initUsb();
            }
        });
    }
    IUsbService usbService;
    private void initUsb() {
        usbService=new USB();
        usbService.Connect(this,0x2012, 0x2009, UsbControlFactory.TYPE__USBREQUEST_FIX);
        read();
    }

    private void send(){
        byte[] test={1,2,3,4};
        usbService.SendCommand(test);
    }

    int i=0;
    private void read(){
        usbService.setUsbDataListener(new IUsbDataListener() {
            @Override
            public void dealUsbData(byte[] bytes) {
                if(i<10){
                    Log.d(TAG, "dealUsbData: --------"+ Arrays.toString(bytes));
                    i++;
                }
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(usbService!=null)usbService.Disconnect();
    }
}
