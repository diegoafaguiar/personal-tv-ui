package tech.futureme.tvhome.data

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs

/** Rede, Bluetooth, RAM e espaço, para o widget do hero. Tudo local, sem rede. */
class StatusRepository(private val context: Context) {
    fun read(): DeviceStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val network = when {
            caps == null -> "Sem rede"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "Rede"
        }
        val bt = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled
        } catch (e: SecurityException) { null }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        return DeviceStatus(
            network = network,
            bluetoothOn = bt,
            freeRamMb = mem.availMem / (1024 * 1024),
            freeStorageGb = stat.availableBytes / 1e9,
        )
    }
}
