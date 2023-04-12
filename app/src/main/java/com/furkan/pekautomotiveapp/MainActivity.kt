package com.furkan.pekautomotiveapp

import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.furkan.pekautomotiveapp.databinding.ActivityMainBinding
import com.furkan.pekautomotiveapp.network.NetworkManager
import com.furkan.pekautomotiveapp.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var networkManager: NetworkManager

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        setupViews()
        setupListeners()
        setupSharedPreferences()

        networkManager = NetworkManager(viewModel, binding.root, this)
        showConnectionDialog(isFirstRun = isFirstRun())
    }

    private fun setupViews() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            val input = binding.etInput.text.toString()
            if (input.isEmpty()) {
                showSnackbar(getString(R.string.error_enter_message))
                return@setOnClickListener
            }
            sendToServer(input)
        }

        binding.cbManualIP.setOnCheckedChangeListener { _, isChecked ->
            viewModel.cbManualIP.value = isChecked
        }

        viewModel.cbManualIP.observe(this) { isChecked ->
            binding.etManualIP.isEnabled = isChecked
        }

        binding.etManualIP.doOnTextChanged { text, _, _, _ ->
            viewModel.etManualIP.value = text.toString()
        }
    }

    private fun setupSharedPreferences() {
        sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    }

    private fun isFirstRun(): Boolean {
        val isFirstRun = sharedPreferences.getBoolean("is_first_run", true)
        if (isFirstRun) {
            sharedPreferences.edit().putBoolean("is_first_run", false).apply()
        }
        return isFirstRun
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                showResetConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Connection")
            .setMessage(getString(R.string.dialog_reset_message))
            .setPositiveButton("Yes") { _, _ -> resetConnection() }
            .setNegativeButton("No", null).show()
    }

    private fun resetConnection() {
        viewModel.cbManualIP.postValue(false)
        sharedPreferences.edit().remove("server_ip").apply()
        showConnectionDialog(isFirstRun())
    }

    private fun showConnectionDialog(isFirstRun: Boolean) {
        AlertDialog.Builder(this).setTitle("Connection Warning")
            .setMessage(getString(R.string.dialog_warning_message))
            .setPositiveButton("OK") { _, _ ->
                showProgressDialog(isFirstRun)
                establishConnection()
            }.setCancelable(false).show()
    }

    private fun showProgressDialog(isFirstRun: Boolean) {
        progressDialog = ProgressDialog(this).apply {
            setTitle("Establishing Connection")
            setMessage(
                if (isFirstRun) getString(R.string.dialog_first_time_message)
                else "Please wait..."
            )
            setCancelable(false)
            show()
        }
    }

    private fun establishConnection() {
        lifecycleScope.launch {
            networkManager.connectToServer(
                getSystemService(Context.WIFI_SERVICE),
                { progressDialog.dismiss() },
                progressDialog
            )
        }
    }

    private fun showTestConnectionProgressDialogForManualIp(): ProgressDialog {
        val isManualActive =
            viewModel.cbManualIP.value == true && !viewModel.etManualIP.value.isNullOrEmpty()
        return ProgressDialog(this@MainActivity).apply {
            setTitle("Testing Connection")
            setMessage("Testing connection for IP: ${viewModel.etManualIP.value}")
            setCancelable(false)
            if (isManualActive) show()
            else dismiss()
        }
    }

    private fun sendToServer(input: String) {
        lifecycleScope.launch {
            networkManager.sendToServer(
                input,
                showTestConnectionProgressDialogForManualIp(),
            )
        }

        if (!viewModel.isConnected.value!! && !viewModel.cbManualIP.value!!) {
            showSnackbar(getString(R.string.error_no_connection))
            return
        }
    }

    fun showSnackbar(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }
}


