package com.example.justuseusb.usb.base;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;


/**
 * Created by else on 2019-06-26.
 */
public class DeviceManager {
    private Context context;
    private UsbManager usbManager;
    private UsbDevice mDevice;
    private IUsbControl iUsbControl;
    private boolean isOpen = false;
    private PendingIntent permissionIntent;
    private IUsbDeviceListener iUsbDeviceListener;
    private int connectType;

    public DeviceManager(Context context, int connectType) {
        this.context = context;
        this.connectType=connectType;
        init();
    }

    private void init() {
        permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(usbReceiver, filter);
    }

    public void connect(int pid, int vid) {
        UsbDevice usbDevice = discover(pid, vid);
        if (usbDevice == null) {
            iUsbDeviceListener.onNoDeviceFind();
            return;
        }
        iUsbDeviceListener.onDeviceFind(usbDevice);
        if (usbManager.hasPermission(usbDevice)) {
            open(usbDevice);
        } else {
            usbManager.requestPermission(usbDevice, permissionIntent);
        }
    }

    //发现设备
    private UsbDevice discover(int pid, int vid) {
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice usbDevice = deviceIterator.next();
            Log.d("discover", "discover: -------" + usbDevice.getVendorId() + "-----" + usbDevice.getProductId());
            if (usbDevice.getVendorId() == vid && usbDevice.getProductId() == pid) {
                return usbDevice;
            }
        }
        return null;
    }


    //建立连接
    private void open(UsbDevice device) {
        if (isOpen) {
            close();
        }
        iUsbControl = UsbControlFactory.getUsbControl(device, usbManager, connectType);
        if(iUsbControl==null){
            Toast.makeText(context,"Usb IUsbControl初始错误", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            isOpen = iUsbControl.open();
            if (isOpen) {
                mDevice = device;
                iUsbDeviceListener.onDeviceConnect(iUsbControl);
            }
        } catch (IOException e) {
            e.printStackTrace();
            iUsbDeviceListener.onDeviceError(e);
        }
    }

    /**
     * 关闭当前连接
     */
    private void close() {
        if (isOpen) {
            try {
                iUsbControl.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            isOpen = false;
            iUsbControl = null;
        }

    }

    public void setIUsbDeviceListener(IUsbDeviceListener iUsbDeviceListener) {
        this.iUsbDeviceListener = iUsbDeviceListener;
    }

    public void release() {
        close();
        setIUsbDeviceListener(null);
        context.unregisterReceiver(usbReceiver);
        permissionIntent = null;
        context = null;
    }

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            open(device);
                        }
                    } else {
                        iUsbDeviceListener.onPermissionDenied("permission denied for device " + device.toString());
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (null != device && mDevice != null && mDevice.getDeviceName().equals(device.getDeviceName())) {
                    //如果是当前连接的设备被断开
                    iUsbDeviceListener.onDeviceDisconnect(device);
                    close();
                }
            }
        }
    };

}
