package com.wangheart.rtmpfile.utils;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Author : eric
 * CreateDate : 2017/12/11  11:34
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class IOUtils {
    public static OutputStream open(String path, boolean append) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path, append);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            return out;
        }
    }

    public static void write(OutputStream out, byte[] buffer, int offset, int length) {
        try {
            if (out != null)
                out.write(buffer, offset, length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close(Closeable io) {
        if (io != null) {
            try {
                io.close();
                io = null;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (io != null) {
                    try {
                        io.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
