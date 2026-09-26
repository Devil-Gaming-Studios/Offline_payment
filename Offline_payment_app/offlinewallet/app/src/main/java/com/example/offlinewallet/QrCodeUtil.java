package com.example.offlinewallet;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class QrCodeUtil {

    // Encodes arbitrary text (here: the wallet's Base64 public key) as a QR bitmap.
    public static Bitmap generate(String content, int sizePx) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565);
        for (int x = 0; x < sizePx; x++) {
            for (int y = 0; y < sizePx; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }
}
