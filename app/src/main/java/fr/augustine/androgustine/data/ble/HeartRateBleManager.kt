package fr.augustine.androgustine.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class HeartRateBleManager(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    @SuppressLint("MissingPermission")
    fun heartRateFlow(): Flow<Int> = callbackFlow {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing BLE permissions. Heart rate scan not started.")
            close()
            return@callbackFlow
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter unavailable or disabled.")
            close()
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "Bluetooth LE scanner unavailable.")
            close()
            return@callbackFlow
        }

        var gatt: BluetoothGatt? = null
        var isScanning = false

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.i(TAG, "GATT state changed: status=$status newState=$newState")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "GATT connection error: status=$status")
                    gatt.close()
                    return
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Heart rate device connected. Discovering services.")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Heart rate device disconnected.")
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.i(TAG, "GATT services discovered: status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) return

                val characteristic = gatt
                    .getService(HEART_RATE_SERVICE_UUID)
                    ?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

                if (characteristic == null) {
                    Log.w(TAG, "Heart Rate Measurement characteristic not found.")
                    return
                }

                val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
                Log.i(TAG, "Heart rate notifications enabled locally: $notificationEnabled")
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor == null) {
                    Log.w(TAG, "Client Characteristic Configuration descriptor not found.")
                    return
                }

                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeStarted = gatt.writeDescriptor(descriptor)
                Log.i(TAG, "Heart rate CCCD write started: $writeStarted")
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.i(TAG, "Heart rate CCCD write completed: status=$status")
            }

            @Deprecated("Deprecated in Android 13, kept for older devices.")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                handleHeartRateMeasurement(characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleHeartRateMeasurement(value)
            }

            private fun handleHeartRateMeasurement(value: ByteArray) {
                val bpm = decodeHeartRate(value)
                if (bpm == null) {
                    Log.w(TAG, "Invalid Heart Rate Measurement payload.")
                    return
                }
                Log.d(TAG, "Heart rate received: $bpm bpm")
                trySend(bpm)
            }
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceName = result.device.name ?: "unknown"
                Log.i(TAG, "Heart rate BLE device found: $deviceName / ${result.device.address}")

                if (isScanning) {
                    scanner.stopScan(this)
                    isScanning = false
                }

                gatt = result.device.connectGatt(context, false, gattCallback)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Heart rate BLE scan failed: errorCode=$errorCode")
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.i(TAG, "Starting BLE scan for Heart Rate Service.")
        scanner.startScan(filters, settings, scanCallback)
        isScanning = true

        awaitClose {
            if (hasRequiredPermissions()) {
                if (isScanning) {
                    scanner.stopScan(scanCallback)
                }
                gatt?.disconnect()
                gatt?.close()
            }
            Log.i(TAG, "Heart rate BLE flow closed.")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun decodeHeartRate(value: ByteArray): Int? {
        if (value.size < 2) return null
        val flags = value[0].toInt()
        val isUint16 = flags and 0x01 == 0x01
        return if (isUint16) {
            if (value.size < 3) return null
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            value[1].toInt() and 0xFF
        }
    }

    companion object {
        private const val TAG = "HeartRateBle"
        private val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
