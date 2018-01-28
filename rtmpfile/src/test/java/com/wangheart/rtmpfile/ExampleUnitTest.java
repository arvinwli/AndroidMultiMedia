package com.wangheart.rtmpfile;

import com.wangheart.rtmpfile.utils.LogUtils;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {


    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.mark();
        bb.put("123456789".getBytes());
        log(bb.position() + "");
        byte[] buf = new byte[6];
        int position = bb.position();
        if (position >= 6) {
            bb.reset();
            bb.get(buf, 0, 6);
        }
        log("-- " + new String(buf));

        byte[] all = bb.array();
        bb.reset();
        bb.put(all, 6, position - 6);
        byte[] buff = bb.array();
        log(new String(buff) + " " + bb.position());
//        log(bb.position() + "");
//        bb.get(buff, 0, 3);
    }

    @Test
    public void test1() {
        final AudioBuffer audioBuffer = new AudioBuffer(10);
        log("asdf");
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 50; i++) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    audioBuffer.put("123456789".getBytes());
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 50; i++) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    byte[] b;
                    b = audioBuffer.getFrameBuf();
                    log(b == null ? "null" : new String(b));
                }
            }
        }).start();
    }

    public static void log(String content) {
        System.out.println(content);
    }

    public static class AudioBuffer {
        private int encodeFrameSize;
        private ByteBuffer buf;
        private ArrayBlockingQueue<byte[]> queue;

        public AudioBuffer(int encodeFrameSize) {
            this.encodeFrameSize = encodeFrameSize;
            buf = ByteBuffer.allocate(encodeFrameSize * 5);
            buf.mark();
            queue = new ArrayBlockingQueue<byte[]>(20);
        }

        public void put(byte[] data) {
            buf.put(data);
            byte[] frameBuf = new byte[encodeFrameSize];
            int position = buf.position();
            if (position >= encodeFrameSize) {
                buf.reset();
                buf.get(frameBuf, 0, encodeFrameSize);
                queue.add(frameBuf);
                byte[] all = buf.array();
                buf.reset();
                buf.put(all, encodeFrameSize, position - encodeFrameSize);
            }
        }

        public byte[] getFrameBuf() {
            if (queue.size() > 0) {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                return null;
            }
        }
    }
}