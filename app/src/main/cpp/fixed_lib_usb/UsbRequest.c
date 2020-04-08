#include <stdio.h>
#include <stdlib.h>

#include "header.h"
#include "com_fixed_usb_UsbRequest.h"

static jfieldID field_context;
//static struct usb_request* usbRequest=NULL;
struct usb_request* get_request_from_object(JNIEnv* env, jobject java_request)
{
	////if(DEBUG) //LOGE("get_request_from_object 1");

	if(NULL == field_context)
	{
		////if(DEBUG) //LOGE("get_request_from_object 2");

		jclass cls = (*env)->GetObjectClass(env, java_request);

		////if(DEBUG) //LOGE("get_request_from_object 3");

		field_context = (*env)->GetFieldID(env, cls, "mNativeContext", "J");

		////if(DEBUG) //LOGE("get_request_from_object 4");

		(*env)->DeleteLocalRef(env, cls);

		////if(DEBUG) //LOGE("get_request_from_object 5");
       // LOGD(LOG, "这是Debug的信息");

    }

	jlong i = (*env)->GetLongField(env, java_request, field_context);

	////if(DEBUG) //LOGE("get_request_from_object 2");

	return (struct usb_request*) i;

   // return usbRequest;
}

// in android_hardware_UsbDeviceConnection.cpp
extern struct usb_device* get_device_from_object(JNIEnv* env, jobject connection);


JNIEXPORT jboolean JNICALL Java_com_fixed_usb_UsbRequest_native_1init(JNIEnv *env, jobject thiz, jobject java_device,
        jint ep_address, jint ep_attributes, jint ep_max_packet_size, jint ep_interval)
{
	////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 1");

    struct usb_device* device = get_device_from_object(env, java_device);

    ////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 2");

    if (!device)
    {
        ////LOGE("device null in native_init");
        return 0;
    }

    ////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 3");

    // construct an endpoint descriptor from the Java object fields
    struct usb_endpoint_descriptor desc;
    desc.bLength = USB_DT_ENDPOINT_SIZE;
    desc.bDescriptorType = USB_DT_ENDPOINT;
    desc.bEndpointAddress = ep_address;
    desc.bmAttributes = ep_attributes;
    desc.wMaxPacketSize = ep_max_packet_size;
    desc.bInterval = ep_interval;

    ////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 4");

    struct usb_request* request = usb_request_new(device, &desc);

    ////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 5");

	if(NULL == field_context)
	{
		////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 6");

		jclass cls = (*env)->GetObjectClass(env, thiz);

		////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 7");

		field_context = (*env)->GetFieldID(env, cls, "mNativeContext", "J");

		////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 8");

		(*env)->DeleteLocalRef(env, cls);

		////if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 9");
	}

    if (request)
    {
    	//if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init 10");

        (*env)->SetLongField(env, thiz, field_context, (long)request);
       // usbRequest=request;
    }

    //if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1init LAST");

    return (request != NULL);
}


JNIEXPORT void JNICALL Java_com_fixed_usb_UsbRequest_native_1close(JNIEnv *env, jobject thiz)
{
	//if(DEBUG) //LOGE("close");

    struct usb_request* request = get_request_from_object(env, thiz);

    if (request)
    {
        usb_request_free(request);
        (*env)->SetLongField(env, thiz, field_context, 0);
    }
}


JNIEXPORT jboolean JNICALL Java_com_fixed_usb_UsbRequest_native_1queue_1array(JNIEnv *env, jobject thiz,
        jbyteArray buffer, jint length, jboolean out)
{
    struct usb_request* request = get_request_from_object(env, thiz);

    if (!request)
    {
        //LOGE("request is closed in native_queue");
        return 0;
    }

    if (buffer && length)
    {
        request->buffer = malloc(length);

        if (!request->buffer)
        {
            return 0;
        }

        memset(request->buffer, 0, length);

        if (out)
        {
            // copy data from Java buffer to native buffer
            (*env)->GetByteArrayRegion(env, buffer, 0, length, (jbyte *)request->buffer);
        }
    }
    else
    {
        request->buffer = NULL;
    }

    request->buffer_length = length;

    if(!out)
    {
    	//if(DEBUG)
			//LOGE("native_1queue_1direct -- allocating memory");

    	request->client_data = (void *)(*env)->NewGlobalRef(env, thiz);
    }

    if (usb_request_queue(request))
    {
        if (request->buffer)
        {
            // free our buffer if usb_request_queue fails
            free(request->buffer);
            request->buffer = NULL;
        }

		if(request->client_data)
		{
			(*env)->DeleteGlobalRef(env, request->client_data);
		}

        return 0;
    }
    else
    {
        // save a reference to ourselves so UsbDeviceConnection.waitRequest() can find us
//        request->client_data = (void *)(*env)->NewGlobalRef(env, thiz);
        return 1;
    }
}


