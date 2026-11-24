package com.erkam.nagme

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.erkam.nagme.data.Downloads
import com.erkam.nagme.data.Store
import com.erkam.nagme.yt.Net
import com.erkam.nagme.yt.Yt
import kotlinx.coroutines.flow.MutableStateFlow

class App : Application(), SingletonImageLoader.Factory {

    companion object {
        /** İnternet var mı? (yoksa cihazdaki şarkılar gösterilir) */
        val online = MutableStateFlow(true)
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        com.erkam.nagme.ui.C.accentState.value = androidx.compose.ui.graphics.Color(Store.settings.value.accent)
        com.erkam.nagme.ui.C.amoledState.value = Store.settings.value.amoled
        Downloads.init(this)
        Yt.init()
        watchNetwork()
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        fun check() {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            online.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
        check()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online.value = true }
            override fun onLost(network: Network) { check() }
            override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) {
                online.value = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.client })) }
            .diskCache {
                DiskCache.Builder().directory(cacheDir.resolve("kapaklar")).maxSizeBytes(80L * 1024 * 1024).build()
            }
            .crossfade(true)
            .build()
}
