package com.example.justuseusb.usb;//

import android.content.Context;

import com.example.justuseusb.usb.base.IUsbDataListener;

public interface IUsbService {
    int Connect(Context context,int pid,int vid,int transferType);

    int Disconnect();

    int SendCommand(byte[] commandData);

    void setUsbDataListener(IUsbDataListener iUsbDataListener);
}
