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
 * 9M 通信管理类
 * 在5.1系统运行存在断开连接的隐患，9m项目中已经通过修改native层解决了该问题。公共类暂未处理
 * Created by else on 2019-06-21.
 */
public class UsbRequestControl extends CommonUsbControl {
    private static final String TAG = "UsbRequestControlPro";

    private UsbInterface usbInterface;
    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;
    private UsbRequest outRequest;
    private UsbRequest inRequest;
    private ByteBuffer bytesBuffer0;
    private ByteBuffer bytesBuffer1;
    //决定当前读取哪一个buffer
    private int bufferIndex = 0;

    public UsbRequestControl(UsbDevice device, UsbManager usbManager) {
        super(device, usbManager);
        bytesBuffer0 = ByteBuffer.allocate(bufferSize);
        bytesBuffer1 = ByteBuffer.allocate(bufferSize);
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

            UsbRequest requesto = new UsbRequest();
            if (requesto.initialize(mConnection, mWriteEndpoint)) {
                outRequest = requesto;
            } else {
                throw new IllegalArgumentException("UsbRequest -outRequest initialize fail");
            }

            UsbRequest requesti = new UsbRequest();
            if (requesti.initialize(mConnection, mReadEndpoint)) {
                inRequest = requesti;
            } else {
                throw new IllegalArgumentException("UsbRequest -inRequest initialize fail");
            }
            //默认添加一个队列等待端口数据返回
            opened = inRequest.queue(bytesBuffer0, bufferSize);
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

        if (outRequest != null) {
            outRequest.cancel();
            outRequest.close();
        }

        if (usbInterface != null) {
            mConnection.releaseInterface(usbInterface);
            usbInterface = null;
        }
        mConnection.close();
        mConnection = null;
    }


    /**
     * usb读取是个耗时过程，这里使用2个ByteBuffer（bytesBuffer1，bytesBuffer0）排队读取。
     * 当处理其中一个接收数据前先将另一个加入队列，可以减少等待时间（只针对交互数据量大的情况）
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
                    inRequest.queue(bytesBuffer1, bufferSize);
                    bufferIndex = 1;
                    break;
                case 1:
                    currentBytesBuffer = bytesBuffer1;
                    inRequest.queue(bytesBuffer0, bufferSize);
                    bufferIndex = 0;
                    break;

            }
            return currentBytesBuffer;
        }
        return null;
    }

    @Override
    public int write(byte[] src, int timeoutMillis) {
        //指令大小为512字节，不需要考虑超出16384的情况.
        ByteBuffer bytesBufferSendCommand = ByteBuffer.allocate(src.length);
        System.arraycopy(src, 0, bytesBufferSendCommand.array(), 0, src.length);
        if (outRequest.queue(bytesBufferSendCommand, src.length)) {
            Log.d(TAG, "向设备发送指令：" + Arrays.toString(src));
            return src.length;
        }
        return -1;
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
