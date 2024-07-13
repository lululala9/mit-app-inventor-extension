package com.joni.callUdp;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.PermissionResultHandler;
import com.google.appinventor.components.runtime.util.YailList;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

@DesignerComponent(
        version = 1,
        description = "A component for UDP audio calls",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = ""
)
@SimpleObject(external = true)
@UsesPermissions({Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.INTERNET})
public class callUdp extends AndroidNonvisibleComponent {

    private static final String LOG_TAG = "AudioCall";
    private static final int SAMPLE_RATE = 8000; // Hertz
    private static final int SAMPLE_INTERVAL = 20; // Milliseconds
    private static final int SAMPLE_SIZE = 2; // Bytes
    private static final int BUF_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE * 2; //Bytes
    private InetAddress address; // Address to call
    private int sendPort = 2222; // Port the packets are sent from
    private int receivePort = 4444; // Port the packets are received on
    private boolean mic = false; // Enable mic?
    private boolean speakers = false; // Enable speakers?

    public callUdp(ComponentContainer container) {
        super(container.$form());
    }

    @SimpleFunction(description = "Initialize the audio call with the given IP address")
    public void Initialize(String ipAddress) throws UnknownHostException {
        this.address = InetAddress.getByName(ipAddress);
        Log.i(LOG_TAG, "Initialized with IP address: " + ipAddress);
    }

    @SimpleFunction(description = "Start the audio call")
    public void StartCall() {
        form.askPermission(Manifest.permission.RECORD_AUDIO, new PermissionResultHandler() {
            @Override
            public void HandlePermissionResponse(String permission, boolean granted) {
                if (granted) {
                    Log.i(LOG_TAG, "RECORD_AUDIO permission granted");
                    startMic();
                    startSpeakers();
                } else {
                    Log.e(LOG_TAG, "RECORD_AUDIO permission denied");
                }
            }
        });
    }

    @SimpleFunction(description = "End the audio call")
    public void EndCall() {
        Log.i(LOG_TAG, "Ending call!");
        MuteMic();
        MuteSpeakers();
    }

    @SimpleFunction(description = "Mute the microphone")
    public void MuteMic() {
        mic = false;
    }

    @SimpleFunction(description = "Mute the speakers")
    public void MuteSpeakers() {
        speakers = false;
    }
/*
    @SimpleFunction(description = "Set the port for sending audio data")
    public void SetSendPort(int port) {
        this.sendPort = port;
        Log.i(LOG_TAG, "Send port set to: " + port);
    }

    @SimpleFunction(description = "Set the port for receiving audio data")
    public void SetReceivePort(int port) {
        this.receivePort = port;
        Log.i(LOG_TAG, "Receive port set to: " + port);
    }
*/
    private void startMic() {
        mic = true;
        new Thread(new MicRunnable()).start();
    }

    private void startSpeakers() {
        if (!speakers) {
            speakers = true;
            new Thread(new SpeakerRunnable()).start();
        }
    }

    private class MicRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(LOG_TAG, "Mic thread started. Thread id: " + Thread.currentThread().getId());
            AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10);
            int bytes_read;
            int bytes_sent = 0;
            byte[] buf = new byte[BUF_SIZE];
            try {
                Log.i(LOG_TAG, "Packet destination: " + address.toString());
                DatagramSocket socket = new DatagramSocket();
                audioRecorder.startRecording();
                while (mic) {
                    bytes_read = audioRecorder.read(buf, 0, BUF_SIZE);
                    DatagramPacket packet = new DatagramPacket(buf, bytes_read, address, sendPort);
                    socket.send(packet);
                    bytes_sent += bytes_read;
                    Log.i(LOG_TAG, "Total bytes sent: " + bytes_sent);
                    Thread.sleep(SAMPLE_INTERVAL);
                }
                audioRecorder.stop();
                audioRecorder.release();
                socket.disconnect();
                socket.close();
                mic = false;
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Mic thread interrupted: " + e.toString());
                mic = false;
            } catch (IOException e) {
                Log.e(LOG_TAG, "Mic thread IOException: " + e.toString());
                mic = false;
            }
        }
    }

    private class SpeakerRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(LOG_TAG, "Speaker thread started. Thread id: " + Thread.currentThread().getId());
            AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, BUF_SIZE, AudioTrack.MODE_STREAM);
            track.play();
            try {
                DatagramSocket socket = new DatagramSocket(receivePort);
                byte[] buf = new byte[BUF_SIZE];
                while (speakers) {
                    DatagramPacket packet = new DatagramPacket(buf, BUF_SIZE);
                    socket.receive(packet);
                    Log.i(LOG_TAG, "Packet received: " + packet.getLength());
                    track.write(packet.getData(), 0, packet.getLength());
                }
                socket.disconnect();
                socket.close();
                track.stop();
                track.flush();
                track.release();
                speakers = false;
            } catch (IOException e) {
                Log.e(LOG_TAG, "Speaker thread IOException: " + e.toString());
                speakers = false;
            }
        }
    }
}