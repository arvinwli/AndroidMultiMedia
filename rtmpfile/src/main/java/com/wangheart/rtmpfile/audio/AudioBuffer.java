package com.wangheart.rtmpfile.audio;

import com.wangheart.rtmpfile.utils.LogUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by Administrator on 2018/1/28.
 */

public class AudioBuffer {
    private int encodeFrameSize;
    private ByteBuffer buf;
    private ArrayBlockingQueue<byte[]> queue;
    private int count;

    public AudioBuffer(int encodeFrameSize) {
        this.encodeFrameSize = encodeFrameSize;
        buf = ByteBuffer.allocate(encodeFrameSize * 5);
        buf.mark();
        queue = new ArrayBlockingQueue<byte[]>(200);
    }

    public void put(byte[] data,int offset,int size) {
        buf.put(data,offset,size);
        int position = buf.position();
        while (position >= encodeFrameSize) {
            byte[] frameBuf = new byte[encodeFrameSize];
            buf.reset();
            buf.get(frameBuf, 0, encodeFrameSize);
            try {
                count++;
                LogUtils.w("size " + queue.size() + "  count " + count);
                queue.put(frameBuf);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            byte[] all = buf.array();
            buf.reset();
            buf.put(all, encodeFrameSize, position - encodeFrameSize);
            position = buf.position();
        }
    }

    public byte[] getFrameBuf() {
        if (!isEmpty()) {
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

    public boolean isEmpty() {
        return queue.size() == 0;

    }
}
