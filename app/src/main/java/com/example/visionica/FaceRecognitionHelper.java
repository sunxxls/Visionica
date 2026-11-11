package com.example.visionica;

import java.util.List;

public class FaceRecognitionHelper {

    // Umbral de similitud coseno. Para FaceNet/MobileFaceNet lo normal es 0.60 - 0.75.
    // Ajústalo según lo que veas en Logcat (filtro "FaceAttendance", línea "Mejor similitud").
    private static final float SIMILARITY_THRESHOLD = 0.62f;

    // Diferencia mínima entre el mejor y el segundo mejor estudiante.
    // Evita confundir a dos personas parecidas.

    private static final float MIN_MARGIN = 0.12f;

    public static float calculateCosineSimilarity(List<Float> embedding1, List<Float> embedding2) {
        if (embedding1 == null || embedding2 == null ||
                embedding1.size() != embedding2.size() || embedding1.isEmpty()) {
            return 0f;
        }

        float dotProduct = 0f;
        float norm1 = 0f;
        float norm2 = 0f;

        for (int i = 0; i < embedding1.size(); i++) {
            float a = embedding1.get(i);
            float b = embedding2.get(i);
            dotProduct += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0f;
        }

        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    public static boolean isSamePerson(List<Float> embedding1, List<Float> embedding2) {
        return calculateCosineSimilarity(embedding1, embedding2) >= SIMILARITY_THRESHOLD;
    }

    public static int findBestMatch(List<Float> targetEmbedding, List<List<Float>> embeddingsList) {
        float maxSimilarity = 0f;
        int bestMatchIndex = -1;

        for (int i = 0; i < embeddingsList.size(); i++) {
            float similarity = calculateCosineSimilarity(targetEmbedding, embeddingsList.get(i));
            if (similarity > maxSimilarity && similarity >= SIMILARITY_THRESHOLD) {
                maxSimilarity = similarity;
                bestMatchIndex = i;
            }
        }

        return bestMatchIndex;
    }

    public static float getSimilarityThreshold() {
        return SIMILARITY_THRESHOLD;
    }

    public static float getMinMargin() {
        return MIN_MARGIN;
    }
}