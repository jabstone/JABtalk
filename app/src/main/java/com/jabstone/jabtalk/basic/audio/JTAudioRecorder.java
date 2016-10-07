package com.jabstone.jabtalk.basic.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import com.jabstone.jabtalk.basic.JTApp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class JTAudioRecorder {
	private static int[] sampleRates = new int[] { 44100, 22050, 11025, 8000 };
	private String TAG = JTAudioRecorder.class.getSimpleName();
	private AudioRecord recorder = null;
	private int bufferSize = 4096;
	private Thread recordingThread = null;
	private boolean isRecording = false;
	private String fileName = null;
	private int selectedRate = 0;
	private short selectedChannel = 1;
	private short selectedBPP = 16;

	public JTAudioRecorder(String fileTarget) {
		fileName = fileTarget;		
	}

	private String getFilename() {
		return fileName;
	}

	private String getTempFilename() {
		return fileName + ".raw";
	}
	
	private AudioRecord getAudioRecorder() {
	    for (int rate : sampleRates) {
	        for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT }) {
	            for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO }) {
	                try {
	                    bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

	                    if (bufferSize > 0) {
	                        // check if we can instantiate and have a success
	                        AudioRecord recorder = new AudioRecord(AudioSource.MIC, rate, channelConfig, audioFormat, bufferSize);

	                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
	                        	selectedRate = rate;
	                        	selectedChannel = channelConfig == AudioFormat.CHANNEL_IN_STEREO ? (short)2 : (short)1;
	                        	selectedBPP = audioFormat == AudioFormat.ENCODING_PCM_16BIT ? (short)16 : (short)8;
	                        	
	                        	String format = audioFormat == AudioFormat.ENCODING_PCM_16BIT ? "PCM 16 Bit" : "PCM 8 Bit";
	                        	String channels = channelConfig == AudioFormat.CHANNEL_IN_STEREO ? "Stereo" : "Mono";
	                        	String diags = "Audio recorded using following settings: Rate: " + String.valueOf(rate) + "   " +
	                        			"Audio Format: " + format + "   " +
	                        			"Channel Config: " + channels;
	                        	JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, diags);
	                        	return recorder;
	                        }	                            
	                    }
					} catch (Exception ignored) {
					}
	            }
	        }
	    }	
	    return null;
	}

	public void startRecording() throws Exception {
		recorder = getAudioRecorder();
		if(recorder == null) {
			throw new Exception("Could not initialize audio recorder");
		}

		int i = recorder.getState();
		if (i == 1)
			recorder.startRecording();

		isRecording = true;

		recordingThread = new Thread(new Runnable() {

			@Override
			public void run() {
				writeAudioDataToFile();
			}
		}, "AudioRecorder Thread");

		recordingThread.setPriority(Thread.MAX_PRIORITY);
		recordingThread.start();
	}

	private void writeAudioDataToFile() {
		byte data[] = new byte[bufferSize];
		String filename = getTempFilename();
		FileOutputStream os = null;

		try {
			os = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Could open file for audio recording");
		}

		int read;

		if (null != os) {
			while (isRecording) {
				read = recorder.read(data, 0, bufferSize);

				if (AudioRecord.ERROR_INVALID_OPERATION != read) {
					try {
						os.write(data);
					} catch (IOException e) {
						JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
					}
				}
			}

			try {
				os.close();
			} catch (IOException e) {
				JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
			}
		}
	}

	public void stopRecording() {
		if (null != recorder) {
			isRecording = false;
			int i = recorder.getState();
			if (i == 1)
				recorder.stop();
			recorder.release();

			recorder = null;
			recordingThread = null;
		}

		copyWaveFile(getTempFilename(), getFilename());
		deleteTempFile();
	}

	private void deleteTempFile() {
		File file = new File(getTempFilename());
		file.delete();
	}

	private void copyWaveFile(String inFilename, String outFilename) {
		FileInputStream in;
		FileOutputStream out;

		byte[] data = new byte[bufferSize];

		try {
			in = new FileInputStream(inFilename);
			out = new FileOutputStream(outFilename);
			int totalDataLen = (int)in.getChannel().size();

			WaveHeader wh = new WaveHeader(WaveHeader.FORMAT_PCM,  selectedChannel, selectedRate, selectedBPP, totalDataLen);
			wh.write(out);

			while (in.read(data) != -1) {
				out.write(data);
			}

			in.close();
			out.close();
		} catch (IOException e) {
			JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
		}
	}


}