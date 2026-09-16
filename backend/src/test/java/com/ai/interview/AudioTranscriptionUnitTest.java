package com.ai.interview;

import com.ai.interview.service.AiService;
import com.ai.interview.service.TestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AudioTranscriptionUnitTest {

    @Mock
    private AiService aiService;

    @InjectMocks
    private TestService testService;

    @Test
    void whenFileIsNull_transcribeReturnsEmpty() {
        String result = testService.transcribeAudio(null);
        assertEquals("", result);
        verifyNoInteractions(aiService);
    }

    @Test
    void whenFileIsEmpty_transcribeReturnsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.webm", "audio/webm", new byte[0]);
        String result = testService.transcribeAudio(emptyFile);
        assertEquals("", result);
        verifyNoInteractions(aiService);
    }

    @Test
    void whenValidAudioProvided_transcribeReturnsAiTranscript() {
        byte[] audioData = "dummy-audio-bytes".getBytes();
        MockMultipartFile audioFile = new MockMultipartFile("file", "recording.webm", "audio/webm", audioData);

        when(aiService.transcribeAudio(eq(audioData), eq("audio/webm"))).thenReturn("We used Redis cluster for session synchronization");

        String result = testService.transcribeAudio(audioFile);
        assertEquals("We used Redis cluster for session synchronization", result);
        verify(aiService, times(1)).transcribeAudio(eq(audioData), eq("audio/webm"));
    }
}