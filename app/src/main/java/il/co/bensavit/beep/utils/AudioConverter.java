package il.co.bensavit.beep.utils;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

public class AudioConverter {
    private static final String TAG = "AudioConverter";
    private static final int TARGET_SAMPLE_RATE = 16000;

    public static File convertToWav(File inputFile, File outputFile) throws IOException {
        Logger.d(TAG, "Converting " + inputFile.getName() + " to WAV...");
        
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        List<short[]> pcmChunks = new ArrayList<>();
        int totalShorts = 0;

        try {
            extractor.setDataSource(inputFile.getAbsolutePath());

            int trackIndex = findAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IOException("No audio track found in file");
            }

            extractor.selectTrack(trackIndex);
            MediaFormat inputFormat = extractor.getTrackFormat(trackIndex);
            if (!inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) || !inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                throw new IOException("Invalid audio format: missing sample rate or channel count in audio track");
            }
            int inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            Logger.d(TAG, "Source: " + inputSampleRate + "Hz, " + inputChannels + " channels");

            String mimeType = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType == null) {
                throw new IOException("Mime type not found in audio track");
            }
            try {
                decoder = MediaCodec.createDecoderByType(mimeType);
                decoder.configure(inputFormat, null, null, 0);
                decoder.start();
            } catch (Exception e) {
                throw new IOException("MediaCodec decoder error: Failed to initialize decoder for " + mimeType + ". Codec resources may be busy or unavailable.", e);
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isExtractorDone = false;
            boolean isDecoderDone = false;

            do {
                if (!isExtractorDone) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            int chunkSize = extractor.readSampleData(inputBuffer, 0);
                            if (chunkSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                isExtractorDone = true;
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputBufferIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        ShortBuffer shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                        
                        short[] chunk = new short[shortBuffer.remaining()];
                        shortBuffer.get(chunk);
                        
                        // Process chunk: Mono and Resample
                        short[] processedChunk = processAudio(chunk, inputSampleRate, inputChannels);
                        pcmChunks.add(processedChunk);
                        totalShorts += processedChunk.length;
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderDone = true;
                    }
                }
            } while (!isDecoderDone);

        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {}
                try {
                    decoder.release();
                } catch (Exception ignored) {}
            }
            try {
                extractor.release();
            } catch (Exception ignored) {}
        }

        // Combine chunks
        short[] allPcm = new short[totalShorts];
        int offset = 0;
        for (short[] chunk : pcmChunks) {
            System.arraycopy(chunk, 0, allPcm, offset, chunk.length);
            offset += chunk.length;
        }

        WaveEncoder.encodeWaveFile(outputFile, allPcm);
        Logger.d(TAG, "Conversion complete: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    private static int findAudioTrack(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        if (trackCount <= 0) return -1;
        int i = 0;
        do {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
            i++;
        } while (i < trackCount);
        return -1;
    }

    private static short[] processAudio(short[] input, int sampleRate, int channels) {
        // 1. Downmix to mono if needed
        short[] mono;
        if (channels > 1) {
            mono = new short[input.length / channels];
            int i = 0;
            while (i < mono.length) {
                int sum = 0;
                int c = 0;
                while (c < channels) {
                    sum += input[i * channels + c];
                    c++;
                }
                mono[i] = (short) (sum / channels);
                i++;
            }
        } else {
            mono = input;
        }

        // 2. Resample to 16kHz if needed (Linear interpolation)
        if (sampleRate == TARGET_SAMPLE_RATE) {
            return mono;
        }

        double ratio = (double) sampleRate / TARGET_SAMPLE_RATE;
        int outputLength = (int) (mono.length / ratio);
        short[] output = new short[outputLength];

        for (int j = 0; j < outputLength; j++) {
            double sourceIndex = j * ratio;
            int index1 = (int) sourceIndex;
            int index2 = Math.min(index1 + 1, mono.length - 1);
            double weight = sourceIndex - index1;
            output[j] = (short) ((1 - weight) * mono[index1] + weight * mono[index2]);
        }

        return output;
    }
}
