/// MIT License
///
/// Copyright (c) 2022 Dorian Bachelot
///
/// Permission is hereby granted, free of charge, to any person obtaining
/// a copy of this software and associated documentation files (the
/// "Software"), to deal in the Software without restriction, including
/// without limitation the rights to use, copy, modify, merge, publish,
/// distribute, sublicense, and/or sell copies of the Software, and to
/// permit persons to whom the Software is furnished to do so, subject to
/// the following conditions:
///
/// The above copyright notice and this permission notice shall be
/// included in all copies or substantial portions of the Software.
///
/// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
/// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
/// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
/// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
/// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
/// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
/// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.dorianb.nightshield.nightshield

import android.annotation.SuppressLint
import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Intent
import android.os.Binder
import android.widget.Toast
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import kotlin.math.abs
import kotlin.math.log10

// Service to listen to the phone's microphone
class AudioService : Service() {

    // Method channel for Flutter
    private val CHANNEL = "net.dorianb.nightshield.audioservice"

    // Binder
    private val binder = AudioBinder()

    // Listening state
    var listening = false

    // Record time
    var recordTime = 1000.0

    // Audio value (decibels)
    var value = 0.0

    // Binder to get teh audio service
    inner class AudioBinder : Binder() {
        fun getService() : AudioService = this@AudioService
    }

    // Bind to service
    override fun onBind(intent: Intent): IBinder {
        showNotification()
        listening = true

        // Run thread
        Thread {

            // Create buffer
            val buffer = ArrayList<Double>()

            // Setup
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val audioBuffer = ShortArray(6400)

            // Setup record
            val record = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    12800)

            // Start record
            record.startRecording()
            var startTime = System.currentTimeMillis().toDouble()

            // Record loop
            while (listening) {

                // Check time difference
                val currentTime = System.currentTimeMillis().toDouble()
                if (currentTime - startTime > recordTime) {

                    // Convert to sum
                    var res = 0.0
                    for (x in buffer) {
                        res += x
                    }

                    // Compute mean
                    val mean = res / buffer.size.toDouble()

                    // Compute decibel value
                    value = 10 * log10(mean)

                    // Check if value is NaN or Inf
                    if (!value.isFinite())
                        value = 0.0

                    buffer.clear()

                    // Reset start time
                    startTime = System.currentTimeMillis().toDouble()
                }

                // Read buffer
                record.read(audioBuffer, 0, audioBuffer.size)

                // Browse audio buffer
                for (x in audioBuffer) {
                    if (x.toInt() == 0)
                        continue

                    // Add to buffer
                    buffer.add(abs(x.toDouble()))
                }
            }

            // Stop record
            record.stop()
            record.release()
        }.start()

        return binder
    }

    // Constructor
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // Destructor
    override fun onDestroy() {
        listening = false;
        stopSelf()
        super.onDestroy()
    }

    // Create notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "NightShield", NotificationManager.IMPORTANCE_HIGH)

            // Register the channel with the system
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {

        // Get MainActivity Intent
        val pendingIntent: PendingIntent =
                Intent(this, MainActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(this, 0, notificationIntent, FLAG_IMMUTABLE)
                }

        // Create the notification
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher) // TODO: real app logo
                    .setTicker("NightShield is running")
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle("NightShield")
                    .setContentText("NightShield is running")
                    .setContentIntent(pendingIntent)
        } else {
            Notification.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher) // TODO: real app logo
                    .setTicker("NightShield is running")
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle("NightShield")
                    .setContentText("NightShield is running")
                    .setContentIntent(pendingIntent)
        }

        // Send the notification
        startForeground(6002, builder.build())
    }
}