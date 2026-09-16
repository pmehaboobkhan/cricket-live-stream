package com.cricket.stream.streaming.capture

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * USB Camera capture manager for UVC-compatible external devices (capture cards).
 * Manages USB device detection, permission request via PendingIntents to Android system dialog,
 * and connection lifecycle to the underlying capture hardware.
 */
class CameraManager private constructor(
    private val context: Context
) {

    var onDeviceConnected: ((UsbDevice) -> Unit)? = null
    var onDeviceDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private lateinit var permissionPendingIntent: PendingIntent
    private var connectedDevice: UsbDevice? = null
    private var receiverRegistered = false

    /** Receiver for USB attach/detach events and permission results */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    handleDeviceEvent(
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE),
                        connected = true
                    )
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    handleDeviceEvent(
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE),
                        connected = false
                    )
                }
                USB_PERMISSION_ACTION -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        connectedDevice = device
                        Log.i(TAG, "USB permission granted for: ${device.deviceName}")
                        onDeviceConnected?.invoke(device)
                    } else {
                        onError?.invoke("USB permission denied for camera")
                        connectedDevice = null
                    }
                }
            }
        }

        /** Check if a USB device is a UVC-compatible capture card */
        private fun isUvcCompatible(device: UsbDevice): Boolean {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                // Video interface subclass=2, protocol=1 matches standard UVC
                if ((intf.configurationClass == 239 && intf.protocol == 1) ||
                    (intf.subclass == 2 && intf.protocol == 1)) {
                    return true
                }
            }
            return false
        }

        /** Request USB permission from the user via system dialog */
        private fun requestPermission(device: UsbDevice) {
            permissionPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(USB_PERMISSION_ACTION).apply {
                    putParcelableExtra(UsbManager.EXTRA_DEVICE, device)
                },
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            usbManager.requestPermission(device, permissionPendingIntent)
        }

        private fun handleDeviceEvent(device: UsbDevice?, connected: Boolean) {
            device ?: return
            if (isUvcCompatible(device)) {
                if (connected && !usbManager.hasPermission(device)) {
                    requestPermission(device)
                } else if (!connected && device == connectedDevice) {
                    connectedDevice = null
                    onDeviceDisconnected?.invoke()
                }
            }
        }
    }

    init {
        registerReceiver()
    }

    /** List all currently attached UVC-compatible devices */
    fun listUvcDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.filter(::isUvcCompatible).toList()
    }

    /** Request permission and connect to a specific USB device */
    fun connect(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
        } else {
            connectedDevice = device
            onDeviceConnected?.invoke(device)
        }
    }

    /** Disconnect the active camera device */
    fun disconnect() {
        connectedDevice = null
        onDeviceDisconnected?.invoke()
    }

    /** Called from Activity lifecycle to unregister USB receiver */
    fun onDestroy() {
        try { context.unregisterReceiver(usbReceiver) } catch (e: Exception) {}
        receiverRegistered = false
    }

    companion object {
        const val TAG = "CameraManager"
        const val USB_PERMISSION_ACTION = "com.cricket.stream.USB_PERMISSION"
        fun create(context: Context): CameraManager = CameraManager(context)
    }
}
