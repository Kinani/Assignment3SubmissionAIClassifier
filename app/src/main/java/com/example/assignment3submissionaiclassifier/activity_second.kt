package com.example.assignment3submissionaiclassifier

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class activity_second : AppCompatActivity() {
    private lateinit var tflite: Interpreter
    private lateinit var labels: List<String>
    private val CHANNEL_ID = "classification_channel_id"
    private val NOTIFICATION_ID = 1
    private val REQUEST_CODE_NOTIFICATION_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        val imageView: ImageView = findViewById(R.id.image_view)
        val resultTextView: TextView = findViewById(R.id.result_text_view)

        val capturedImage = intent.getParcelableExtra<Bitmap>("capturedImage")
        imageView.setImageBitmap(capturedImage)

        // Load TFLite model
        tflite = Interpreter(loadModelFile())
        // Load labels
        labels = loadLabels()

        val result = classifyImage(capturedImage)
        resultTextView.text = "Classification Result: $result"

        showNotification(result)
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("mobilenet_v2.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(assets.open("labels.txt")))
        reader.forEachLine { labels.add(it) }
        reader.close()
        return labels
    }

    private fun classifyImage(bitmap: Bitmap?): String {
        val inputSize = 224
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)

        bitmap?.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]

                byteBuffer.putFloat(((value shr 16 and 0xFF) - 127) / 128.0f)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - 127) / 128.0f)
                byteBuffer.putFloat(((value and 0xFF) - 127) / 128.0f)
            }
        }

        val result = Array(1) { FloatArray(labels.size) }
        tflite.run(byteBuffer, result)

        // Find the index of the maximum probability
        var maxIndex = -1
        var maxProb = 0.0f
        for (i in result[0].indices) {
            if (result[0][i] > maxProb) {
                maxProb = result[0][i]
                maxIndex = i
            }
        }

        return "${labels[maxIndex]} with confidence ${(maxProb * 100).toInt()}%"
    }

    private fun showNotification(result: String) {
        // Create the notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Classification Result Channel"
            val descriptionText = "Channel for image classification results"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Check if the small icon is valid
        val smallIcon = R.drawable.ic_launcher_background // Replace with your own icon
        if (smallIcon == 0) {
            // Log an error if the icon is invalid
            println("Error: Invalid small icon resource")
            return
        }

        // Build the notification
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Image Classification")
            .setContentText(result)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Show the notification
        with(NotificationManagerCompat.from(this)) {
            try {
                notify(NOTIFICATION_ID, builder.build())
                println("Notification shown successfully.")
            } catch (e: SecurityException) {
                println("SecurityException: ${e.message}")
            } catch (e: Exception) {
                println("Exception while showing notification: ${e.message}")
            }
        }
    }


    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATION_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission was granted
                val result = "Classification Result: Cat with confidence 98%"
                showNotification(result)
            } else {
                // Permission was denied
                // Handle the denial gracefully, perhaps show a message to the user
            }
        }
    }
}