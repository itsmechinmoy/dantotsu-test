#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vips/vips.h>
#include <string.h>

#define LOG_TAG "DantotsuVipsJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool vips_initialized = false;

extern "C" JNIEXPORT jboolean JNICALL
Java_ani_dantotsu_util_ImageScaler_initVips(JNIEnv *env, jobject thiz) {
    if (!vips_initialized) {
        if (vips_init("dantotsu") != 0) {
            LOGE("Failed to initialize libvips");
            return JNI_FALSE;
        }
        vips_initialized = true;
        LOGI("libvips initialized successfully");
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_ani_dantotsu_util_ImageScaler_scaleAndSharpenVips(
    JNIEnv *env, jobject thiz, jobject bitmap, jdouble scale_factor, jdouble sharpen_strength) {

    if (!vips_initialized) {
        if (vips_init("dantotsu") != 0) {
            LOGE("libvips not initialized and failed to init");
            return bitmap;
        }
        vips_initialized = true;
    }

    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return bitmap;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format: must be RGBA_8888");
        return bitmap;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return bitmap;
    }

    VipsImage *in = vips_image_new_from_memory_copy(
        pixels, info.width * info.height * 4, info.width, info.height, 4, VIPS_FORMAT_UCHAR);

    AndroidBitmap_unlockPixels(env, bitmap);

    if (!in) {
        LOGE("Failed to create VipsImage from memory");
        return bitmap;
    }

    VipsImage *temp = in;

    // 1. Apply Lanczos3 resize if scale_factor != 1.0
    if (scale_factor != 1.0) {
        VipsImage *resized = nullptr;
        if (vips_resize(temp, &resized, scale_factor, "kernel", VIPS_KERNEL_LANCZOS3, NULL) == 0) {
            g_object_unref(temp);
            temp = resized;
        } else {
            LOGE("vips_resize failed: %s", vips_error_buffer());
            vips_error_clear();
        }
    }

    // 2. Apply sharpen if strength > 0.0
    if (sharpen_strength > 0.0) {
        VipsImage *sharpened = nullptr;
        if (vips_sharpen(temp, &sharpened, "sigma", 0.5, "m2", sharpen_strength, NULL) == 0) {
            g_object_unref(temp);
            temp = sharpened;
        } else {
            LOGE("vips_sharpen failed: %s", vips_error_buffer());
            vips_error_clear();
        }
    }

    int out_width = temp->Xsize;
    int out_height = temp->Ysize;

    jclass bitmap_cls = env->FindClass("android/graphics/Bitmap");
    jmethodID create_bitmap_mid = env->GetStaticMethodID(
        bitmap_cls, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jclass config_cls = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID rgba_8888_fid = env->GetStaticFieldID(config_cls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject config_obj = env->GetStaticObjectField(config_cls, rgba_8888_fid);

    jobject out_bitmap = env->CallStaticObjectMethod(
        bitmap_cls, create_bitmap_mid, out_width, out_height, config_obj);

    if (!out_bitmap) {
        LOGE("Failed to create output bitmap");
        g_object_unref(temp);
        return bitmap;
    }

    void *out_pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, out_bitmap, &out_pixels) < 0) {
        LOGE("Failed to lock output bitmap pixels");
        g_object_unref(temp);
        return bitmap;
    }

    size_t out_size = 0;
    void *vips_out_mem = vips_image_write_to_memory(temp, &out_size);
    if (vips_out_mem) {
        memcpy(out_pixels, vips_out_mem, out_width * out_height * 4);
        g_free(vips_out_mem);
    } else {
        LOGE("vips_image_write_to_memory failed");
    }

    AndroidBitmap_unlockPixels(env, out_bitmap);
    g_object_unref(temp);

    return out_bitmap;
}
