import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*

class BluetoothConnectThread(
    device: BluetoothDevice,
    private val context: Activity,
    private val permissionGrantedCallback: () -> Unit
) : Thread() {
    private lateinit var socket: BluetoothSocket

    init {
        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Handle permission not granted case
                Toast.makeText(context, "peeer", Toast.LENGTH_SHORT).show()

            }
            socket = device.createRfcommSocketToServiceRecord(uuid)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, "Socket creation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun run() {
        // Cancel discovery to improve connection performance
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "peeer", Toast.LENGTH_SHORT).show()

            return
        }

        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(context, "peeer", Toast.LENGTH_SHORT).show()

                return
            }
            socket.connect()
            manageConnectedSocket(socket)
            permissionGrantedCallback() // Notify that permission is granted and connection was successful
        } catch (e: IOException) {
            e.printStackTrace()
            context.runOnUiThread {
                Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            try {
                socket.close()
            } catch (closeException: IOException) {
                closeException.printStackTrace()
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        try {
            val inputStream = socket.inputStream
            val outputStream = socket.outputStream

            // Handle read and write operations here
            val buffer = ByteArray(1024) // Buffer store for the stream

            while (true) {
                try {
                    // Read data from the input stream
                    val bytes: Int = inputStream.read(buffer)
                    if (bytes > 0) {
                        val receivedData = String(buffer, 0, bytes)
                        Toast.makeText(context, "Received: $receivedData", Toast.LENGTH_SHORT).show()
                    } else {
                        // Break loop if no data received
                        break
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Read failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    break
                }
            }

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, "Error managing connected socket: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            try {
                socket.close() // Ensure socket is closed on exit
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
