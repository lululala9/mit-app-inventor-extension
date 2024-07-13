package com.joni.AudioCall;

import android.app.Activity;
import android.content.Context;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


@DesignerComponent(
        version = 1,
        iconName = "images/extension.png",
        description = "Extension for Audio Call functionality",
        category = ComponentCategory.EXTENSION,
        nonVisible = true)
@SimpleObject(external = true)
public class AudioCall extends AndroidNonvisibleComponent {

    private static final String LOG_TAG = "AudioCall";
    private static final int SAMPLE_RATE = 8000; // Hertz
    private static final int SAMPLE_INTERVAL = 20; // Milliseconds
    private static final int SAMPLE_SIZE = 2; // Bytes
    private static final int BUF_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE * 2; // Bytes

    private InetAddress address; // Address to call
    private int portSend;
    private int portReceive; // Port the packets are addressed to
    private boolean mic = false; // Enable mic?
    private boolean speakers = false; // Enable speakers?

    //private Form form;

    public AudioCall(ComponentContainer container) {
        super(container.$form());
        //form = container.$form();
    }

    @SimpleFunction(description = "Initialize Audio Call with the specified IP address")
    public void Initialize(String ipAddress) {
        try {
            address = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            form.dispatchErrorOccurredEvent(this, "Initialize", 0, e.toString());
        }
    }
     @SimpleFunction(description = "Set the port for Send")
    public void SetPortS(int portSend) {
        this.portSend = portSend;
    }
     @SimpleFunction(description = "Set the port for Receive")
    public void SetPortR(int portReceive) {
        this.portReceive = portReceive;
    }

    @SimpleFunction(description = "Start the audio call")
    public void StartCall() {
        startMic();
        startSpeakers();
    }

    @SimpleFunction(description = "End the audio call")
    public void EndCall() {
        Log.i(LOG_TAG, "Ending call!");
        muteMic();
        muteSpeakers();
    }

    @SimpleFunction(description = "Mute the microphone")
    public void muteMic() {
        mic = false;
    }

    @SimpleFunction(description = "Mute the speakers")
    public void muteSpeakers() {
        speakers = false;
    }

    @SimpleFunction(description = "Unmute the speakers")
    public void unmuteSpeakers() {
        //speakers = true;
        startSpeakers();
    }
    @SimpleFunction(description = "Unmute the microphone")
    public void unmuteMic() {
        //mic = true;
       // startMic();
    }

    private void startMic() {
        mic = true;
        Thread sendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "Send thread started. Thread id: " + Thread.currentThread().getId());
                AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10);
                int bytes_read = 0;
                int bytes_sent = 0;
                byte[] buf = new byte[BUF_SIZE];
                try {
                    DatagramSocket socket = new DatagramSocket();
                    audioRecorder.startRecording();
                    while (mic) {
                        bytes_read = audioRecorder.read(buf, 0, BUF_SIZE);
                        DatagramPacket packet = new DatagramPacket(buf, bytes_read, address, portSend);
                        socket.send(packet);
                        bytes_sent += bytes_read;
                        Log.i(LOG_TAG, "Total bytes sent: " + bytes_sent);
                        Thread.sleep(SAMPLE_INTERVAL, 0);
                    }
                    audioRecorder.stop();
                    audioRecorder.release();
                    socket.disconnect();
                    socket.close();
                    mic = false;
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "InterruptedException: " + e.toString());
                    mic = false;
                } catch (SocketException e) {
                    Log.e(LOG_TAG, "SocketException: " + e.toString());
                    mic = false;
                } catch (IOException e) {
                    Log.e(LOG_TAG, "IOException: " + e.toString());
                    mic = false;
                }
            }
        });
        sendThread.start();
    }

    private void startSpeakers() {
        if (!speakers) {
            speakers = true;
            Thread receiveThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.i(LOG_TAG, "Receive thread started. Thread id: " + Thread.currentThread().getId());
                    AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, BUF_SIZE, AudioTrack.MODE_STREAM);
                    track.play();
                    try {
                        DatagramSocket socket = new DatagramSocket(portReceive);
                        byte[] buf = new byte[BUF_SIZE];
                        while (speakers) {
                            DatagramPacket packet = new DatagramPacket(buf, BUF_SIZE);
                            socket.receive(packet);
                            Log.i(LOG_TAG, "Packet received: " + packet.getLength());
                            track.write(packet.getData(), 0, BUF_SIZE);
                        }
                        socket.disconnect();
                        socket.close();
                        track.stop();
                        track.flush();
                        track.release();
                        speakers = false;
                    } catch (SocketException e) {
                        Log.e(LOG_TAG, "SocketException: " + e.toString());
                        speakers = false;
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "IOException: " + e.toString());
                        speakers = false;
                    }
                }
            });
            receiveThread.start();
        }
    }
}
