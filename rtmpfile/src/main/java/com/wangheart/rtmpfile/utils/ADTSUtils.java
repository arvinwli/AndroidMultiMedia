package com.wangheart.rtmpfile.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Author : eric
 * CreateDate : 2018/1/4  15:28
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class ADTSUtils {
    private static Map<String, Integer> SAMPLE_RATE_TYPE;

    static {
        SAMPLE_RATE_TYPE = new HashMap<>();
        SAMPLE_RATE_TYPE.put("96000", 0);
        SAMPLE_RATE_TYPE.put("88200", 1);
        SAMPLE_RATE_TYPE.put("64000", 2);
        SAMPLE_RATE_TYPE.put("48000", 3);
        SAMPLE_RATE_TYPE.put("44100", 4);
        SAMPLE_RATE_TYPE.put("32000", 5);
        SAMPLE_RATE_TYPE.put("24000", 6);
        SAMPLE_RATE_TYPE.put("22050", 7);
        SAMPLE_RATE_TYPE.put("16000", 8);
        SAMPLE_RATE_TYPE.put("12000", 9);
        SAMPLE_RATE_TYPE.put("11025", 10);
        SAMPLE_RATE_TYPE.put("8000", 11);
        SAMPLE_RATE_TYPE.put("7350", 12);
    }

    public static int getSampleRateType(int sampleRate) {
        return SAMPLE_RATE_TYPE.get(sampleRate + "");
    }


    /**
     * 添加ADTS头
     *
     * @param packet
     * @param packetLen
     */
    public static void addADTStoPacket(int sampleRateType, byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = sampleRateType; // 44.1KHz
        int chanCfg = 2; // CPE


// fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}
