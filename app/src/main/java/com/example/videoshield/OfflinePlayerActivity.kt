package com.example.videoshield

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable

class OfflinePlayerActivity : LocalizedActivity() {
    private lateinit var video: VideoView
    private var equalizer: OfflineEqualizer? = null
    private lateinit var screenOn: PlaybackScreenOnController
    private val thumbnails=VideoThumbnailLoader()
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screenOn=PlaybackScreenOnController(window)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.offlineSystemInsets()
        val toolbar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),dp(4),dp(8),dp(4)) }
        toolbar.addView(IconButton(this,null,android.R.attr.borderlessButtonStyle).apply {
            setIcon(R.drawable.ic_ui_back); contentDescription=getString(R.string.ui_back); setOnClickListener { finish() }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        toolbar.addView(TextView(this).apply {
            text=intent.getStringExtra("title"); textSize=16f; setTextColor(Color.WHITE)
            maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; setPadding(dp(8),dp(8),dp(8),dp(8))
        },LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(IconButton(this,null,android.R.attr.borderlessButtonStyle).apply {
            setIcon(R.drawable.ic_ui_eq); contentDescription="EQ • Equalizer"
            setOnClickListener { EqDialog.show(this@OfflinePlayerActivity) }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(toolbar)
        video=object : VideoView(this) {
            override fun start() { super.start(); screenOn.update(!intent.getBooleanExtra("audioOnly",false)) }
            override fun pause() { super.pause(); screenOn.update(false) }
        }
        val content=FrameLayout(this)
        content.addView(video,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        if(intent.getBooleanExtra("audioOnly",false)) {
            val cover=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(24),0,dp(24),0) }
            cover.addView(ImageView(this).apply {
                scaleType=ImageView.ScaleType.CENTER_CROP
                background=GradientDrawable().apply { setColor(Color.rgb(39,39,39)); cornerRadius=dp(20).toFloat() }; clipToOutline=true
                thumbnails.bind(this,intent.getStringExtra("videoId").orEmpty())
            },LinearLayout.LayoutParams(dp(260),dp(146)))
            cover.addView(TextView(this).apply {
                text=intent.getStringExtra("title"); textSize=20f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
                maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END; setPadding(0,dp(24),0,dp(12))
            },LinearLayout.LayoutParams(-1,-2))
            cover.addView(TextView(this).apply { text=getString(R.string.ui_mp3_offline_playback); textSize=13f; setTextColor(Color.LTGRAY); gravity=Gravity.CENTER })
            content.addView(cover,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        }
        root.addView(content,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        val uri=intent.data
        if(uri==null || uri.scheme!="content") { finish(); return }
        video.setMediaController(MediaController(this).apply { setAnchorView(video) })
        video.setOnCompletionListener { screenOn.update(false) }
        video.setOnErrorListener { _,_,_ -> screenOn.update(false); AlertDialog.Builder(this).setMessage(getString(R.string.ui_this_device_cannot_play_this_file_s_codec_try_another_player_or_d))
            .setPositiveButton("OK") { _,_ -> finish() }.show(); true }
        video.setVideoURI(uri); video.setOnPreparedListener { player ->
            equalizer?.close(); equalizer = OfflineEqualizer(this, player)
            video.seekTo(state?.getInt("position") ?: 0); video.start()
        }
    }
    override fun onPause() { if(::video.isInitialized) video.pause(); super.onPause() }
    override fun onSaveInstanceState(state: Bundle) { if(::video.isInitialized) state.putInt("position",video.currentPosition); super.onSaveInstanceState(state) }
    override fun onDestroy() { if(::screenOn.isInitialized) screenOn.update(false); thumbnails.close(); EqDialog.dismiss(this); equalizer?.close(); equalizer=null; if(::video.isInitialized) video.stopPlayback(); super.onDestroy() }
}
