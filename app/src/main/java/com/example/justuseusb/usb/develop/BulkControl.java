package com.example.justuseusb.usb.develop;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.example.justuseusb.usb.base.CommonUsbControl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;



/**
 * 通用同步通信类,一般直接使用该类控制usb。
 * 缺点：数据量大的时候，约大于10M/s（跟在usb线程中执行的逻辑以及cpu的性能有关）时有数据接收不全的风险
 * Created by else on 2019-06-26.
 */
public class BulkControl extends CommonUsbControl {
    private static final String TAG = "BulkControl";

    private UsbInterface usbInterface;
    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;
    private ByteBuffer readBuffer;

    public BulkControl(UsbDevice device, UsbManager usbManager) {
        super(device, usbManager);
        readBuffer = ByteBuffer.allocate(bufferSize);
    }

    @Override
    public boolean open() throws IOException {
        if (mConnection == null) {
            return false;
        }
        boolean opened = false;
        try {
            UsbInterface usbInterface = mDevice.getInterface(0);
            Log.d(TAG, "claimInterface " + 0 + " usbInterface-----" + usbInterface.toString());
            if (!mConnection.claimInterface(usbInterface, true)) {
                Log.d(TAG, "claimInterface " + 0 + " FAIL");
                //一般都是用usbInterface 0即可
                return false;
            }
            UsbEndpoint epOut = null;
            UsbEndpoint epIn = null;
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = usbInterface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    switch (ep.getDirection()) {
                        case UsbConstants.USB_DIR_OUT:
                            epOut = usbInterface.getEndpoint(i);
                            break;
                        case UsbConstants.USB_DIR_IN:
                            epIn = usbInterface.getEndpoint(i);
                            break;

                    }
                }
            }

            if (epOut == null || epIn == null) {
                //Requests on endpoint zero are not supported by this class;
                throw new IllegalArgumentException("not all endpoints found");
            }
            mWriteEndpoint = epOut;
            mReadEndpoint = epIn;
            opened = true;
        } finally {
            if (!opened) {
                close();
            }
            return opened;
        }
    }

    @Override
    public void close() throws IOException {
        if (usbInterface != null) {
            mConnection.releaseInterface(usbInterface);
            usbInterface = null;
        }
        mConnection.close();
        mConnection = null;
    }

    @Override
    public ByteBuffer read(int timeoutMillis) throws IOException {
        final int numBytesRead;
        synchronized (mReadBufferLock) {
            numBytesRead = mConnection.bulkTransfer(mReadEndpoint, readBuffer.array(), bufferSize,
                    timeoutMillis);
            if (numBytesRead < 0) {
                return null;
            }
        }
        readBuffer.position(numBytesRead);
        return readBuffer;
    }

    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        int offset = 0;

        while (offset < src.length) {
            final int writeLength;
            final int amtWritten;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;

                writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                    writeBuffer = mWriteBuffer;
                }

                amtWritten = mConnection.bulkTransfer(mWriteEndpoint,
                        writeBuffer, writeLength, timeoutMillis);
            }

            if (amtWritten <= 0) {
                throw new IOException("Error writing " + writeLength
                        + " bytes at offset " + offset + " length="
                        + src.length);
            }

            offset += amtWritten;
        }
        if (offset == 512) {
            Log.d(TAG, "向设备发送指令：" + Arrays.toString(src));
        }
        return offset;
    }


    @Override
    public int controlTransferProxy(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout) {
        return mConnection.controlTransfer(requestType, request, value, index, buffer, length, timeout);
    }


}
