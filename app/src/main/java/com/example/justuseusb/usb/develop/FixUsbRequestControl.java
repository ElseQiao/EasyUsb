package com.example.justuseusb.usb.develop;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.example.justuseusb.usb.base.IUsbControl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;



/**
 * 修改Android低层代码，解除Android usb 通信16kb的限制
 * Created by else on 2019-06-21.
 */
public class FixUsbRequestControl implements IUsbControl {
    private static final String TAG = "FixUsbRequestControl";
    static {
        System.loadLibrary("fixed_lib_usb");
    }
    private com.fixed.usb.UsbDeviceConnection usbDeviceConnection;
    private UsbDevice usbDevice;
    private UsbEndpoint epReceiveData;
    private UsbEndpoint epSendControl;
    private com.fixed.usb.UsbRequest usbRequestRead;
    private com.fixed.usb.UsbRequest usbRequestSendCommand;
    private ByteBuffer bytesBuffer1;
    private ByteBuffer bytesBuffer2;
    private int maxPacketSize;
    private int bufferIndex = 0;
    UsbInterface usbInterface;
    public FixUsbRequestControl(UsbDevice device, UsbManager usbManager) {
        UsbDeviceConnection usbDeviceConnection=usbManager.openDevice(device);
        this.usbDevice = device;
        this.usbDeviceConnection = new com.fixed.usb.UsbDeviceConnection(usbDevice, usbDeviceConnection.getFileDescriptor());
    }
    /**
     * 打开USB连接设备
     * Open USB Device
     */
    @Override
    public boolean open()  throws IOException {
        //判断USB连接是否为空,如果为空  返回false
        if (usbDeviceConnection == null) {
            return false;
        }
        //代表usb设备的一个接口(物理接口)
        usbInterface = usbDevice.getInterface(0);
        //返回一个UsbEndpoint,获得此接口指定的index节点
        epSendControl = usbInterface.getEndpoint(0);
        //获得此接口的节点数量
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            //获得此节点的地址
            int address = usbInterface.getEndpoint(i).getAddress();
            switch (address) {
                case 0:
                    break;
                case 0x02:
                    epSendControl = usbInterface.getEndpoint(i);
                    break;
                case 0x86:
                    epReceiveData = usbInterface.getEndpoint(i);
                    break;

            }
        }
        //代表usb连接的一个类,找到设备接口
        boolean test1=usbDeviceConnection.claimInterface(usbInterface, true);
        //表示usb请求数据的一个类,可以用于从USBDeviceConnection读写数据,用来传输大量数据和中断端点
        usbRequestRead = new com.fixed.usb.UsbRequest();
        //初始化请求,这样就可以读写给定的端点
        boolean test12= usbRequestRead.initialize(usbDeviceConnection, epReceiveData);
        usbRequestSendCommand = new com.fixed.usb.UsbRequest();
        boolean test13= usbRequestSendCommand.initialize(usbDeviceConnection, epSendControl);
        //得到端点的最大数据包大小
        maxPacketSize = epReceiveData.getMaxPacketSize() * 32 * 4;//* 4
        //创建一个字节缓冲区,缓冲区的容量为maxPacketSize
        this.bytesBuffer1 = ByteBuffer.allocate(maxPacketSize);
        this.bytesBuffer2 = ByteBuffer.allocate(maxPacketSize);
        //端点将会把给定的字节数排队给缓冲区,排队成功返回true ,通过.UsbDeviceConnection#requestWait获取
        usbRequestRead.queue(this.bytesBuffer1, maxPacketSize);
        return true;
}


    @Override
    public void close() throws IOException {
        if (usbRequestRead != null) {
            usbRequestRead.cancel();
            usbRequestRead.close();
        }

        if (usbRequestSendCommand != null) {
            usbRequestSendCommand.cancel();
            usbRequestSendCommand.close();
        }

        if (usbInterface != null) {
            usbDeviceConnection.releaseInterface(usbInterface);
            usbInterface = null;
        }
        usbDeviceConnection.close();
        usbDeviceConnection = null;
    }


    /**
     *
     */
    @Override
    public ByteBuffer read(int timeoutMillis) throws IOException {
        //long startT = System.currentTimeMillis();
        com.fixed.usb.UsbRequest usbRequest;
        try {
            //获取从usb回来正在排队中的数据
            usbRequest = usbDeviceConnection.requestWait();
        } catch (Exception ex) {
            Log.e("USB Error", ex.toString());
            return null;
        }
        //将下一包读取队列就绪，并处理本次数据
        if (usbRequest == usbRequestRead) {
            ByteBuffer currentBytesBuffer = null;
            switch (bufferIndex) {
                case 0:
                    currentBytesBuffer = bytesBuffer1;
                    usbRequestRead.queue(bytesBuffer2, maxPacketSize);
                    bufferIndex = 1;
                    break;
                case 1:
                    currentBytesBuffer = bytesBuffer2;
                    usbRequestRead.queue(bytesBuffer1, maxPacketSize);
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
        if (usbRequestSendCommand.queue(bytesBufferSendCommand, src.length)) {
            Log.d(TAG, "向设备发送指令：" + Arrays.toString(src));
            return src.length;
        }
        return -1;
    }

    @Override
    public int controlTransferProxy(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout) {
        return usbDeviceConnection.controlTransfer(requestType, request, value, index, buffer, length, timeout);

    }

    /**
     * Reset USB Device by Send 0xb3
     */
    public void Reset() {
        byte[] bytes = new byte[3];
        usbDeviceConnection.controlTransfer(128, 0xb3, 1, 0, bytes, 3, 300);
        if (bytes[0] != 0) {
            Log.i("control", ":" + Arrays.toString(bytes));
        }
    }

}
