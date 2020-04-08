package com.example.justuseusb.usb.base;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.example.justuseusb.usb.develop.BulkControl;
import com.example.justuseusb.usb.develop.FixUsbRequestControl;
import com.example.justuseusb.usb.develop.UsbRequestControl;
import com.example.justuseusb.usb.develop.UsbRequestControlPro;


/**
 * BulkControl 为通用的usb控制类，一般usb通信直接选取这个类即可
 * UsbRequestControl 使用usbrequest的方式
 * UsbRequestControlPro 读取大数据量时（>17M/s）,用于7.1系统
 * FixUsbRequestControl  修改了Android源码，解除16kb的限制。可以通过提高单次通信数据量，提高读取效率，
 * 降低cpu占用
 * reated by else on 2019-06-27.
 */
public class UsbControlFactory {
    public static final int TYPE_BULK = 0x01;
    public static final int TYPE_USBREQUEST = 0x02;
    public static final int TYPE_USBREQUEST_PRO = 0x03;
    public static final int TYPE__USBREQUEST_FIX = 0x04;
    public static IUsbControl getUsbControl(UsbDevice device, UsbManager usbManager, int type) {
        switch (type) {
            case TYPE_BULK:
                return new BulkControl(device, usbManager);
            case TYPE_USBREQUEST:
                return new UsbRequestControl(device, usbManager);
            case TYPE_USBREQUEST_PRO:
                return new UsbRequestControlPro(device, usbManager);
            case TYPE__USBREQUEST_FIX:
                return new FixUsbRequestControl(device, usbManager);
        }
        return null;
    }
}
