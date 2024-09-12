package com.wingtech.cloudserver.codec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.wingtech.cloudserver.MainActivity;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by lijiwei on 2018/2/8.
 */

public class AudioEncoder {

    private static final String TAG = "AudioEncoder";

    static AudioEncoder instance;

    MediaCodec audioEncoder;

    public static AudioEncoder newInstance() {
        if (instance == null) {
            instance = new AudioEncoder();
        }
        return instance;
    }

    public void initAudioEncoder() {
        try {
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
            audioEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        } catch (IOException e) {
            e.printStackTrace();
        }
        if (audioEncoder == null) {
            Log.d(TAG, "initAudioEncoder: 音频编码器初始化失败");
            return;
        }

        audioEncoder.start();
    }

    public void encodeAudio(byte[] pcmData) {
        int inputIndex = audioEncoder.dequeueInputBuffer(-1);
        ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputIndex);
        inputBuffer.clear();
        inputBuffer.put(pcmData);
        inputBuffer.limit(pcmData.length);

        audioEncoder.queueInputBuffer(inputIndex, 0, pcmData.length, 0, 0);

        MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();
        int outputIndex = audioEncoder.dequeueOutputBuffer(encodeBufferInfo, 0);
        while (outputIndex >= 0) {
            int outputBitSize = encodeBufferInfo.size;
            ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(outputIndex);
            outputBuffer.position(encodeBufferInfo.offset);
            outputBuffer.limit(encodeBufferInfo.offset + outputBitSize);
            byte[] aacBytes = new byte[outputBitSize];
            outputBuffer.get(aacBytes, 0, outputBitSize);
            outputBuffer.position(encodeBufferInfo.offset);
            audioEncoder.releaseOutputBuffer(outputIndex, false);
            outputIndex = audioEncoder.dequeueOutputBuffer(encodeBufferInfo, 0);
            MainActivity.mainActivity.audioServer.sendAutioData(aacBytes,0,aacBytes.length);
        }

    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 2; // CPE

        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public void release() {
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
    }

}
