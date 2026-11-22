package com.ahmedmahmoud.cinemonitor

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ahmedmahmoud.cinemonitor.camera.CanonPtpController
import com.ahmedmahmoud.cinemonitor.gl.CinemaRenderer
import com.ahmedmahmoud.cinemonitor.ui.CinemaMonitorScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.ahmedmahmoud.cinemonitor.USB_PERMISSION"
    }

    private lateinit var usbManager: UsbManager
    private lateinit var ptpController: CanonPtpController
    private lateinit var cinemaRenderer: CinemaRenderer

    private var isCanonConnected by mutableStateOf(false)
    private var isVideoConnected by mutableStateOf(false)
    private var liveVideoAspectRatio by mutableFloatStateOf(16f / 9f)
    private var isReceiverRegistered = false
    private val isConnectionInProgress = AtomicBoolean(false)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        connectCanonDeviceAsync(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (device != null) {
                        requestOrConnectCanon(device)
                    } else {
                        scanConnectedDevices()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (ptpController.isMatchingDevice(device)) {
                        handleDisconnectedState()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        ptpController = CanonPtpController(usbManager)
        cinemaRenderer = CinemaRenderer()

        ptpController.onEvfFrameDecoded = { bitmap ->
            cinemaRenderer.submitUsbEvfFrame(bitmap)
        }
        cinemaRenderer.onBitmapConsumed = { bitmap ->
            ptpController.releaseEvfBitmap(bitmap)
        }
        cinemaRenderer.onVideoAspectChanged = { aspect ->
            liveVideoAspectRatio = aspect
        }

        registerUsbReceiver()
        if (!handleUsbIntent(intent)) {
            scanConnectedDevices()
        }

        setContent {
            CinemaMonitorScreen(
                ptpController = ptpController,
                renderer = cinemaRenderer,
                isCameraConnected = isCanonConnected,
                isVideoConnected = isVideoConnected,
                videoAspectRatio = liveVideoAspectRatio,
                onManualRescanUsb = {
                    if (!isCanonConnected) {
                        scanConnectedDevices(forceReset = true)
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleUsbIntent(intent) && !isCanonConnected) {
            scanConnectedDevices()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isCanonConnected && !isConnectionInProgress.get()) {
            scanConnectedDevices()
        }
    }

    private fun handleUsbIntent(intent: Intent?): Boolean {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device != null && device.vendorId == CanonPtpController.CANON_VENDOR_ID) {
                requestOrConnectCanon(device)
                return true
            }
        }
        return false
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun scanConnectedDevices(forceReset: Boolean = false) {
        if (forceReset) {
            isConnectionInProgress.set(false)
        }
        val canonDevice = usbManager.deviceList.values.firstOrNull {
            it.vendorId == CanonPtpController.CANON_VENDOR_ID
        } ?: return

        requestOrConnectCanon(canonDevice)
    }

    private fun requestOrConnectCanon(device: UsbDevice) {
        if (device.vendorId != CanonPtpController.CANON_VENDOR_ID) return
        if (isCanonConnected && ptpController.isMatchingDevice(device)) return

        if (usbManager.hasPermission(device)) {
            connectCanonDeviceAsync(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val explicitIntent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
            val permissionIntent = PendingIntent.getBroadcast(this, 0, explicitIntent, flags)
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun connectCanonDeviceAsync(device: UsbDevice) {
        if (!isConnectionInProgress.compareAndSet(false, true)) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connected = ptpController.connect(device)
                withContext(Dispatchers.Main) {
                    isCanonConnected = connected
                    if (connected) {
                        ptpController.startDirectUsbLiveViewAndEvents(
                            scope = lifecycleScope,
                            onStreamStateChanged = { streaming ->
                                isVideoConnected = streaming
                                if (!streaming) {
                                    cinemaRenderer.onLiveVideoDisconnected()
                                }
                            },
                            onConnectionLost = {
                                handleDisconnectedState()
                            }
                        )
                    }
                }
            } finally {
                isConnectionInProgress.set(false)
            }
        }
    }

    private fun handleDisconnectedState() {
        isConnectionInProgress.set(false)
        isCanonConnected = false
        isVideoConnected = false
        cinemaRenderer.onLiveVideoDisconnected()
        lifecycleScope.launch(Dispatchers.IO) {
            ptpController.disconnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(usbReceiver) }
            isReceiverRegistered = false
        }
        cinemaRenderer.onBitmapConsumed = null
        ptpController.onEvfFrameDecoded = null
        ptpController.disconnect()
    }
}
