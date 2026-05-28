package com.voicecommand.app.audio;

import com.k2fsa.sherpa.onnx.*;

class SherpaHelper {

    private OfflineRecognizer recognizer;

    SherpaHelper(
            String preprocess,
            String encoder,
            String uncachedDecoder,
            String cachedDecoder,
            String tokens,
            int numThreads
    ) {
        this(encoder, tokens, numThreads, preprocess, uncachedDecoder, cachedDecoder, "", false);
    }

    SherpaHelper(
            String encoder,
            String decoder,
            String tokens,
            int numThreads
    ) {
        this(encoder, tokens, numThreads, "", "", "", decoder, true);
    }

    private SherpaHelper(
            String encoder,
            String tokens,
            int numThreads,
            String preprocess,
            String uncachedDecoder,
            String cachedDecoder,
            String decoder,
            boolean isWhisper
    ) {
        QnnConfig emptyQnn = new QnnConfig("", "", "");

        OfflineMoonshineModelConfig moonshine = new OfflineMoonshineModelConfig(
                preprocess, encoder, uncachedDecoder, cachedDecoder,
                isWhisper ? "" : decoder
        );

        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig(
                isWhisper ? encoder : "",
                isWhisper ? decoder : "",
                "", "transcribe", 0, false, false
        );

        OfflineFunAsrNanoModelConfig funAsrNano = new OfflineFunAsrNanoModelConfig(
                "", "", "", "", "", "", 0, 0f, 0f, 0, "", false, ""
        );

        OfflineQwen3AsrModelConfig qwen3Asr = new OfflineQwen3AsrModelConfig(
                "", "", "", "", 0, 0, 0f, 0f, 0, ""
        );

        OfflineModelConfig modelConfig = new OfflineModelConfig(
                new OfflineTransducerModelConfig("", "", ""),
                new OfflineParaformerModelConfig("", emptyQnn),
                whisper,
                new OfflineFireRedAsrModelConfig("", ""),
                moonshine,
                new OfflineNemoEncDecCtcModelConfig(""),
                new OfflineSenseVoiceModelConfig("", "", false, emptyQnn),
                new OfflineDolphinModelConfig(""),
                new OfflineZipformerCtcModelConfig("", emptyQnn),
                new OfflineWenetCtcModelConfig(""),
                new OfflineOmnilingualAsrCtcModelConfig(""),
                new OfflineMedAsrCtcModelConfig(""),
                funAsrNano,
                qwen3Asr,
                new OfflineFireRedAsrCtcModelConfig(""),
                new OfflineCanaryModelConfig("", "", "", "", false),
                new OfflineCohereTranscribeModelConfig("", "", "", false, false),
                "", numThreads, false, "cpu", isWhisper ? "whisper" : "moonshine",
                tokens, "", ""
        );

        OfflineRecognizerConfig recognizerConfig = new OfflineRecognizerConfig(
                new FeatureConfig(16000, 80, -1.0f),
                modelConfig,
                new HomophoneReplacerConfig("", "", ""),
                "greedy_search", 0, "", 0f,
                "", "", 0f
        );

        recognizer = new OfflineRecognizer(null, recognizerConfig);
    }

    String checkWakeWord(float[] audioSamples) {
        OfflineStream stream = recognizer.createStream();
        stream.acceptWaveform(audioSamples, 16000);
        recognizer.decode(stream);
        OfflineRecognizerResult result = recognizer.getResult(stream);
        return result.getText();
    }

    void release() {
        recognizer = null;
    }
}
