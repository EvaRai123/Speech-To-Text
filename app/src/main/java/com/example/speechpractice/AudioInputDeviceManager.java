package com.example.speechpractice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AudioInputDeviceManager {
    public static final class InputDevice {
        public final int id;
        public final String name;
        public final int type;

        private InputDevice(int id, String name, int type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
    }

    private AudioInputDeviceManager() {
    }

    public static List<InputDevice> getExternalInputs(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return Collections.emptyList();
        List<InputDevice> devices = new ArrayList<>();
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (device.isSource() && isExternalType(device.getType(), Build.VERSION.SDK_INT)) {
                devices.add(new InputDevice(device.getId(), String.valueOf(device.getProductName()), device.getType()));
            }
        }
        return devices;
    }

    static boolean isExternalType(int type, int sdkInt) {
        switch (type) {
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return true;
            default:
                return sdkInt >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET;
        }
    }

    static boolean isRouteVerified(boolean required, Integer selectedId, Integer routedId,
                                   Integer routedType, int sdkInt) {
        if (selectedId == null) return !required;
        return routedId != null && routedType != null && selectedId.equals(routedId)
                && isExternalType(routedType, sdkInt);
    }
}
