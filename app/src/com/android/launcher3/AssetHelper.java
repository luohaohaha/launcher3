package com.android.launcher3;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * Project: launcher3-github<br/>
 * Package: com.android.launcher3<br/>
 * ClassName: AssetHelper<br/>
 * Description: asset 读取操作类<br/>
 * Date: 2020/3/3 3:42 PM <br/>
 * <p>
 * Author  LuoHao<br/>
 * Version 1.0<br/>
 * since JDK 1.6<br/>
 * <p>
 */
public class AssetHelper {

    public static String TAG = "AssetHelper";

    private AssetHelper(){

    }

    /**
     * 获取asset目录下文件
     *
     * @param fileName 文件全称,包含后缀名 例如a.png , x.9.png , yy.jpg
     * @return inputStream
     */
    public static InputStream asset_getFile(Context context,String fileName) {
        try {
            InputStream inStream = context.getAssets().open(fileName);
            return inStream;
        } catch (IOException e) {
            Log.e(TAG,"asset:" + fileName + ",no exist");
        }
        return null;
    }

    /**
     * 将InputStream 转换为String
     *
     * @param is
     * @param encoding 编码格式,可以为null,null表示适用utf-8
     */
    public static String stream_2String(InputStream is, String encoding) throws IOException {
        if (is == null)
            return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i = -1;
        while ((i = is.read()) != -1) {
            baos.write(i);
        }
        baos.close();
        String result = null;
        if (encoding == null) {
            encoding = "UTF-8";
        }
        result = baos.toString(encoding);
        return result;
    }

    /**
     * String => inputStream
     *
     * @param src
     * @param charsetName 编码格式 可以为null,null表示适用utf-8
     * @return
     */
    public static InputStream string_2stream(String src, String charsetName) {
        try {
            if (null == charsetName) {
                charsetName = "UTF-8";
            }
            byte[] bArray = src.getBytes(charsetName);
            InputStream is = new ByteArrayInputStream(bArray);
            return is;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG,e.toString());
        }
        return null;
    }
}
