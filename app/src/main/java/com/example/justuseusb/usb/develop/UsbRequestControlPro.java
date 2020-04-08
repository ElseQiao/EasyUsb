package com.example.justuseusb.usb.develop;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import com.example.justuseusb.usb.base.CommonUsbControl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 使用的传输方式，运行版本7.1
 * 相对于UsbRequestControl：1.更改了写数据的方式，避免requestWait()接收到写入数据的UsbRequest
 * 2.增加了一个ByteBuffer，提高读取效率
 * Created by else on 2019-06-21.
 */
public class UsbRequestControlPro extends CommonUsbControl {
    private static final String TAG = "UsbRequestControlPro";
    static {
        System.loadLibrary("fixed_lib_usb");
    }
    private UsbInterface usbInterface;
    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;
    private UsbRequest inRequest;
    private ByteBuffer bytesBuffer0;
    private ByteBuffer bytesBuffer1;
    private ByteBuffer bytesBuffer2;
    //决定当前读取哪一个buffer
    private int bufferIndex = 0;

    public UsbRequestControlPro(UsbDevice device, UsbManager usbManager) {
        super(device, usbManager);
        bytesBuffer0 = ByteBuffer.allocate(bufferSize);
        bytesBuffer1 = ByteBuffer.allocate(bufferSize);
        bytesBuffer2 = ByteBuffer.allocate(bufferSize);
    }

    @Override
    public boolean open() throws IOException {
        if (mConnection == null) {
            return false;
        }
        boolean opened = false;
        try {
            //默认使用第一个端口
            UsbInterface usbInterface = mDevice.getInterface(0);
            Log.d(TAG, "UsbInterface 0----- " + usbInterface.toString());
            if (mConnection.claimInterface(usbInterface, true)) {
                Log.d(TAG, "claimInterface " + 0 + " SUCCESS");
            } else {
                Log.d(TAG, "claimInterface " + 0 + " FAIL");
                return false;
            }

            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                switch (usbInterface.getEndpoint(i).getDirection()) {
                    case UsbConstants.USB_DIR_OUT:
                        mWriteEndpoint = usbInterface.getEndpoint(i);
                        break;
                    case UsbConstants.USB_DIR_IN:
                        mReadEndpoint = usbInterface.getEndpoint(i);
                        break;

                }
            }

            if (mWriteEndpoint == null || mReadEndpoint == null) {
                throw new IllegalArgumentException("Endpoint init fail mWriteEndpoint: " + mWriteEndpoint +
                        "mReadEndpoint: " + mReadEndpoint);
            }

            UsbRequest requesti = new UsbRequest();
            if (requesti.initialize(mConnection, mReadEndpoint)) {
                inRequest = requesti;
            } else {
                throw new IllegalArgumentException("UsbRequest -inRequest initialize fail");
            }
            //默认添加一个队列等待端口数据返回
            opened = inRequest.queue(bytesBuffer0, bufferSize);
            inRequest.queue(bytesBuffer1, bufferSize);
        } finally {
            if (!opened) {
                close();
            }
            return opened;
        }
    }

    @Override
    public void close() throws IOException {
        if (inRequest != null) {
            inRequest.cancel();
            inRequest.close();
        }

        if (usbInterface != null) {
            mConnection.releaseInterface(usbInterface);
            usbInterface = null;
        }
        mConnection.close();
        mConnection = null;
    }

    /**
     * 这里多加了一个ByteBuffer
     */
    @Override
    public ByteBuffer read(int timeoutMillis) throws IOException {
        // long startT = System.currentTimeMillis();
        UsbRequest usbRequest;
        try {
            //获取从usb回来正在排队中的数据
            usbRequest = mConnection.requestWait();
        } catch (Exception ex) {
            Log.e("USB Error", ex.toString());
            return null;
        }
        //将下一包读取队列就绪，并处理本次数据
        if (usbRequest == inRequest) {
            ByteBuffer currentBytesBuffer = null;
            switch (bufferIndex) {
                case 0:
                    currentBytesBuffer = bytesBuffer0;
                    inRequest.queue(bytesBuffer2, bufferSize);
                    bufferIndex = 1;
                    break;
                case 1:
                    currentBytesBuffer = bytesBuffer1;
                    inRequest.queue(bytesBuffer0, bufferSize);
                    bufferIndex = 2;
                    break;
                case 2:
                    currentBytesBuffer = bytesBuffer2;
                    inRequest.queue(bytesBuffer1, bufferSize);
                    bufferIndex = 0;
                    break;
            }
            return currentBytesBuffer;
        }
        return null;
    }

    //    同步写数据方式
    @Override
    public int write(byte[] src, int timeoutMillis) {
        return mConnection.bulkTransfer(mWriteEndpoint,
                src, src.length, timeoutMillis);
    }

    @Override
    public int controlTransferProxy(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout) {
        return mConnection.controlTransfer(requestType, request, value, index, buffer, length, timeout);
    }

    /**
     * Reset USB Device by Send 0xb3
     */
    public void Reset() {
        byte[] bytes = new byte[3];
        mConnection.controlTransfer(128, 0xb3, 1, 0, bytes, 3, 300);
        if (bytes[0] != 0) {
            Log.i("control", ":" + Arrays.toString(bytes));
        }
    }

}
