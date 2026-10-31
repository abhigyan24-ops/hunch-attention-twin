package com.dhyan.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Loopback-safe Android network socket binder.
 *
 * When connected to a Wi-Fi hotspot without internet, Android routes default sockets
 * over mobile data unless bound to wifi. However, binding 127.0.0.1 (adb reverse) to the
 * Wi-Fi interface fails with ENETUNREACH. This binder dispatches loopback sockets directly
 * and LAN sockets via the bound Wi-Fi interface.
 */
class NetworkBinder(ctx: Context, private val onNetworkChanged: () -> Unit = {}) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    @Volatile var wifi: Network? = null

    init {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm?.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                wifi = n
                onNetworkChanged()
            }
            override fun onLost(n: Network) {
                if (wifi == n) wifi = null
                onNetworkChanged()
            }
        })
    }

    fun openTcpSocket(host: String, port: Int, timeoutMs: Int = 2_500): Socket {
        // Loopback (127.0.0.1 for USB adb reverse) must NOT bind to the Wi-Fi NIC
        val isLoopback = host == "127.0.0.1" || host == "localhost"
        val socket = if (isLoopback) {
            Socket()
        } else {
            val net = wifi ?: throw IOException("No Wi-Fi network available for LAN connection")
            net.socketFactory.createSocket()
        }
        return socket.apply {
            tcpNoDelay = true
            keepAlive = true
            connect(InetSocketAddress(host, port), timeoutMs)
        }
    }
}
