package com.furkan.pekautomotiveapp.network

import android.app.ProgressDialog
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.furkan.pekautomotiveapp.R
import com.furkan.pekautomotiveapp.util.Commons
import com.furkan.pekautomotiveapp.util.Constants
import com.furkan.pekautomotiveapp.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.*
import java.net.*

class NetworkManager(
    private val viewModel: MainViewModel,
    private val bindingRoot: View,
    private val context: Context
) {
    private suspend fun attemptConnection(ip: String): Boolean {
        val result: Boolean
        withContext(Dispatchers.IO) {
            result = try {
                Log.d("Network", "attemptConnection: Attempting connection for IP $ip")
                val socket = Socket()
                socket.connect(
                    InetSocketAddress(ip, Constants.PORT),
                    Constants.SOCKET_TIMEOUT.toInt()
                )
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

    private suspend fun testConnection(ip: String): Boolean {
        Log.d("Network", "testConnection: Starting test for IP $ip")
        var result = false
        try {
            withTimeout(Constants.SOCKET_TIMEOUT) {
                result = attemptConnection(ip)
                Log.d(
                    "Network",
                    "testConnection: Connection attempt finished for IP $ip, result: $result"
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.d("Network", "testConnection: Test connection timed out for IP: $ip")
        }
        Log.d("Network", "testConnection: Finished test for IP $ip, final result: $result")
        return result
    }

    private suspend fun getLocalIpAddress(getSystemService: Any): HashSet<String> {

        val ipList = HashSet<String>()
        val wifi: WifiManager = getSystemService as WifiManager
        val d = wifi.dhcpInfo
        val host: InetAddress
        withContext(Dispatchers.IO) {
            try {
                host = InetAddress.getByName(Commons.intToIp(d.dns1))
                val ip: ByteArray = host.address
                val jobs = mutableListOf<Deferred<Any>>()
                for (i in 1..254) {
                    jobs.add(async {
                        ip[3] = i.toByte()
                        val address: InetAddress = InetAddress.getByAddress(ip)
                        val ipAddress = address.toString().substring(1)
                        val isDeviceOnline =
                            isHostOnline(ipAddress, Constants.PORT, 1000) || isHostInArpCache(ipAddress)
                        if (isDeviceOnline) {
                            Log.d("Network", "$ipAddress machine is turned on and can be pinged")
                            synchronized(ipList) { ipList.add(ipAddress) }
                        } else if (address.hostAddress != address.hostName) {
                            Log.d("Network", "$ipAddress machine is known in a DNS lookup")
                        }
                        else {
                        }
                    })
                }
                jobs.awaitAll()
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return ipList
    }

    private fun isHostInArpCache(ipAddress: String?): Boolean {
        try {
            val process = Runtime.getRuntime().exec("arp -a")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String
            while (bufferedReader.readLine().also { line = it } != null) {
                if (line.contains(ipAddress!!)) {
                    return true
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private fun isHostOnline(ip: String?, port: Int, timeout: Int): Boolean {
        try {
            Socket().use { socket ->
                val socketAddress: SocketAddress = InetSocketAddress(ip, port)
                socket.connect(socketAddress, timeout)
                return true
            }
        } catch (e: IOException) {
            return false
        }
    }

    private suspend fun sendToIp(
        ip: String,
        text: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket(ip, Constants.PORT)
                val outputStream = socket.getOutputStream()
                val outputStreamWriter = OutputStreamWriter(outputStream)
                val bufferedWriter = BufferedWriter(outputStreamWriter)
                bufferedWriter.write(text)
                bufferedWriter.flush()
                socket.close()
                Log.d("Network", "Sending to IP : $ip")
                withContext(Dispatchers.Main){
                    Snackbar.make(
                        bindingRoot,
                        context.getString(R.string.info_message_send_successful),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("Network", "Error sending to IP: $ip")
                withContext(Dispatchers.Main){
                    Snackbar.make(
                        bindingRoot,
                        context.getString(R.string.error_send_message),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    suspend fun sendToServer(
        text: String,
        showTestConnectionProgressDialog: ProgressDialog,
    ) {
        Log.e("NetworkManager", "cbManualIP: ${viewModel.cbManualIP.value}, etManualIP: ${viewModel.etManualIP.value}")
        val isManualActive = viewModel.cbManualIP.value == true && !viewModel.etManualIP.value.isNullOrEmpty()
        Log.e("Network", isManualActive.toString())
        withContext(Dispatchers.IO) {
            if (isManualActive) {
                val progressDialog = withContext(Dispatchers.Main) {
                    showTestConnectionProgressDialog
                }
                Log.d("Network", "sendToServer: Sending ip to attemptConnection for IP ${viewModel.etManualIP.value!!}")
                if (attemptConnection(viewModel.etManualIP.value!!)) {
                    withContext(Dispatchers.Main) { progressDialog.dismiss() }
                    Log.d("Network", "sendToServer: Sending ip to sentToIp for IP ${viewModel.etManualIP.value}")
                    sendToIp(
                        viewModel.etManualIP.value!!,
                        text
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Snackbar.make(
                            bindingRoot,
                            context.getString(R.string.error_ip_not_valid),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                return@withContext
            }
            for (ip in viewModel.ipList.value!!) {
                if (!viewModel.refusedList.value!!.contains(ip)) {
                    if (attemptConnection(ip)) {
                        sendToIp(
                            ip,
                            text
                        )
                    } else {
                        Log.d("Network", "REFUSED FOR IP : $ip")
                        viewModel.refusedList.value!!.add(ip)
                    }
                }
            }
        }
    }

    suspend fun connectToServer(
        getSystemService: Any,
        dismissProgressDialog: (ProgressDialog) -> Unit,
        progressDialog: ProgressDialog,
    ) {
        val isManualActive = viewModel.cbManualIP.value == true && !viewModel.etManualIP.value.isNullOrEmpty()
        Log.e("Network", isManualActive.toString())
        val maxRetries = 3
        var currentRetry = 0
        try {
            withTimeout(Constants.CONNECTION_TIMEOUT) {
                while (currentRetry < maxRetries) {
                    Log.d("Network", "connectToServer: Inside while loop")
                    Log.d("Network", "Connection retry : ${currentRetry + 1}")
                    viewModel.isConnected.postValue(false)
                    if (isManualActive) {
                        Log.d("Network", "connectToServer: Manual IP checked")
                        if (testConnection(viewModel.etManualIP.value!!)
                        ) {
                            Log.d("Network", "connectToServer: Manual IP connection successful")
                            viewModel.ipList.value!!.clear()
                            viewModel.ipList.value!!.add(viewModel.etManualIP.value!!)
                            viewModel.isConnected.postValue(true)
                            break
                        }
                    } else {
                        Log.d("Network", "connectToServer: Manual IP connection unsuccessful")
                        viewModel.ipList.value =
                            getLocalIpAddress(getSystemService as WifiManager)
                        Log.d("Network", "connectToServer: Obtained IP list")
                        Log.d(
                            "Network",
                            "IP RETRIEVAL PROCESS FINISHED, AVAILABLE IP ADDRESSES : ${viewModel.ipList.value!!}"
                        )
                        if (viewModel.ipList.value!!.isNotEmpty()) {
                            val iterator = viewModel.ipList.value!!.iterator()
                            while (iterator.hasNext()) {
                                Log.d("Network", "connectToServer: Inside iterator loop")
                                val ip = iterator.next()
                                Log.d("Network", "connectToServer: Testing connection for IP $ip")
                                if (testConnection(ip)
                                ) {
                                    Log.d("Network", "connectToServer: IP connection successful")
                                    viewModel.isConnected.postValue(true)
                                    viewModel.ipList.value?.clear()
                                    viewModel.refusedList.value?.clear()
                                    viewModel.ipList.value!!.add(ip)
                                    break
                                }
                                Log.d("Network", "connectToServer: IP connection unsuccessful")
                            }
                        }
                    }
                    if (viewModel.isConnected.value!!) {
                        Log.d("Network", "connectToServer: isConnected is true")
                        break
                    } else {
                        currentRetry++
                        Log.d("Network", "connectToServer: Incrementing currentRetry")
                        if (currentRetry < maxRetries) {
                            delay(1500)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.d("Network", "Connection process timed out.")
        } finally {
            withContext(Dispatchers.Main) {
                dismissProgressDialog(progressDialog)
                if (viewModel.isConnected.value!!) {
                    Snackbar.make(
                        bindingRoot,
                        context.getString(R.string.info_connection_successful),
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("Connection Failed")
                        .setMessage(context.getString(R.string.dialog_failed_message))
                        .setPositiveButton("OK", null).setCancelable(false).show()
                }
            }
        }
    }
}