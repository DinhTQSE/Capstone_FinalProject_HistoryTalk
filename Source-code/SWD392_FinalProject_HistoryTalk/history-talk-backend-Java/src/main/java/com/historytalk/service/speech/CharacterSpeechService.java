package com.historytalk.service.speech;

public interface CharacterSpeechService {

    /** Reads text aloud in the character's voice and returns a WAV file. */
    byte[] synthesize(String characterId, String text);
}
