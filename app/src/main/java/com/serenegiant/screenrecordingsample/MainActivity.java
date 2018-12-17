package com.serenegiant.screenrecordingsample;
/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: MainActivity.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.serenegiant.dialog.MessageDialogFragment;
import com.serenegiant.service.ScreenRecorderService;
import com.serenegiant.utils.BuildCheck;
import com.serenegiant.utils.HandlerThreadHandler;
import com.serenegiant.utils.PermissionCheck;

import org.w3c.dom.Text;

public final class MainActivity extends Activity
	implements MessageDialogFragment.MessageDialogListener {

	private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
	private static final boolean DEBUG = false;
	private static final String TAG = "MainActivity";

	private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;

	private ToggleButton mRecordButton;
	private ToggleButton mPauseButton;
	private Button mStopButton;
	private MyBroadcastReceiver mReceiver;
	private TextView question;
	private TextView warn;
	private TextView left_time;
	private TextureView mTextureView;

	private CountDownTimer preTimer;
	private CountDownTimer recTimer;
	private ArrayList<String> questions = new ArrayList<>();
	private int question_number = 0;


	private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
			setupCamera(width, height);
			connectCamera();
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			return false;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {

		}
	};
	private CameraDevice mCameraDevice;
	private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback(){
		@Override
		public void onOpened(CameraDevice camera){
			mCameraDevice = camera;
			startPreview();
//			Toast.makeText(getApplicationContext(),
//					"Камера подключена!", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onDisconnected(CameraDevice camera){
			camera.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(CameraDevice camera, int error){

		}
	};
	private HandlerThread mBackgroundHandlerThread;
	private Handler mBackgroundHandler;
	private String mCameraId;
	private Size mPreviewSize;
	private CaptureRequest.Builder mCaptureRequestBuilder;
	private static SparseIntArray ORIENTATIONS = new SparseIntArray();
	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 0);
		ORIENTATIONS.append(Surface.ROTATION_90, 90);
		ORIENTATIONS.append(Surface.ROTATION_180, 180);
		ORIENTATIONS.append(Surface.ROTATION_270, 270);
	}


	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		setContentView(R.layout.activity_main);
		mRecordButton = (ToggleButton)findViewById(R.id.record_button);
		mPauseButton = (ToggleButton)findViewById(R.id.pause_button);
		mStopButton = (Button)findViewById(R.id.stop_button);

		question = (TextView) findViewById(R.id.question);
		left_time = (TextView) findViewById(R.id.left_time);
		warn = (TextView) findViewById(R.id.warn);

		updateRecording(false, false);
		if (mReceiver == null) {
			mReceiver = new MyBroadcastReceiver(this);
		}
		mTextureView = (TextureView) findViewById(R.id.textureView);

		questions.add("Вопрос 1: Как Вы можете охарактеризовать себя в двух словах?");
		questions.add("Вопрос 2: Есть ли у Вас свой девиз, миссия?");
		questions.add("Вопрос 3: В чем заключается главная экспертиза человека Вашего уровня?");
		questions.add("Вопрос 4: Почему вы выбрали naimi.kz?");
		questions.add("Вопрос 5: Где живешь?");

		startQuestion(question_number);

		mStopButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				recTimer.cancel();
				recTimer.onFinish();
			}
		});
	}

	private void startQuestion(int i) {

		question.setText(questions.get(i));
		warn.setText(R.string.warn);
		preTimer = new CountDownTimer(10000, 1000) {
			public void onTick(long millisUntilFinished) {
				left_time.setText("" + String.format("%d сек",
						TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)));
			}

			public void onFinish() {
				if (question_number ==0){
					mRecordButton.toggle();
				} else {
					mPauseButton.toggle();
				}
				warn.setText(R.string.left);
				mStopButton.setVisibility(View.VISIBLE);
				recTimer = new CountDownTimer(120000, 1000) {
					public void onTick(long millisUntilFinished) {
						left_time.setText("" + String.format("%d:%d",
								TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished),
								TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
										TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))));
					}

					public void onFinish() {
						if (question_number < questions.size()-1) {
							mPauseButton.toggle();
							startQuestion(++question_number);
						} else {
							mRecordButton.toggle();
						}
						mStopButton.setVisibility(View.INVISIBLE);

					}
				}.start();
			}
		}.start();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocas){
		super.onWindowFocusChanged(hasFocas);
		View decorView = getWindow().getDecorView();
		if (hasFocas){
			decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
			| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
			| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_FULLSCREEN
			| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
		}
	}

	private static class CompareSizeByArea implements Comparator<Size> {
		@Override
		public int compare(Size lhs, Size rhs) {
			return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
					(long) rhs.getWidth() * rhs.getHeight());
		}
	}

	private void setupCamera(int width, int height){
		CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		try {
			for (String cameraId : cameraManager.getCameraIdList()){
				CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
				if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
						CameraCharacteristics.LENS_FACING_BACK){
					continue;
				}
				StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
				int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
				int totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
				boolean swapRotation = totalRotation == 90 || totalRotation == 270;
				int rotatedWidth = width;
				int rotatedHeight = height;
				if (swapRotation) {
					rotatedWidth = height;
					rotatedHeight = width;
				}
				mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
				mCameraId = cameraId;
				return;
			}
		} catch (CameraAccessException e){
			e.printStackTrace();
		}
	}

	private void connectCamera(){
		CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
						PackageManager.PERMISSION_GRANTED) {
					cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
				} else {
					if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
						Toast.makeText(this,
								"Требуется разрешение на камеру", Toast.LENGTH_SHORT).show();
					}
					requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
				}

			} else {
				cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
			}
		} catch (CameraAccessException e){
			e.printStackTrace();
		}
	}

	private void startPreview(){
		SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
		surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
		Surface previewSurface = new Surface(surfaceTexture);

		try{
			mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mCaptureRequestBuilder.addTarget(previewSurface);

			mCameraDevice.createCaptureSession(Arrays.asList(previewSurface),
					new CameraCaptureSession.StateCallback() {
						@Override
						public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
							try {
								cameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null, mBackgroundHandler);
							} catch (CameraAccessException e) {
								e.printStackTrace();
							}
						}

						@Override
						public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
							Toast.makeText(getApplicationContext(),
									"Ошибка настройки камеры", Toast.LENGTH_SHORT).show();
						}
					}, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}

	}

	private void closeCamera(){
		if (mCameraDevice !=null ){
			mCameraDevice.close();
			mCameraDevice = null;
		}
	}

	private void startBackgroundThread(){
		mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
		mBackgroundHandlerThread.start();
		mBackgroundHandler = new Handler (mBackgroundHandlerThread.getLooper());
	}

	private void stopBackgroundThread(){
		mBackgroundHandlerThread.quitSafely();
		try {
			mBackgroundHandlerThread.join();
			mBackgroundHandlerThread=null;
			mBackgroundHandler = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){
		int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
		deviceOrientation = ORIENTATIONS.get(deviceOrientation);
		return (sensorOrientation + deviceOrientation + 360) % 360;
	}

	private static Size chooseOptimalSize(Size[] choices, int width, int height){
		List<Size> bigEnough = new ArrayList<Size>();
		for (Size option : choices) {
			if (option.getHeight() == option.getWidth() * height / width &&
					option.getWidth() >= width && option.getHeight() >= height) {
				bigEnough.add(option);
			}
		}
		if (bigEnough.size()> 0) {
			return Collections.min(bigEnough, new CompareSizeByArea());
		} else {
			return choices[0];
		}
	}

	public void setRequestCameraPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
			if (grantResults[0] != PackageManager.PERMISSION_GRANTED){
				Toast.makeText(getApplicationContext(),
						"Приложение не будет работать без сервисов камеры", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		startBackgroundThread();
		if (mTextureView.isAvailable()){
			setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
			connectCamera();
		} else {
			mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
		}
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ScreenRecorderService.ACTION_QUERY_STATUS_RESULT);
		registerReceiver(mReceiver, intentFilter);
		queryRecordingStatus();
	}

	@Override
	protected void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		unregisterReceiver(mReceiver);
		closeCamera();
		stopBackgroundThread();
		super.onPause();

	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (DEBUG) Log.v(TAG, "onActivityResult:resultCode=" + resultCode + ",data=" + data);
		super.onActivityResult(requestCode, resultCode, data);
		if (REQUEST_CODE_SCREEN_CAPTURE == requestCode) {
            if (resultCode != Activity.RESULT_OK) {
                // when no permission
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                return;
            }
            startScreenRecorder(resultCode, data);
        }
	}

	private final OnCheckedChangeListener mOnCheckedChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
			switch (buttonView.getId()) {
			case R.id.record_button:
				if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
					if (isChecked) {
						final MediaProjectionManager manager
							= (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
							final Intent permissionIntent = manager.createScreenCaptureIntent();
						   startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE);
					} else {
						final Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
						intent.setAction(ScreenRecorderService.ACTION_STOP);
						startService(intent);
					}
				} else {
					mRecordButton.setOnCheckedChangeListener(null);
					try {
						mRecordButton.setChecked(false);
					} finally {
						mRecordButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
					}
				}
				break;
			case R.id.pause_button:
				if (isChecked) {
					final Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
					intent.setAction(ScreenRecorderService.ACTION_PAUSE);
					startService(intent);
				} else {
					final Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
					intent.setAction(ScreenRecorderService.ACTION_RESUME);
					startService(intent);
				}
				break;
			}
		}
	};

	private void queryRecordingStatus() {
		if (DEBUG) Log.v(TAG, "queryRecording:");
		final Intent intent = new Intent(this, ScreenRecorderService.class);
		intent.setAction(ScreenRecorderService.ACTION_QUERY_STATUS);
		startService(intent);
	}

	private void startScreenRecorder(final int resultCode, final Intent data) {
		final Intent intent = new Intent(this, ScreenRecorderService.class);
		intent.setAction(ScreenRecorderService.ACTION_START);
		intent.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode);
		intent.putExtras(data);
		startService(intent);
	}

	private void updateRecording(final boolean isRecording, final boolean isPausing) {
		if (DEBUG) Log.v(TAG, "updateRecording:isRecording=" + isRecording + ",isPausing=" + isPausing);
		mRecordButton.setOnCheckedChangeListener(null);
		mPauseButton.setOnCheckedChangeListener(null);
		try {
			mRecordButton.setChecked(isRecording);
			mPauseButton.setEnabled(isRecording);
			mPauseButton.setChecked(isPausing);
		} finally {
			mRecordButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
			mPauseButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
		}
	}

	private static final class MyBroadcastReceiver extends BroadcastReceiver {
		private final WeakReference<MainActivity> mWeakParent;
		public MyBroadcastReceiver(final MainActivity parent) {
			mWeakParent = new WeakReference<MainActivity>(parent);
		}

		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (DEBUG) Log.v(TAG, "onReceive:" + intent);
			final String action = intent.getAction();
			if (ScreenRecorderService.ACTION_QUERY_STATUS_RESULT.equals(action)) {
				final boolean isRecording = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_RECORDING, false);
				final boolean isPausing = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_PAUSING, false);
				final MainActivity parent = mWeakParent.get();
				if (parent != null) {
					parent.updateRecording(isRecording, isPausing);
				}
			}
		}
	}

