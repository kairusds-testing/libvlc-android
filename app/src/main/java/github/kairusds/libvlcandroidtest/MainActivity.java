package com.github.kairusds.libvlcandroidtest;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.HWDecoderUtil;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity  {
	private static final boolean USE_TEXTURE_VIEW = false;
	private static final boolean ENABLE_SUBTITLES = true;
	private static final String ASSET_FILENAME = "bbb.webm";

	private VLCVideoLayout mVideoLayout = null;

	private LibVLC mLibVLC = null;
	private MediaPlayer mMediaPlayer = null;
	private int audiotrackSessionId;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		init();

		mLibVLC = new LibVLC(this, getOptions());
		mMediaPlayer = new MediaPlayer(mLibVLC);
		mMediaPlayer.setAudioOutput(((HWDecoderUtil.getAudioOutputFromDevice() == HWDecoderUtil.AudioOutput.OPENSLES) ? "opensles_android" : null));

		mVideoLayout = findViewById(R.id.video_layout);
	}

	private void init(){
		if(audiotrackSessionId == null){
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			audiotrackSessionId = audioManager.generateAudioSessionId();
		}
	}

	private ArrayList<String> getOptions(){
		final ArrayList<String> options = new ArrayList<>();
		options.add("--no-audio-time-stretch");
		options.add("--avcodec-skiploopfilter");
		options.add("-1");
		options.add("--avcodec-skip-frame");
		options.add("0");
		options.add("--avcodec-skip-idct");
		options.add("0");
		options.add("--audio-resampler");
		options.add("soxr"); // use soxr for audio resampling
		options.add("--audiotrack-session-id=" + audiotrackSessionId);
		options.add("-v"); // minimum vlc logging
		options.add("--preferred-resolution=-1"); // use maximum resolution

		return options;
	}

	private void makeFullScreen() {
		getWindow().getDecorView().setSystemUiVisibility(
			View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
			View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
			View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			makeFullScreen();
	}

	@Override
	protected void onResume() {
		super.onResume();
		makeFullScreen();
		mMediaPlayer.play();
	}

	@Override
	protected void onDestroy(){
		super.onDestroy();
		mMediaPlayer.detachViews();
		mMediaPlayer.release();
		mLibVLC.release();
	}

	@Override
	protected void onStart(){
		super.onStart();

		mMediaPlayer.attachViews(mVideoLayout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW);

		try {
			final Media media = new Media(mLibVLC, getAssets().openFd(ASSET_FILENAME));
			media.setHWDecoderEnabled(true, true); // full hardware decoding
			mMediaPlayer.setMedia(media);
			media.release();
		} catch (IOException e){
			throw new RuntimeException("Invalid asset folder");
		}
		mMediaPlayer.play();
	}

	@Override
	protected void onStop(){ // activity is in the background or minimized
		super.onStop();
		mMediaPlayer.pause();
		// mMediaPlayer.stop();
		// mMediaPlayer.detachViews();
	}

}
