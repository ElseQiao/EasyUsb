#include <errno.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include "com_fixed_usb_UsbDeviceConnection.h"
#include "header.h"

static jfieldID field_context;
//static struct usb_device* usbDevice=NULL;
struct usb_device* get_device_from_object(JNIEnv* env, jobject connection)
{
	//if(DEBUG) //LOGE("UsbDeviceConnection.c get_device_from_object 1");

	if(NULL == field_context)
	{
		//if(DEBUG) //LOGE("UsbDeviceConnection.c get_device_from_object 2");

		jclass cls = (*env)->GetObjectClass(env, connection);

		//if(DEBUG) //LOGE("UsbDeviceConnection.c get_device_from_object 3");

		field_context = (*env)->GetFieldID(env, cls, "mNativeContext", "J");

		//if(DEBUG) //LOGE("UsbDeviceConnection.c get_device_from_object 4");

		(*env)->DeleteLocalRef(env, cls);

		//if(DEBUG) //LOGE("UsbDeviceConnection.c get_device_from_object 5");
	}

	jlong i = (*env)->GetLongField(env, connection, field_context);

	//if(DEBUG) //LOGE("UsbDeviceConnection.c get_device_from_object 6");

	return (struct usb_device*) i;
	//return usbDevice;
}


JNIEXPORT jboolean JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1open
(JNIEnv *env, jobject thiz, jstring deviceName, jint device_fd)
{
	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 1");

	if (device_fd < 0)
	{
		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 2");
		return 0;
	}

	const char *deviceNameStr = (*env)->GetStringUTFChars(env, deviceName, NULL);
	struct usb_device* device = usb_device_new(deviceNameStr, device_fd);

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 3");

	if(NULL == field_context)
	{
		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 4");

		jclass cls = (*env)->GetObjectClass(env, thiz);

		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 5");

		field_context = (*env)->GetFieldID(env, cls, "mNativeContext", "J");

		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 6");

		(*env)->DeleteLocalRef(env, cls);

		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 7");
	}

	if (device)
	{
		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 8");
		(*env)->SetLongField(env, thiz, field_context, (long) device);
		//usbDevice=device;
	}
	else
	{
		//if(DEBUG)
		{
			//LOGE("UsbDeviceConnection_native_1open 9");
			//LOGE("usb_device_open failed for %s", deviceNameStr);
		}

		close(device_fd);
	}

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open 10");

	(*env)->ReleaseStringUTFChars(env, deviceName, deviceNameStr);

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1open LAST");

	return (device != NULL);
}

JNIEXPORT void JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1close(JNIEnv *env, jobject thiz)
{
	//if(DEBUG) //LOGE("close");

	struct usb_device* device = get_device_from_object(env, thiz);

	if (device)
	{
		usb_device_close(device);
		(*env)->SetLongField(env, thiz, field_context, 0);
	}
}

JNIEXPORT jint JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1get_1fd(JNIEnv *env, jobject thiz)
{
	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1get_1fd -- 1");

	struct usb_device* device = get_device_from_object(env, thiz);

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1get_1fd -- 2");

	if (!device)
	{
		//LOGE("device is closed in native_get_fd");
		return -1;
	}

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1get_1fd -- 3");

	return usb_device_get_fd(device);
}

JNIEXPORT jbyteArray JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1get_1desc(JNIEnv *env, jobject thiz)
{
	char buffer[16384];
	int fd = Java_com_fixed_usb_UsbDeviceConnection_native_1get_1fd(env, thiz);

	if (fd < 0)
	{
		return NULL;
	}

	lseek(fd, 0, SEEK_SET);
	int length = read(fd, buffer, sizeof(buffer));

	if (length < 0)
	{
		return NULL;
	}

	jbyteArray ret = (*env)->NewByteArray(env, length);

	if (ret)
	{
		jbyte* bytes = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, ret, 0);

		if (bytes)
		{
			memcpy(bytes, buffer, length);
			(*env)->ReleasePrimitiveArrayCritical(env, ret, bytes, 0);
		}
	}

	return ret;
}

