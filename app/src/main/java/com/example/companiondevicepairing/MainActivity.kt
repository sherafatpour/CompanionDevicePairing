package com.example.companiondevicepairing

import BluetoothConnectThread
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import android.bluetooth.BluetoothAdapter
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult

class MainActivity : ComponentActivity() {

    private lateinit var companionDeviceManager: CompanionDeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize CompanionDeviceManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            companionDeviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        }

        // Register broadcast receiver to listen for pairing results
        registerReceiver(bluetoothPairingReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))

        setContent {
            CompanionDevicePermissionScreen()
        }
    }

    @Composable
    fun CompanionDevicePermissionScreen() {
        val context = LocalContext.current as Activity
        var isPermissionGranted by remember { mutableStateOf(false) }

        val requestBluetoothPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isBluetoothGranted = permissions[Manifest.permission.BLUETOOTH] ?: false
            val isBluetoothAdminGranted = permissions[Manifest.permission.BLUETOOTH_ADMIN] ?: false
            val isBluetoothScanGranted = permissions.getOrElse(Manifest.permission.BLUETOOTH_SCAN) { false }
            val isBluetoothConnectGranted = permissions.getOrElse(Manifest.permission.BLUETOOTH_CONNECT) { false }

            // Check if permission is granted for Bluetooth Classic or BLE
            isPermissionGranted = isBluetoothGranted || isBluetoothAdminGranted || isBluetoothScanGranted || isBluetoothConnectGranted
            if (isPermissionGranted) {
                startDevicePairing(context)
            } else {
                Toast.makeText(context, "Bluetooth permissions are required to proceed.", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            // Request the necessary permissions based on SDK version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBluetoothPermissionLauncher.launch(
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                )
            } else {
                requestBluetoothPermissionLauncher.launch(
                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
                )
            }
        }
    }

    private fun startDevicePairing(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val associationRequest = AssociationRequest.Builder()
                .setSingleDevice(false)
                .build()

            companionDeviceManager.associate(
                associationRequest,
                object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        startIntentSenderForResult(chooserLauncher, 0, null, 0, 0, 0)
                    }

                    override fun onFailure(error: CharSequence?) {
                        Toast.makeText(context, "Failed to start pairing: $error", Toast.LENGTH_SHORT).show()
                    }
                },
                null
            )
        }
    }

    /*   @RequiresApi(Build.VERSION_CODES.O)
       override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
           super.onActivityResult(requestCode, resultCode, data)

           if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
               val device: BluetoothDevice? = data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
               device?.let {
                   // Check if the device is already bonded (paired)
                   if (ActivityCompat.checkSelfPermission(
                           this,
                           Manifest.permission.BLUETOOTH_CONNECT
                       ) != PackageManager.PERMISSION_GRANTED
                   ) {
                       return
                   }
                   if (it.bondState == BluetoothDevice.BOND_BONDED) {
                       Toast.makeText(this, "Device is already paired", Toast.LENGTH_SHORT).show()
                       // Connect to the device
                       connectToDevice(it)  // Use the new connectToDevice method
                   } else {
                       // Start pairing process
                       pairWithBluetoothDevice(it)
                   }
               }
           }
       }*/
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            // Check for null or empty data
            data?.let {
                // Instead of using getParcelableExtra, directly handle the associated device
                val device: BluetoothDevice? = it.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE) as? BluetoothDevice

                if (device != null) {
                    // Check if the device is already bonded (paired)
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        Toast.makeText(this, "Device is already paired", Toast.LENGTH_SHORT).show()
                        // Connect to the device
                        connectToDevice(device)
                    } else {
                        // Start pairing process
                        pairWithBluetoothDevice(device)
                    }
                } else {
                    Toast.makeText(this, "Failed to retrieve device from intent", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pairWithBluetoothDevice(device: BluetoothDevice) {
        // Check if the necessary permission is granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        // Initiate pairing
        device.createBond() // No return value, just initiate bonding
        Toast.makeText(this, "Pairing initiated with ${device.name}", Toast.LENGTH_SHORT).show()
    }

    // BroadcastReceiver to listen for Bluetooth pairing status
    private val bluetoothPairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)

                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        Toast.makeText(context, "Paired with ${device?.name}", Toast.LENGTH_SHORT).show()
                        // Optionally, connect to the device
                        connectToDevice(device!!)
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }
                        Toast.makeText(context, "Pairing with ${device?.name}", Toast.LENGTH_SHORT).show()
                    }
                    BluetoothDevice.BOND_NONE -> {
                        Toast.makeText(context, "Pairing failed or canceled", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission or handle accordingly
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the connection thread and start it
        val connectThread = BluetoothConnectThread(device, this) {
            // This callback will be invoked when the connection is successful
            runOnUiThread {
                Toast.makeText(this, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
            }
        }
        connectThread.start() // Start the connection thread
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver when the activity is destroyed
        unregisterReceiver(bluetoothPairingReceiver)
    }
}
