package com.example.visionica;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONException;
import org.json.JSONObject;

public class QRCodeHelper {

    /**
     * Genera un código QR con la información del estudiante y clase
     * @param studentId ID del estudiante
     * @param studentName Nombre del estudiante
     * @param classId ID de la clase
     * @param className Nombre de la clase
     * @param size Tamaño del QR en píxeles
     * @return Bitmap con el código QR
     */
    public static Bitmap generateQRCode(String studentId, String studentName,
                                        String classId, String className, int size) {
        try {
            // Crear JSON con los datos
            JSONObject qrData = new JSONObject();
            qrData.put("studentId", studentId);
            qrData.put("studentName", studentName);
            qrData.put("classId", classId);
            qrData.put("className", className);
            qrData.put("timestamp", System.currentTimeMillis());

            // Convertir JSON a String
            String qrContent = qrData.toString();

            // Generar QR Code
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size);

            // Convertir BitMatrix a Bitmap
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            return bitmap;

        } catch (WriterException | JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extrae los datos del contenido del QR escaneado
     * @param qrContent Contenido del QR en formato JSON
     * @return JSONObject con los datos
     */
    public static JSONObject parseQRContent(String qrContent) {
        try {
            return new JSONObject(qrContent);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}