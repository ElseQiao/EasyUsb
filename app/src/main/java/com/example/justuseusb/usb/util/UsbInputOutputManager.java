/* Copyright 2011 Google Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: http://code.google.com/p/usb-serial-for-android/
 */

package com.example.justuseusb.usb.util;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.example.justuseusb.usb.base.IUsbControl;



/**
 * Edit by else on 2019-06-27.
 */
public class UsbInputOutputManager implements Runnable {

    private static final String TAG = UsbInputOutputManager.class.getSimpleName();
    private final int STOPPED = 0;
    private final int RUNNING = 1;
    private final int STOPPING = 2;
    private static final boolean DEBUG = false;

    private int READ_WAIT_MILLIS;
    private int READ_RATE_MILLIS;
    private final IUsbControl mDriver;

    // Synchronized by 'this'
    private int mState = STOPPED;
    // Synchronized by 'this'
    private Listener mListener;

    public UsbInputOutputManager(IUsbControl driver, Listener listener) {
        mDriver = driver;
        mListener = listener;
    }

    public synchronized void setListener(Listener listener) {
        mListener = listener;
    }

    public synchronized Listener getListener() {
        return mListener;
    }

    public synchronized void stop() {
        if (getState() == RUNNING) {
            Log.i(TAG, "Stop requested");
            mState = STOPPING;
        }
    }


    private synchronized int getState() {
        return mState;
    }

    /**
     * Continuously services the read and write buffers until {@link #stop()} is
     * called, or until a driver exception is raised.
     */
    @Override
    public void run() {
        synchronized (this) {
            if (getState() != STOPPED) {
                throw new IllegalStateException("Already running.");
            }
            mState = RUNNING;
        }

        Log.i(TAG, "Running ..");
        try {
            while (true) {
                if (getState() != RUNNING) {
                    break;
                }
                step();
                //Thread.sleep(READ_RATE_MILLIS);//降低读取的频率
            }
        } catch (Exception e) {
            Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
            final Listener listener = getListener();
            if (listener != null) {
                listener.onRunError(e);
            }
        } finally {
            synchronized (this) {
                mState = STOPPED;
                Log.i(TAG, "Stopped.");
            }
        }
    }

    private void step() throws IOException {

        ByteBuffer byteBuffer = mDriver.read(READ_WAIT_MILLIS);
        if (byteBuffer != null && byteBuffer.position() > 0) {
            int length = byteBuffer.position();
            if (DEBUG) Log.d(TAG, "Read data len=" + length);
            final Listener listener = getListener();
            if (listener != null) {
                final byte[] data = new byte[length];
                System.arraycopy(byteBuffer.array(), 0, data, 0, byteBuffer.position());
                listener.onNewData(data, this);
            }
            byteBuffer.clear();
        }

    }


    public interface Listener {
        /**
         * Called when new incoming data is available.
         */
        void onNewData(byte[] data, UsbInputOutputManager manager);

        void onRunError(Exception e);
    }

    public void setREAD_WAIT_MILLIS(int READ_WAIT_MILLIS) {
        this.READ_WAIT_MILLIS = READ_WAIT_MILLIS;
    }

    public void setREAD_RATE_MILLIS(int READ_RATE_MILLIS) {
        this.READ_RATE_MILLIS = READ_RATE_MILLIS;
    }

    public static class Builder {
        private int timeOut = 200;
        private int readRate = 20;

        public Builder setTimeOut(int timeOut) {
            this.timeOut = timeOut;
            return this;
        }

        public Builder setReadRate(int readRate) {
            this.readRate = readRate;
            return this;
        }

        private void custom(UsbInputOutputManager usbInputOutputManager) {
            usbInputOutputManager.setREAD_WAIT_MILLIS(timeOut);
            usbInputOutputManager.setREAD_RATE_MILLIS(readRate);
        }

        public UsbInputOutputManager create(IUsbControl iUsbControl, Listener listener) {
            UsbInputOutputManager usbInputOutputManager = new UsbInputOutputManager(iUsbControl, listener);
            custom(usbInputOutputManager);
            return usbInputOutputManager;
        }

    }

}
