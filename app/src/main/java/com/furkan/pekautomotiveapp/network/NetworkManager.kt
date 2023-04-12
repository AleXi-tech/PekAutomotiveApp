package com.furkan.pekautomotiveapp.network

import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import android.widget.CheckBox
import android.widget.EditText
import com.furkan.pekautomotiveapp.util.Constants
import com.furkan.pekautomotiveapp.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.*

class NetworkManager(private val context: Context, private val viewModel: MainViewModel) {

    suspend fun attemptConnection(ip: String): Boolean {
        val result: Boolean
        withContext(Dispatchers.IO) {
            result = try {
                Log.d("Network", "attemptConnection: Attempting connection for IP $ip")
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, Constants.PORT), Constants.SOCKET_TIMEOUT.toInt())
                Log.d("Network", "attemptConnection: Socket created for IP $ip") // Add this log
                socket.close()
                Log.d("Network", "attemptConnection: Connection for IP $ip is successful")
                viewModel.isConnected.postValue(true)
                true
            } catch (e: ConnectException) {
                if (e.toString().contains("Connection refused")) {
                    Log.d("Network", "attemptConnection: CONNECTION REFUSED FOR IP : $ip")
                }
                viewModel.isConnected.postValue(false)
                false
            } catch (e: UnknownHostException) {
                Log.d("Network", "attemptConnection: UnknownHost Exception for IP: $ip")
                e.printStackTrace()
                viewModel.isConnected.postValue(false)
                false
            } catch (e: IOException) {
                Log.d("Network", "attemptConnection: IO Exception for IP: $ip")
                e.printStackTrace()
                viewModel.isConnected.postValue(false)
                false
            } catch (e: SocketTimeoutException) {
                Log.d("Network", "attemptConnection: Socket timeout for IP: $ip")
                e.printStackTrace()
                viewModel.isConnected.postValue(false)
                false
            }
        }
        return result
    }

    suspend fun testConnection(
        ip: String,
        manualIpBoolean: Boolean,
        showTestConnectionProgressDialog : ProgressDialog,
        networkManager: NetworkManager
    ): Boolean {
        Log.d("Network", "testConnection: Starting test for IP $ip")
        val progressDialog = withContext(Dispatchers.Main) { showTestConnectionProgressDialog }
        var result = false
        try {
            withTimeout(Constants.SOCKET_TIMEOUT) {
                result = networkManager.attemptConnection(ip)
                Log.d(
                    "Network",
                    "testConnection: Connection attempt finished for IP $ip, result: $result"
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.d("Network", "testConnection: Test connection timed out for IP: $ip")
        }
//        if (progressDialog != null) {
//            withContext(Dispatchers.Main) { progressDialog.dismiss() }
//        }
        Log.d("Network", "testConnection: Finished test for IP $ip, final result: $result")
        return result
    }
}