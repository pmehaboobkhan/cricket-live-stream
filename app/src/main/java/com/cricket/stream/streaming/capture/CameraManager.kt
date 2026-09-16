package com.cricket.stream.streaming.capture

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
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
    private var connectedDevice: UsbDevice? = null
    private var receiverRegistered = false
    private lateinit var pendingIntent: PendingIntent

    /** Receiver for USB attach/detach events and permission results */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    handleDeviceEvent(device, connected = true)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    handleDeviceEvent(device, connected = false)
                }
                USB_PERMISSION_ACTION -> {
                    @Suppress("DEPRECATION")
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    }
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
    }

    /** Check if a USB device is a UVC-compatible capture card */
    private fun isUvcCompatible(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            val isClass = intf.interfaceClass == 14
            val isSubclass = intf.interfaceSubclass == 2
            val isProtocol = intf.interfaceProtocol == 1
            if (isClass && isSubclass && isProtocol) {
                return true
            }
        }
        return false
    }

    /** Request USB permission from the user via system dialog */
    private fun requestPermission(device: UsbDevice) {
        pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(USB_PERMISSION_ACTION).apply {
                @Suppress("DEPRECATION")
                putExtra(UsbManager.EXTRA_DEVICE, device)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun registerReceiver() {
        try {
            context.registerReceiver(usbReceiver, IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(USB_PERMISSION_ACTION)
            })
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register USB receiver", e)
        }
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
        if (receiverRegistered) {
            try { context.unregisterReceiver(usbReceiver) } catch (e: Exception) {}
            receiverRegistered = false
        }
    }

    companion object {
        const val TAG = "CameraManager"
        const val USB_PERMISSION_ACTION = "com.cricket.stream.USB_PERMISSION"
        fun create(context: Context): CameraManager = CameraManager(context)
    }
}
