package com.wingtech.cloudserver.client.coder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.wingtech.cloudserver.client.MainActivity;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by lijiwei on 2018/2/8.
 */

public class AudioDecoder {

    private static final String TAG = "AudioDecoder";

    static AudioDecoder instance;

    MediaCodec audioDecoder;

    ByteBuffer[] inputBuffers;
    ByteBuffer[] outputBuffers;

    public static AudioDecoder newInstance() {
        if (instance == null) {
            instance = new AudioDecoder();
        }
        return instance;
    }

    public void initAudioDecoder() {
        try {
            audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioDecoder.configure(mediaFormat, null, null, 0);
            audioDecoder.start();
        } catch (IOException e) {
            Log.d(TAG, "initAudioDecoder: exception");
            e.printStackTrace();
        }
    }

    public void decodeAudio(byte[] aacData,int offset,int length) {

        inputBuffers = audioDecoder.getInputBuffers();
        outputBuffers = audioDecoder.getOutputBuffers();

        int inputIndex = 0;
        try {
            inputIndex = audioDecoder.dequeueInputBuffer(-1);
        } catch (Exception e) {
            Log.d(TAG, "decodeAudio: exception");
            e.printStackTrace();
        }
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputIndex];
            inputBuffer.clear();
            inputBuffer.put(aacData,offset,length);
            audioDecoder.queueInputBuffer(inputIndex, 0, length, 0, 0);
        }

        MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();
        int outputIndex = audioDecoder.dequeueOutputBuffer(decodeBufferInfo, 0);
        while (outputIndex >= 0) {
            ByteBuffer outputBuffer = outputBuffers[outputIndex];
            outputBuffer.position(decodeBufferInfo.offset);
            outputBuffer.limit(decodeBufferInfo.offset + decodeBufferInfo.size);
            byte[] pcmBytes = new byte[decodeBufferInfo.size];
            outputBuffer.get(pcmBytes);
            outputBuffer.clear();
            if (MainActivity.instance != null) {
                MainActivity.instance.initAudioTrack(pcmBytes);
            }
            audioDecoder.releaseOutputBuffer(outputIndex, false);
            outputIndex = audioDecoder.dequeueOutputBuffer(decodeBufferInfo, 1000);
        }
    }
}
