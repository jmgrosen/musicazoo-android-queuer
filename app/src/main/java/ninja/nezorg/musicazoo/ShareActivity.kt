package ninja.nezorg.musicazoo

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.ShareCompat
import android.support.v7.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

import kotlinx.android.synthetic.main.activity_share.*
import java.net.URLEncoder
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import com.android.volley.DefaultRetryPolicy
import android.app.Activity
import android.content.Intent
import android.net.Uri


const val MUSICAZOO_QUEUE_PREFIX = "http://musicazoo.mit.edu/enqueue?youtube_id="
const val CHANNEL_ID = "queueing"

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reader = ShareCompat.IntentReader.from(this)
        if (reader.isShareIntent) {
            val url = reader.text.toString()
            val (onSuccess, onFailure) = showNotification(url)
            queueVideo(url, onSuccess, onFailure)
        }

        finish()
    }

    fun queueVideo(url: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        val requestQueue = Volley.newRequestQueue(this)
        val encodedURL = URLEncoder.encode(url, "UTF-8")
        val req = JsonObjectRequest(Request.Method.POST,
                MUSICAZOO_QUEUE_PREFIX + encodedURL,
                null,
                Response.Listener { resp ->
                    if (resp.optBoolean("success", false)) {
                        onSuccess()
                    } else {
                        onFailure()
                    }
                },
                Response.ErrorListener { _ -> onFailure() })
        val retryPolicy = DefaultRetryPolicy(0, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        req.setRetryPolicy(retryPolicy)
        requestQueue.add(req)
    }

    fun makeNotificationId(): Int {
        val prefs = getSharedPreferences("notifications", 0)
        val id = prefs.getInt("notificationId", 0)
        prefs.edit().putInt("notificationId", id + 1).apply()
        return id
    }

    fun showNotification(url: String): Pair<() -> Unit, () -> Unit> {
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
