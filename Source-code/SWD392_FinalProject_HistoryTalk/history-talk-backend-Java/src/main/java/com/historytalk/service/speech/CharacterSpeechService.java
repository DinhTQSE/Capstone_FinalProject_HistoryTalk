package com.historytalk.service.speech;

import com.historytalk.dto.speech.VoiceOptionResponse;
import com.historytalk.service.chat.AiServiceClient.SpeechStream;

import java.util.List;

public interface CharacterSpeechService {

    /** Reads text aloud in the character's voice and returns a WAV file. */
    byte[] synthesize(String characterId, String text);

    /** Reads text aloud in the character's voice as raw PCM, streamed while it is being generated. */
    SpeechStream openStream(String characterId, String text);

    List<VoiceOptionResponse> listVoices();

    /** Reads text, or a sample sentence when it is empty, with the given preset voice. */
    byte[] preview(String voiceId, String text);
}
