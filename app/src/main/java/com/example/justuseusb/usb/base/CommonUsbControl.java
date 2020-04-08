package com.example.justuseusb.usb.base;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 设备通信操作基类。
 * usb通信注意事项：
 * 设备间缓存在api 28以下最大为16*1024
 * 一个数据包的大小为64byte或512byte,由传输速度决定。（1024byte在Android中未提及）
 * USBRequest不支持EndPoint 0，
 * <p>
 * Created by else on 2019-06-20.
 */
public abstract class CommonUsbControl implements IUsbControl {
    //android 官方文档中有指出设备间的缓存最大值为16384
    public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
    public static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;
    //一次读取的数据量，根据自己设备的读取需求修改。UsbEndpoint#mMaxPacketSize
    protected int bufferSize=1024*32;
    protected final UsbDevice mDevice;
    protected UsbDeviceConnection mConnection;

    protected final Object mReadBufferLock = new Object();
    protected final Object mWriteBufferLock = new Object();

    protected byte[] mWriteBuffer;

    public CommonUsbControl(UsbDevice device, UsbManager usbManager) {
        mDevice = device;
        mConnection = usbManager.openDevice(device);
        if(bufferSize>DEFAULT_READ_BUFFER_SIZE){
            bufferSize=DEFAULT_READ_BUFFER_SIZE;
        }
        mWriteBuffer = new byte[DEFAULT_WRITE_BUFFER_SIZE];
    }

    @Override
    public abstract boolean open() throws IOException;

    @Override
    public abstract void close() throws IOException;

    @Override
    public abstract ByteBuffer read(int timeoutMillis) throws IOException;

    @Override
    public abstract int write(byte[] src, int timeoutMillis) throws IOException;

    @Override
    public abstract int controlTransferProxy(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout);

}
