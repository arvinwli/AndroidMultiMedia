package com.wangheart.rtmpfile.utils;

/**
 * @author Arvin
 * @date 2018/8/31
 * @e-mail arvinli@pacewear.com
 * @description
 */
public class BytesHexStrTranslate {
    private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * 方法一：
     * byte[] to hex string
     *
     * @param bytes
     * @return
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb=new StringBuilder();
        for (byte aByte : bytes) {
            int b1 = aByte & 0x0F;
            int b2 = (aByte >> 4) & 0x0F;
            sb.append(HEX_CHAR[b2]).append(HEX_CHAR[b1]).append(" ");
        }
        return sb.toString();
    }
}
