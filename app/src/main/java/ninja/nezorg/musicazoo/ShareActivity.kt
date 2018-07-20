package ninja.nezorg.musicazoo

import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.ShareCompat
import android.support.v7.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

import java.net.URLEncoder
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import com.android.volley.DefaultRetryPolicy
import android.app.Activity
import android.support.v7.preference.PreferenceManager

const val MUSICAZOO_QUEUE_PROTO = "http://"
const val MUSICAZOO_QUEUE_URI = "/enqueue?youtube_id="
const val DEFAULT_MUSICAZOO_SERVER = "musicazoo.mit.edu"
const val CHANNEL_ID = "queueing"

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false)

        val reader = ShareCompat.IntentReader.from(this)
        if (reader.isShareIntent) {
            val url = reader.text.toString()
            val (onSuccess, onFailure) = showNotification(url)
            queueVideo(url, onSuccess, onFailure)
        }

        finish()
    }

    private fun buildQueueUrl(youtubeUrl: String) : String {
        val server = getSharedPreferences("ninja.nezorg.musicazoo_preferences", 0)
                .getString("server", DEFAULT_MUSICAZOO_SERVER)
        val encodedUrl = URLEncoder.encode(youtubeUrl, "UTF-8")
        return MUSICAZOO_QUEUE_PROTO + server + MUSICAZOO_QUEUE_URI + encodedUrl
    }

    private fun queueVideo(youtubeUrl: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        val requestQueue = Volley.newRequestQueue(this)
        val req = JsonObjectRequest(Request.Method.POST,
                buildQueueUrl(youtubeUrl),
                null,
                Response.Listener { resp ->
                    if (resp.optBoolean("success", false)) {
                        onSuccess()
                    } else {
                        onFailure()
                    }
                },
                Response.ErrorListener { _ -> onFailure() })
        req.retryPolicy = DefaultRetryPolicy(
                0,
                -1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        requestQueue.add(req)
    }

    private fun makeNotificationId(): Int {
        val prefs = getSharedPreferences("nezorg.ninja.musicazoo_preferences", 0)
        val id = prefs.getInt("notificationId", 0)
        prefs.edit().putInt("notificationId", id + 1).apply()
        return id
    }

    private fun showNotification(url: String): Pair<() -> Unit, () -> Unit> {
        createNotificationChannel()
        val manager = NotificationManagerCompat.from(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Queueing video on Musicazoo...")
                .setContentText(url)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setProgress(0, 0, true)
        val notificationId = makeNotificationId()
        manager.notify(notificationId, builder.build())
        val onSuccess = {
            builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
                    .setProgress(0, 0, false)
                    .setContentTitle("Successfully queued video on Musicazoo")
            manager.notify(notificationId, builder.build())
        }
        val onFailure = {
            builder.setSmallIcon(android.R.drawable.stat_notify_error)
                    .setProgress(0, 0, false)
                    .setContentTitle("Failed to queue video on Musicazoo")
            manager.notify(notificationId, builder.build())
        }
        return Pair(onSuccess, onFailure)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Musicazoo queueing"
            val description = "what do you think"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

}
