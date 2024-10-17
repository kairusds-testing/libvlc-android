package github.kairusds.libvlcandroidtest;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import static org.videolan.libvlc.MediaPlayer.Event;
import org.videolan.libvlc.util.VLCVideoLayout;

import org.apache.tika.Tika;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity  {
	private static final boolean USE_TEXTURE_VIEW = false;
	private static final boolean ENABLE_SUBTITLES = false;
	private static final int FILE_ACCESS_REQUEST = 1;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private ExecutorService executor = null;

	private final Tika tika = new Tika();

	private VLCVideoLayout videoLayout = null;
	private LibVLC libVLC = null;
	private MediaPlayer mediaPlayer = null;
	private long time = 0L;
	private int audiotrackSessionId = 0;
	private final ArrayList<String> options = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		audiotrackSessionId = audioManager.generateAudioSessionId();

		options.add("--avcodec-skiploopfilter");
		options.add("0");
		options.add("--avcodec-skip-frame");
		options.add("0");
		options.add("--avcodec-skip-idct");
		options.add("0");
		options.add("--audio-resampler");
		options.add("soxr"); // use soxr for audio resampling
		options.add("-v"); // minimum vlc logging
		options.add("--preferred-resolution=-1"); // use maximum resolution
		options.add("--audiotrack-session-id=" + audiotrackSessionId);

		videoLayout = findViewById(R.id.video_layout);
		videoLayout.setOnClickListener(v -> {
			if(libvlcAvailable()){
				if(mediaPlayer.isPlaying()){
					pause();
				}else{
					play();
				}
			}else{
				if(executor == null){ // prevent taps in quick succession from executing the code below multiple times
					executor = Executors.newSingleThreadExecutor();
					showFilePickerDialog(Environment.getExternalStorageDirectory());
				}
			}
		});
		videoLayout.setOnLongClickListener(v -> {
			if(libvlcAvailable()){
				destroyVLC();
				toast("Stopped video.");
				return true;
			}
			return false;
		});

		checkPermissions();
	}

	private void checkPermissions(){
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()){
			Uri uri = Uri.parse("package:" + getPackageName());
			Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
			startActivityForResult(intent, FILE_ACCESS_REQUEST);
		}else if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, FILE_ACCESS_REQUEST);
		}
	}

	private void toast(String str){
		Toast.makeText(this, str, Toast.LENGTH_LONG).show();
	}

	private void initVLC(){
		if(libvlcAvailable()) return;
		libVLC = new LibVLC(this, options);
		mediaPlayer = new MediaPlayer(libVLC);
	}

	private void destroyVLC(){
		if(!libvlcAvailable()) return;
		time = 0L;
		mediaPlayer.stop();
		mediaPlayer.detachViews();
		mediaPlayer.release();
		libVLC.release();
		mediaPlayer = null;
		libVLC = null;
	}

	private boolean libvlcAvailable(){
		return libVLC != null && mediaPlayer != null;
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
		// if(time > 0L) mediaPlayer.setTime(time);
	}

	@Override
	protected void onDestroy(){
		super.onDestroy();
		shutdownExecutor();
		destroyVLC();
	}

	private void pause(){
		if(!libvlcAvailable()) return;
		time = mediaPlayer.getTime();
		mediaPlayer.pause();
	}

	private void play(){
		if(!libvlcAvailable()) return;
		mediaPlayer.setTime(time);
		mediaPlayer.play();
	}

	private void attachViews(){
		if(!libvlcAvailable()) return;
		mediaPlayer.attachViews(videoLayout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW);
	}

	private void setVLCMedia(String path){
		if(libvlcAvailable()) return;
		initVLC();
		attachViews();

		try{
			final Media media = new Media(libVLC, path);
			media.setHWDecoderEnabled(true, true); // full hardware decoding
			mediaPlayer.setMedia(media);
			// media.release();
		}catch(Exception e){
			toast(e.getMessage());
		}
		mediaPlayer.setEventListener(event -> {
			switch(event.type){
				case Event.TimeChanged:
					time = event.getTimeChanged();
					break;
				case Event.EndReached:
					destroyVLC();
					break;
			}
		});
		
		play();
	}

	private void showFilePickerDialog(File dir){
		try{
			executor.execute(() -> {
				File[] files = dir.listFiles(file -> {
					if(file.isDirectory() && file.canRead()){
						return true;
					}

					String mimetype = tika.detect(file.getName());
					return mimetype.startsWith("video/");
				});
				
				if(files == null){
					toast("Unable to access directory");
					shutdownExecutor();
					return;
				}
				
				Arrays.sort(files, new Comparator<File>(){
					@Override
					public int compare(File f1, File f2){
						if(f1.isDirectory() && !f2.isDirectory()){
							return -1;
						}

						if(!f1.isDirectory() && f2.isDirectory()){
							return 1;
						}

						String name1 = f1.getName();
						String name2 = f2.getName();
	
						boolean isSymbol1 = !Character.isLetterOrDigit(name1.charAt(0));
						boolean isSymbol2 = !Character.isLetterOrDigit(name2.charAt(0));

						if(isSymbol1 && !isSymbol2) return -1;
						if(!isSymbol1 && isSymbol2) return 1;

						return name1.compareToIgnoreCase(name2);
					}
				});

				ArrayList<String> fileList = new ArrayList<>();
				HashMap<String, String> paths = new HashMap<>();			
				fileList.add("..");
	
				for(File file : files){
					fileList.add(file.getName());
					paths.put(file.getName(), file.getAbsolutePath());
				}

				handler.post(() -> {
					ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.select_dialog_item, fileList);
					AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
					builder.setCancelable(false); // prevent dismiss on click outside of dialog
					builder.setTitle("Choose a video file");
					builder.setAdapter(adapter, (dialog, which) -> {
						if(which == 0){
							File parentDir = dir.getParentFile();
							if(parentDir != null && parentDir.canRead()){
								showFilePickerDialog(parentDir);
							}else{
								toast("No parent directory");
								showFilePickerDialog(dir);
							}
						}else{
							String filename = fileList.get(which);
							String filepath = paths.get(filename);
							File selectedFile = new File(filepath);
							if(selectedFile.isDirectory()){
								showFilePickerDialog(selectedFile);
							}else{
								setVLCMedia(filepath);
								shutdownExecutor();
							}
						}
					});
					builder.setNegativeButton("Cancel", (dialog, which) -> {
						shutdownExecutor();
					});
					builder.show();
				});
			});
		}catch(Exception e){
			toast(e.getMessage());
		}
	}

	private void shutdownExecutor(){
		if(executor != null){
			executor.shutdown();
			executor = null;
		}
	}

	@Override  
	protected void onActivityResult(int requestCode, int resultCode, Intent data){  
		super.onActivityResult(requestCode, resultCode, data);  
		if(requestCode == FILE_ACCESS_REQUEST){
			checkPermissions();
		}
	}

	@Override
	protected void onStart(){ // activity is in view again or the real resumed state
		super.onStart();
		// mediaPlayer.play();
		attachViews();
		play();
	}

	@Override
	protected void onStop(){ // activity is in the background or minimized
		super.onStop();
		if(libvlcAvailable()){
			time = mediaPlayer.getTime();
			mediaPlayer.detachViews();
		}
		pause();
		// mediaPlayer.pause();
		// mediaPlayer.stop();
	}

}