//================================================================================
// methods related to new permission model on Android 6 and later
//================================================================================
	/**
	 * Callback listener from MessageDialogFragmentV4
	 * @param dialog
	 * @param requestCode
	 * @param permissions
	 * @param result
	 */
	@SuppressLint("NewApi")
	@Override
	public void onMessageDialogResult(final MessageDialogFragment dialog, final int requestCode, final String[] permissions, final boolean result) {
		if (result) {
			// request permission(s) when user touched/clicked OK
			if (BuildCheck.isMarshmallow()) {
				requestPermissions(permissions, requestCode);
				return;
			}
		}
		// check permission and call #checkPermissionResult when user canceled or not Android6(and later)
		for (final String permission: permissions) {
			checkPermissionResult(requestCode, permission, PermissionCheck.hasPermission(this, permission));
		}
	}

	/**
	 * callback method when app(Fragment) receive the result of permission result from ANdroid system
	 * @param requestCode
	 * @param permissions
	 * @param grantResults
	 */
	@Override
	public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);	// 何もしてないけど一応呼んどく
		final int n = Math.min(permissions.length, grantResults.length);
		for (int i = 0; i < n; i++) {
			checkPermissionResult(requestCode, permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED);
		}
	}

	/**
	 * check the result of permission request
	 * if app still has no permission, just show Toast
	 * @param requestCode
	 * @param permission
	 * @param result
	 */
	protected void checkPermissionResult(final int requestCode, final String permission, final boolean result) {
		// show Toast when there is no permission
		if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
			onUpdateAudioPermission(result);
			if (!result) {
				Toast.makeText(this, R.string.permission_audio, Toast.LENGTH_SHORT).show();
			}
		}
		if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
			onUpdateExternalStoragePermission(result);
			if (!result) {
				Toast.makeText(this, R.string.permission_ext_storage, Toast.LENGTH_SHORT).show();
			}
		}
		if (Manifest.permission.INTERNET.equals(permission)) {
			onUpdateNetworkPermission(result);
			if (!result) {
				Toast.makeText(this, R.string.permission_network, Toast.LENGTH_SHORT).show();
			}
		}
	}

	/**
	 * called when user give permission for audio recording or canceled
	 * @param hasPermission
	 */
	protected void onUpdateAudioPermission(final boolean hasPermission) {
	}

	/**
	 * called when user give permission for accessing external storage or canceled
	 * @param hasPermission
	 */
	protected void onUpdateExternalStoragePermission(final boolean hasPermission) {
	}

	/**
	 * called when user give permission for accessing network or canceled
	 * this will not be called
	 * @param hasPermission
	 */
	protected void onUpdateNetworkPermission(final boolean hasPermission) {
	}

	protected static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 0x01;
	protected static final int REQUEST_PERMISSION_AUDIO_RECORDING = 0x02;
	protected static final int REQUEST_PERMISSION_NETWORK = 0x03;

	/**
	 * check whether this app has write external storage
	 * if this app has no permission, show dialog
	 * @return true this app has permission
	 */
	protected boolean checkPermissionWriteExternalStorage() {
		if (!PermissionCheck.hasWriteExternalStorage(this)) {
			MessageDialogFragment.showDialog(this, REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE,
				R.string.permission_title, R.string.permission_ext_storage_request,
				new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
			return false;
		}
		return true;
	}

	/**
	 * check whether this app has permission of audio recording
	 * if this app has no permission, show dialog
	 * @return true this app has permission
	 */
	protected boolean checkPermissionAudio() {
		if (!PermissionCheck.hasAudio(this)) {
			MessageDialogFragment.showDialog(this, REQUEST_PERMISSION_AUDIO_RECORDING,
				R.string.permission_title, R.string.permission_audio_recording_request,
				new String[]{Manifest.permission.RECORD_AUDIO});
			return false;
		}
		return true;
	}

	/**
	 * check whether permission of network access
	 * if this app has no permission, show dialog
	 * @return true this app has permission
	 */
	protected boolean checkPermissionNetwork() {
		if (!PermissionCheck.hasNetwork(this)) {
			MessageDialogFragment.showDialog(this, REQUEST_PERMISSION_NETWORK,
				R.string.permission_title, R.string.permission_network_request,
				new String[]{Manifest.permission.INTERNET});
			return false;
		}
		return true;
	}
}
