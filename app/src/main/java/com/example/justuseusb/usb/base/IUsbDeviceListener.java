package com.example.justuseusb.usb.base;

import android.hardware.usb.UsbDevice;

/**
 * Created by else on 2019-06-25.
 */
public interface IUsbDeviceListener {

    void onDeviceFind(UsbDevice usbDevice);

    void onNoDeviceFind();

    void onPermissionDenied(String s);

    void onDeviceConnect(IUsbControl iUsbControl);

    void onDeviceError(Exception e);

    void onDeviceDisconnect(UsbDevice device);
}
