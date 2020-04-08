package com.example.justuseusb.usb.base;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by else on 2019-06-20.
 */
public interface IUsbControl {
     boolean open() throws IOException;

     void close() throws IOException;

     ByteBuffer read(final int timeoutMillis) throws IOException;

     int write(final byte[] src, final int timeoutMillis) throws IOException;

     int controlTransferProxy(int requestType, int request, int value,
                              int index, byte[] buffer, int length, int timeout);
}