JNIEXPORT jboolean JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1claim_1interface
(JNIEnv *env, jobject thiz, int interfaceID, jboolean force)
{
	struct usb_device* device = get_device_from_object(env, thiz);

	if (!device)
	{
		//LOGE("device is closed in native_claim_interface");
		return -1;
	}

	int ret = usb_device_claim_interface(device, interfaceID);

	if (ret && force && errno == EBUSY)
	{
		// disconnect kernel driver and try again
		usb_device_connect_kernel_driver(device, interfaceID, 0);
		ret = usb_device_claim_interface(device, interfaceID);
	}

	return ret == 0;
}

JNIEXPORT jboolean JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1release_1interface
(JNIEnv *env, jobject thiz, int interfaceID)
{
	struct usb_device* device = get_device_from_object(env, thiz);

	if (!device)
	{
		//LOGE("device is closed in native_release_interface");
		return 0;
	}

	int ret = usb_device_release_interface(device, interfaceID);

	if (ret == 0)
	{
		// allow kernel to reconnect its driver
		usb_device_connect_kernel_driver(device, interfaceID, 1);
	}

	return ret;
}

JNIEXPORT jint JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1control_1request
(JNIEnv *env, jobject thiz, jint requestType, jint request, jint value, jint index,
jbyteArray buffer, jint start, jint length, jint timeout)
{
	struct usb_device* device = get_device_from_object(env, thiz);

	if (!device)
	{
		//LOGE("device is closed in native_control_request");
		return -1;
	}

	jbyte* bufferBytes = NULL;

	if (buffer)
	{
		bufferBytes = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);
	}

	jint result = usb_device_control_transfer(device, requestType, request,
			value, index, bufferBytes + start, length, timeout);

	if (bufferBytes)
	{
		(*env)->ReleasePrimitiveArrayCritical(env, buffer, bufferBytes, 0);
	}

	return result;
}

JNIEXPORT jint JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1bulk_1request
(JNIEnv *env,
jobject thiz, jint endpoint, jbyteArray buffer, jint start, jint length,
jint timeout)
{
	struct usb_device* device = get_device_from_object(env, thiz);

	if (!device)
	{
		//LOGE("device is closed in native_control_request");
		return -1;
	}

	jbyte* bufferBytes = NULL;

	if (buffer)
	{
		bufferBytes = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);
	}

	jint result = usb_device_bulk_transfer(device, endpoint,
			bufferBytes + start, length, timeout);

	if (bufferBytes)
	{
		(*env)->ReleasePrimitiveArrayCritical(env, buffer, bufferBytes, 0);
	}

	return result;
}

JNIEXPORT jobject JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1request_1wait(JNIEnv *env, jobject thiz)
{
	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 1");

	struct usb_device* device = get_device_from_object(env, thiz);

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 2");

	if (!device)
	{
		//LOGE("UsbDeviceConnection_native_1request_1wait -- device is closed in native_request_wait");
		return NULL;
	}

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 3");

	struct usb_request* request = usb_request_wait(device);

	//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 4");

	if (request)
	{
		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 5");

		jobject ob = (jobject) request->client_data;

		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 6");

		if(NULL != ob)
		{
			//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 6A");
		}
		else
		{
			//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 6B");
		}

		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 6xxx");

		return ob;
	}
	else
	{
		//if(DEBUG) //LOGE("UsbDeviceConnection_native_1request_1wait 7");

		return NULL;
	}
}

JNIEXPORT jstring JNICALL Java_com_fixed_usb_UsbDeviceConnection_native_1get_1serial(JNIEnv *env, jobject thiz)
{
	struct usb_device* device = get_device_from_object(env, thiz);

	if (!device)
	{
		//LOGE("device is closed in native_request_wait");
		return NULL;
	}

	char* serial = usb_device_get_serial(device);

	if (!serial)
	{
		return NULL;
	}

	jstring result = (*env)->NewStringUTF(env, serial);
	free(serial);

	return result;
}