JNIEXPORT jboolean JNICALL Java_com_fixed_usb_UsbRequest_native_1queue_1direct
(JNIEnv *env, jobject thiz, jobject buffer, jint length, jboolean out)
{
	//if(DEBUG) //LOGE("Java_com_fixed_usb_UsbRequest_native_1queue_1direct 1");

    struct usb_request* request = get_request_from_object(env, thiz);

    //if(DEBUG) //LOGE("native_1queue_1direct 2");

    if (!request)
    {
        //LOGE("native_1queue_1direct -- request is closed in native_queue");
        return 0;
    }

    //if(DEBUG) //LOGE("native_1queue_1direct 2");

    if (buffer && length)
    {
    	//if(DEBUG) //LOGE("native_1queue_1direct -- buffer && length 1");

        request->buffer = (*env)->GetDirectBufferAddress(env, buffer);

        //if(DEBUG) //LOGE("native_1queue_1direct -- buffer && length 2");

        if (!request->buffer)
        {
        	//LOGE("native_1queue_1direct -- buffer && length -- !request->buffer");
            return 0;
        }
    }
    else
    {
    	//if(DEBUG) //LOGE("native_1queue_1direct -- buffer && length ELSE");

        request->buffer = NULL;
    }

    //if(DEBUG) //LOGE("native_1queue_1direct 3");

    request->buffer_length = length;

    //if(DEBUG) //LOGE("native_1queue_1direct 4");

    if(!out)
    {
    	//if(DEBUG)
			//LOGE("native_1queue_1direct -- allocating memory");

    	request->client_data = (void *)(*env)->NewGlobalRef(env, thiz);
    }

    //if(DEBUG)
    	//LOGE("native_1queue_1direct out : %d , %p", out, request->client_data);

    if (usb_request_queue(request))
    {
    	//if(DEBUG) //LOGE("native_1queue_1direct -- usb_request_queue(request) == TRUE 1");

		if (request->buffer)
		{
			// free our buffer if usb_request_queue fails
			free(request->buffer);
			request->buffer = NULL;
		}

		if(request->client_data)
		{
			(*env)->DeleteGlobalRef(env, request->client_data);
		}

		//if(DEBUG) //LOGE("native_1queue_1direct -- usb_request_queue(request) == TRUE 2");
        return 0;
    }
    else
    {
    	//if(DEBUG) //LOGE("native_1queue_1direct -- usb_request_queue(request) == FALSE 1");

        // save a reference to ourselves so UsbDeviceConnection.waitRequest() can find us
        // we also need this to make sure our native buffer is not deallocated
        // while IO is active

//        request->client_data = (void *)(*env)->NewGlobalRef(env, thiz);

    	//if(DEBUG) //LOGE("native_1queue_1direct -- usb_request_queue(request) == FALSE 2");

        return 1;
    }
}


JNIEXPORT jint JNICALL Java_com_fixed_usb_UsbRequest_native_1dequeue_1array(JNIEnv *env, jobject thiz,
        jbyteArray buffer, jint length, jboolean out)
{
    struct usb_request* request = get_request_from_object(env, thiz);

    if (!request)
    {
        //LOGE("request is closed in native_dequeue");
        return -1;
    }

    if (buffer && length && request->buffer && !out)
    {
        // copy data from native buffer to Java buffer
        (*env)->SetByteArrayRegion(env, buffer, 0, length, (jbyte *)request->buffer);
    }

    free(request->buffer);
    (*env)->DeleteGlobalRef(env, (jobject)request->client_data);

    return request->actual_length;
}


JNIEXPORT jint JNICALL Java_com_fixed_usb_UsbRequest_native_1dequeue_1direct(JNIEnv *env, jobject thiz)
{
    struct usb_request* request = get_request_from_object(env, thiz);

    if (!request)
    {
        //LOGE("request is closed in native_dequeue");
        return -1;
    }

    // all we need to do is delete our global ref
    (*env)->DeleteGlobalRef(env, (jobject)request->client_data);

    return request->actual_length;
}


JNIEXPORT jboolean JNICALL Java_com_fixed_usb_UsbRequest_native_1cancel(JNIEnv *env, jobject thiz)
{
    struct usb_request* request = get_request_from_object(env, thiz);

    if (!request)
    {
        //LOGE("request is closed in native_cancel");
        return 0;
    }

    return (usb_request_cancel(request) == 0);
}


