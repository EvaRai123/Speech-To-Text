package com.example.speechpractice;

import android.media.AudioDeviceInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioInputDeviceManagerTest {
    @Test
    public void recognizesExternalInputClassesSupportedByThePlatform() {
        assertTrue(AudioInputDeviceManager.isExternalType(AudioDeviceInfo.TYPE_USB_DEVICE, 26));
        assertTrue(AudioInputDeviceManager.isExternalType(AudioDeviceInfo.TYPE_WIRED_HEADSET, 26));
        assertTrue(AudioInputDeviceManager.isExternalType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26));
        assertTrue(AudioInputDeviceManager.isExternalType(AudioDeviceInfo.TYPE_BLE_HEADSET, 31));
        assertFalse(AudioInputDeviceManager.isExternalType(AudioDeviceInfo.TYPE_BUILTIN_MIC, 35));
        assertFalse(AudioInputDeviceManager.isExternalType(AudioDeviceInfo.TYPE_BLE_HEADSET, 30));
    }

    @Test
    public void routeVerificationRequiresSelectedIdAndAnExternalRoutedType() {
        assertTrue(AudioInputDeviceManager.isRouteVerified(true, 7, 7,
                AudioDeviceInfo.TYPE_USB_HEADSET, 35));
        assertFalse(AudioInputDeviceManager.isRouteVerified(true, 7, 8,
                AudioDeviceInfo.TYPE_USB_HEADSET, 35));
        assertFalse(AudioInputDeviceManager.isRouteVerified(true, 7, 7,
                AudioDeviceInfo.TYPE_BUILTIN_MIC, 35));
        assertFalse(AudioInputDeviceManager.isRouteVerified(true, null, 7,
                AudioDeviceInfo.TYPE_USB_HEADSET, 35));
        assertFalse(AudioInputDeviceManager.isRouteVerified(true, 7, null, null, 35));
        assertTrue(AudioInputDeviceManager.isRouteVerified(false, null, null, null, 35));
    }
}
