package com.darblee.livingword.domain.model


import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.darblee.livingword.data.BibleVerse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.BreakIterator
import java.util.Locale

private const val NO_ACTIVE_SENTENCE_INDEX = -1

// Utterance IDs for sequence parts
private const val UTTERANCE_ID_SEQUENCE_SCRIPTURE_REFERENCE = "SEQ_SCRIPTURE_REFERENCE"
private const val UTTERANCE_ID_SEQUENCE_SCRIPTURE_SENTENCE_PREFIX = "SEQ_SCRIPTURE_SENTENCE_"
private const val UTTERANCE_ID_SEQUENCE_PAUSE_AFTER_SCRIPTURE = "SEQ_PAUSE_AFTER_SCRIPTURE"
private const val UTTERANCE_ID_SEQUENCE_INTRO_PHRASE = "SEQ_INTRO_PHRASE"
private const val UTTERANCE_ID_SEQUENCE_AI_RESPONSE_SENTENCE_PREFIX = "SEQ_AI_RESPONSE_SENTENCE_"
private const val UTTERANCE_ID_SIMPLE_TEXT_SENTENCE_PREFIX = "SIMPLE_TEXT_SENTENCE_"


// Defines the type of TTS operation currently active or last active.
// Ensure this enum is accessible by VerseDetailScreen (e.g., same package or imported)
enum class TTS_OperationMode {
    NONE,
    SINGLE_TEXT,
    VERSE_DETAIL_SEQUENCE
}

// Defines the current part of the VERSE_DETAIL_SEQUENCE.
// Ensure this enum is accessible by VerseDetailScreen
enum class VerseDetailSequencePart {
    NONE,
    SCRIPTURE_REFERENCE,
    SCRIPTURE,
    PAUSE_AFTER_SCRIPTURE,
    INTRO_PHRASE,
    AI_RESPONSE
}

class TTSViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val locale: Locale = Locale.getDefault()

    // --- General State Flows (for UI) ---
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    // Used to inform Screen which text to highlight as it is being verbalized.
    private val _currentSentenceInBlockIndex = MutableStateFlow(NO_ACTIVE_SENTENCE_INDEX)
    val currentSentenceInBlockIndex: StateFlow<Int> = _currentSentenceInBlockIndex.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    // --- Internal State for Operation Management (as MutableStateFlow) ---
    private val _currentOperationMode = MutableStateFlow(TTS_OperationMode.NONE)
    val currentOperationMode: StateFlow<TTS_OperationMode> = _currentOperationMode.asStateFlow() // Exposed

    private val _sequenceCurrentPart = MutableStateFlow(VerseDetailSequencePart.NONE)
    val sequenceCurrentPart: StateFlow<VerseDetailSequencePart> = _sequenceCurrentPart.asStateFlow() // Exposed

    // For SINGLE_TEXT mode (HomeScreen)
    private var simpleTextSentences: List<String> = emptyList()
    private var simpleTextFullContent: String? = null

    // For VERSE_DETAIL_SEQUENCE mode (VerseDetailScreen)
    private var sequenceScriptureReference: String? = null
    private var sequenceAiResponseText: String? = null
    private var sequenceScriptureSentences: List<String> = emptyList()
    private var sequenceAiResponseSentences: List<String> = emptyList()

    // State for resuming sequence
    private var sequencePausedPart: VerseDetailSequencePart = VerseDetailSequencePart.NONE
    private var sequencePausedSentenceIndexInPart: Int = NO_ACTIVE_SENTENCE_INDEX

    init {
        Log.d("TtsViewModel", "Initializing TextToSpeech...")
        tts = TextToSpeech(application.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("TtsViewModel", "TTS Initialization successful. Engine: ${tts?.defaultEngine}")
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsViewModel", "The Language specified ($locale) is not supported!")
                _isInitialized.value = false
                tts = null
            } else {
                Log.i("TtsViewModel", "Successfully set language $locale.")
                _isInitialized.value = true
                setupUtteranceListener()
            }
        } else {
            Log.e("TtsViewModel", "TTS Initialization failed with status: $status")
            _isInitialized.value = false
            tts = null
        }
    }

    /**
     * Setting up a listener for the Text-to-Speech (TTS) engine to monitor the progress and state of speech utterances.
     *
     * The purpose of this function is to:
     *
     * * React to TTS events: The `UtteranceProgressListener` allows the application to respond to events like when the TTS engine
     * starts speaking a particular text (onStart), finishes speaking (onDone), or encounters an error (onError).
     *
     * * Manage application state:  The listener updates the application's state (using MutableStateFlows) based on these TTS events.
     * This includes tracking whether the TTS is currently speaking, which sentence is being spoken, and what part of a larger sequence
     * is being played.
     *
     * * Handle different speech modes: The code distinguishes between different modes of TTS operation (SINGLE_TEXT and VERSE_DETAIL_SEQUENCE)
     * and updates the state accordingly.
     *
     * **How it Works with Utterance IDs**
     *
     * * The `utteranceId` parameter in the listener callbacks is a crucial piece of information.  It's a unique identifier assigned to each text utterance when it's queued with the `tts?.speak()` method.
     * * The code uses prefixes for `utteranceId` values (e.g., `UTTERANCE_ID_SIMPLE_TEXT_SENTENCE_PREFIX`, `UTTERANCE_ID_SEQUENCE_SCRIPTURE_SENTENCE_PREFIX`) to distinguish between different parts of the speech and different modes of operation.
     * * By examining the `utteranceId` in the listener, the application can determine which sentence or which part of a sequence has started or finished playing.
     */
    private fun setupUtteranceListener() {

        /**
         * An anonymous `UtteranceProgressListener` object is created and set on the TTS engine using
         * `tts?.setOnUtteranceProgressListener(...)`.  This listener provides three callback methods:
         * `onStart()`, `onDone()`, and `onError()`.
         */
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {


            /**
             * Called when the TTS engine begins speaking an utterance.
             * Determines the context of the utterance based on the `utteranceId` (which indicates whether it's part of a single text or a verse detail sequence).
             * Updates the  `_currentOperationMode` and `_sequenceCurrentPart`  StateFlows.
             * Extracts the sentence index from the utterance ID and updates `_currentSentenceInBlockIndex.value`.
             */
            override fun onStart(utteranceId: String?) {
                // Logs the start event with the utterance ID.
                Log.d("TtsViewModel", "[Listener] onStart for utteranceId: $utteranceId (Paused: ${_isPaused.value})")

                if (_isPaused.value && _currentOperationMode.value != TTS_OperationMode.VERSE_DETAIL_SEQUENCE) {
                    // If user paused single text, onStart might still be called for previously queued items.
                    // For sequence, onStart is expected on resume.
                    //
                    // Check if this is a new utterance after a resume command.
                    if (_currentOperationMode.value == TTS_OperationMode.SINGLE_TEXT) return
                }

                // Set speaking true as TTS engine confirms start
                _isSpeaking.value = true

                when {
                    utteranceId?.startsWith(UTTERANCE_ID_SIMPLE_TEXT_SENTENCE_PREFIX) == true -> {
                        _currentOperationMode.value = TTS_OperationMode.SINGLE_TEXT
                        val index = utteranceId.removePrefix(UTTERANCE_ID_SIMPLE_TEXT_SENTENCE_PREFIX).toIntOrNull()
                        if (index != null) _currentSentenceInBlockIndex.value = index
                    }
                    utteranceId == UTTERANCE_ID_SEQUENCE_SCRIPTURE_REFERENCE -> {
                        _currentOperationMode.value = TTS_OperationMode.VERSE_DETAIL_SEQUENCE
                        _sequenceCurrentPart.value = VerseDetailSequencePart.SCRIPTURE_REFERENCE
                        _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX
                    }
                    utteranceId?.startsWith(UTTERANCE_ID_SEQUENCE_SCRIPTURE_SENTENCE_PREFIX) == true -> {
                        _currentOperationMode.value = TTS_OperationMode.VERSE_DETAIL_SEQUENCE
                        _sequenceCurrentPart.value = VerseDetailSequencePart.SCRIPTURE
                        val index = utteranceId.removePrefix(UTTERANCE_ID_SEQUENCE_SCRIPTURE_SENTENCE_PREFIX).toIntOrNull()
                        if (index != null) _currentSentenceInBlockIndex.value = index
                    }
                    utteranceId == UTTERANCE_ID_SEQUENCE_PAUSE_AFTER_SCRIPTURE -> {
                        _currentOperationMode.value = TTS_OperationMode.VERSE_DETAIL_SEQUENCE
                        _sequenceCurrentPart.value = VerseDetailSequencePart.PAUSE_AFTER_SCRIPTURE
                        _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX
                    }
                    utteranceId == UTTERANCE_ID_SEQUENCE_INTRO_PHRASE -> {
                        _currentOperationMode.value = TTS_OperationMode.VERSE_DETAIL_SEQUENCE
                        _sequenceCurrentPart.value = VerseDetailSequencePart.INTRO_PHRASE
                        _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX
                    }
                    utteranceId?.startsWith(UTTERANCE_ID_SEQUENCE_AI_RESPONSE_SENTENCE_PREFIX) == true -> {
                        _currentOperationMode.value = TTS_OperationMode.VERSE_DETAIL_SEQUENCE
                        _sequenceCurrentPart.value = VerseDetailSequencePart.AI_RESPONSE
                        val index = utteranceId.removePrefix(UTTERANCE_ID_SEQUENCE_AI_RESPONSE_SENTENCE_PREFIX).toIntOrNull()
                        if (index != null) _currentSentenceInBlockIndex.value = index
                    }
                }
            }

            /**
             * Called when the TTS engine finishes speaking an utterance.
             * Calls `handlePlaybackCompletion(utteranceId)` to determine the next action based on the current operation mode and the completed utterance.
             */
            override fun onDone(utteranceId: String?) {

                // Logs the completion event with the utterance ID.
                Log.d("TtsViewModel", "[Listener] onDone for utteranceId: $utteranceId (Paused: ${_isPaused.value}, Mode: ${_currentOperationMode.value})")

                // Checks if the TTS is paused. If paused, it returns without doing anything.
                if (_isPaused.value) {
                    Log.d("TtsViewModel", "onDone received while user-paused. No state change from onDone.")
                    return
                }

                // Determine the next action based on the current operation mode and the completed utterance.
                handlePlaybackCompletion(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("TtsViewModel", "[Listener] onError for utteranceId: $utteranceId")
                stopAllSpeaking() // Consider a more nuanced error handling if needed
            }
        })
    }

    /**
     * Determines what happens after the Text-to-Speech (TTS) engine finishes speaking a specific utterance.
     */
    private fun handlePlaybackCompletion(utteranceId: String?) {
        // The 'when' statement correctly compares the .value of the StateFlow
        when (_currentOperationMode.value) {
            TTS_OperationMode.SINGLE_TEXT -> { // User's selected line context: This case compares the ENUM value from _currentOperationMode.value
                val lastSentenceIndex = simpleTextSentences.size - 1
                val doneSentenceIndex = utteranceId?.removePrefix(UTTERANCE_ID_SIMPLE_TEXT_SENTENCE_PREFIX)?.toIntOrNull()
                if (doneSentenceIndex == lastSentenceIndex || simpleTextSentences.isEmpty()) {
                    Log.i("TtsViewModel", "Finished speaking single text.")
                    resetPlaybackStates() // Resets speaking, paused, index
                    _currentOperationMode.value = TTS_OperationMode.NONE
                }
            }
            TTS_OperationMode.VERSE_DETAIL_SEQUENCE -> {
                when (_sequenceCurrentPart.value) {
                    VerseDetailSequencePart.SCRIPTURE_REFERENCE -> {
                        if (utteranceId == UTTERANCE_ID_SEQUENCE_SCRIPTURE_REFERENCE) {
                            Log.d("TtsViewModel", "Finished scripture reference. Queueing scripture.")
                            queueSequenceScripture() // Start scripture from beginning
                        }
                    }
                    VerseDetailSequencePart.SCRIPTURE -> {
                        val lastScriptureSentenceIndex = sequenceScriptureSentences.size - 1
                        val doneScriptureSentenceIndex = utteranceId?.removePrefix(UTTERANCE_ID_SEQUENCE_SCRIPTURE_SENTENCE_PREFIX)?.toIntOrNull()
                        if (doneScriptureSentenceIndex == lastScriptureSentenceIndex || sequenceScriptureSentences.isEmpty()) {
                            Log.d("TtsViewModel", "Finished scripture part. Queueing pause.")
                            queueSequencePause() // This will set sequenceCurrentPart to PAUSE
                        }
                    }
                    VerseDetailSequencePart.PAUSE_AFTER_SCRIPTURE -> {
                        if (utteranceId == UTTERANCE_ID_SEQUENCE_PAUSE_AFTER_SCRIPTURE) {
                            Log.d("TtsViewModel", "Finished pause. Queueing intro phrase.")
                            queueSequenceIntroPhrase() // Sets part to INTRO
                        }
                    }
                    VerseDetailSequencePart.INTRO_PHRASE -> {
                        if (utteranceId == UTTERANCE_ID_SEQUENCE_INTRO_PHRASE) {
                            Log.d("TtsViewModel", "Finished intro phrase. Queueing AI response.")
                            queueSequenceAiResponse() // Start AI response from beginning
                        }
                    }
                    VerseDetailSequencePart.AI_RESPONSE -> {
                        val lastAiSentenceIndex = sequenceAiResponseSentences.size - 1
                        val doneAiSentenceIndex = utteranceId?.removePrefix(UTTERANCE_ID_SEQUENCE_AI_RESPONSE_SENTENCE_PREFIX)?.toIntOrNull()
                        if (doneAiSentenceIndex == lastAiSentenceIndex || sequenceAiResponseSentences.isEmpty()) {
                            Log.i("TtsViewModel", "Finished verse detail sequence.")
                            resetPlaybackStates()
                            _currentOperationMode.value = TTS_OperationMode.NONE
                            _sequenceCurrentPart.value = VerseDetailSequencePart.NONE
                        }
                    }
                    VerseDetailSequencePart.NONE -> { /* Should ideally not be in this state if sequence was active */ }
                }
            }
            TTS_OperationMode.NONE -> { /* No operation was active */ }
        }

        // Fallback check if TTS stops unexpectedly
        if (tts?.isSpeaking == false && !_isPaused.value && _currentOperationMode.value != TTS_OperationMode.NONE) {
            val activeQueue = tts?.speak("", TextToSpeech.QUEUE_ADD, null, "QUEUE_CHECK") == TextToSpeech.SUCCESS
            // A bit of a hack to check if queue is empty. Stop may clear queue.
            // A more robust check might involve looking at utterance progress if API supports it.
            // For now, if it's not speaking, not paused, and mode is not NONE, assume it finished.
            // This primarily handles the case where the queue finishes naturally.
            // The onDone for the *last* item in a sequence part or whole sequence should drive the reset.
            if (!activeQueue) { // Heuristic: if adding to queue fails or if we assume queue became empty
                Log.d("TtsViewModel", "TTS queue seems empty and not paused, operation mode was ${_currentOperationMode.value}. Considering reset.")
                // This might be too aggressive if onDone is slightly delayed.
                // Let primary onDone logic handle resets.
            }
        }
    }

    // --- Public Methods for HomeScreen ---
    fun togglePlayPauseResumeSingleText(text: String) {
        if (!_isInitialized.value || tts == null) {
            Log.w("TtsViewModel", "togglePlayPauseResumeSingleText: TTS not ready.")
            return
        }

        if (_currentOperationMode.value == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (_isSpeaking.value || _isPaused.value)) {
            Log.d("TtsViewModel", "Single text requested, stopping active verse detail sequence.")
            stopAllSpeaking()
        }

        if (_currentOperationMode.value == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (_isSpeaking.value || _isPaused.value)) {
            Log.d("TtsViewModel", "Single text requested, stopping active verse detail sequence.")
            stopAllSpeaking() // This will reset modes and clear sequence data
        }

        if (_isSpeaking.value && !_isPaused.value && _currentOperationMode.value == TTS_OperationMode.SINGLE_TEXT && simpleTextFullContent == text) {
            Log.d("TtsViewModel", "Pausing single text.")
            tts?.stop() // Stops current speech and clears queue for subsequent utterances in this call.
            _isPaused.value = true
            _isSpeaking.value = false // Manually set as tts.stop() might not trigger onDone immediately
        } else if (_isPaused.value && _currentOperationMode.value == TTS_OperationMode.SINGLE_TEXT && simpleTextFullContent == text) {
            Log.d("TtsViewModel", "Resuming single text from sentence: ${_currentSentenceInBlockIndex.value}.")
            _isPaused.value = false
            // _isSpeaking will be set by onStart
            simpleTextFullContent?.let {
                // Resume from the sentence after the paused one, or current if it wasn't completed.
                // currentSentenceInBlockIndex should be the one that was active or about to be.
                queueSentencesForSingleText(it, _currentSentenceInBlockIndex.value.coerceAtLeast(0))
            }
        } else {
            Log.d("TtsViewModel", "Starting new single text playback for: ${text.take(30)}...")
            stopAllSpeaking() // Clear previous state and operation
            _currentOperationMode.value = TTS_OperationMode.SINGLE_TEXT
            _sequenceCurrentPart.value = VerseDetailSequencePart.NONE // Not a sequence
            simpleTextFullContent = text
            _isPaused.value = false
            queueSentencesForSingleText(text, 0)
        }
    }

    fun restartSingleText(text: String) {
        if (!_isInitialized.value || tts == null) {
            Log.w("TtsViewModel", "restartSingleText: TTS not ready.")
            return
        }
        Log.d("TtsViewModel", "Restarting single text playback for: ${text.take(30)}...")
        stopAllSpeaking()
        _currentOperationMode.value = TTS_OperationMode.SINGLE_TEXT
        _sequenceCurrentPart.value = VerseDetailSequencePart.NONE
        simpleTextFullContent = text
        _isPaused.value = false
        queueSentencesForSingleText(text, 0)
    }

    private fun queueSentencesForSingleText(text: String, startIndex: Int) {
        val cleanedText = text.replace("*", "")

        simpleTextSentences = splitIntoSentences(cleanedText, locale)
        if (simpleTextSentences.isEmpty()) {
            Log.w("TtsViewModel", "No sentences in single text.")
            resetPlaybackStates()
            _currentOperationMode.value = TTS_OperationMode.NONE
            return
        }
        val actualStartIndex = startIndex.coerceIn(0, simpleTextSentences.size -1)
        _currentSentenceInBlockIndex.value = if (simpleTextSentences.isNotEmpty()) actualStartIndex else NO_ACTIVE_SENTENCE_INDEX

        var queuedSomething = false
        if (actualStartIndex < simpleTextSentences.size) {
            for (i in actualStartIndex until simpleTextSentences.size) {
                val utteranceId = "$UTTERANCE_ID_SIMPLE_TEXT_SENTENCE_PREFIX$i"
                tts?.speak(simpleTextSentences[i], TextToSpeech.QUEUE_ADD, null, utteranceId)
                queuedSomething = true
            }
        }

        if (!queuedSomething && simpleTextSentences.isNotEmpty()) {
            Log.d("TtsViewModel", "Single text: Attempted to queue but nothing was left or index issue.")
            resetPlaybackStates()
            _currentOperationMode.value = TTS_OperationMode.NONE
        } else if (simpleTextSentences.isEmpty()) {
            resetPlaybackStates()
            _currentOperationMode.value = TTS_OperationMode.NONE
        }
    }

    // --- Public Methods for VerseDetailScreen (Sequence Playback) ---
    fun startVerseDetailSequence(
        aiResponse: String,
        verseItem: BibleVerse
    ) {
        if (!_isInitialized.value || tts == null) {
            Log.w("TtsViewModel", "startVerseDetailSequence: TTS not ready.")
            return
        }
        Log.d("TtsViewModel", "Starting verse detail sequence using scriptureJson.")
        stopAllSpeaking()

        _currentOperationMode.value = TTS_OperationMode.VERSE_DETAIL_SEQUENCE

        sequenceScriptureReference = if (verseItem.startVerse == verseItem.endVerse)
            "${verseItem.book} chapter ${verseItem.chapter} verse ${verseItem.startVerse}"
        else
            "${verseItem.book} chapter ${verseItem.chapter} verse ${verseItem.startVerse} to ${verseItem.endVerse}"


        // Populate sentences directly from scriptureJson
        sequenceScriptureSentences = verseItem.scriptureVerses.map { it.verseString }
        sequenceAiResponseText = aiResponse // Store raw text

        // Split AI response sentences as before
        sequenceAiResponseSentences = splitIntoSentences(cleanupTextForTts(sequenceAiResponseText.toString()), locale)

        _isPaused.value = false // Ensure not paused
        sequencePausedPart = VerseDetailSequencePart.NONE // Reset pause state
        sequencePausedSentenceIndexInPart = NO_ACTIVE_SENTENCE_INDEX

        queueSequenceScriptureRef() // Start by queueing scripture reference
    }

    fun togglePlayPauseResumeVerseDetailSequence() {
        if (!_isInitialized.value || tts == null) {
            Log.w("TtsViewModel", "togglePlayPauseResumeVerseDetailSequence: TTS not ready.")
            return
        }

        if (_currentOperationMode.value != TTS_OperationMode.VERSE_DETAIL_SEQUENCE) {
            Log.w("TtsViewModel", "togglePlayPauseResumeVerseDetailSequence called but not in sequence mode. Start sequence first.")
            // Or, if you have verseItem data stored, you could call startVerseDetailSequence here.
            // For now, assume UI calls startVerseDetailSequence if mode is not VERSE_DETAIL_SEQUENCE.
            return
        }

        if (_isSpeaking.value && !_isPaused.value) { // Currently playing sequence -> Pause
            Log.d("TtsViewModel", "Pausing verse detail sequence at part: ${_sequenceCurrentPart.value}, sentence: ${_currentSentenceInBlockIndex.value}")
            sequencePausedPart = _sequenceCurrentPart.value
            sequencePausedSentenceIndexInPart = _currentSentenceInBlockIndex.value
            tts?.stop()
            _isPaused.value = true
            _isSpeaking.value = false
        } else if (_isPaused.value) { // Currently paused sequence -> Resume
            Log.d("TtsViewModel", "Resuming verse detail sequence from part: $sequencePausedPart, sentence: $sequencePausedSentenceIndexInPart")
            _isPaused.value = false
            // _isSpeaking will be set by onStart listener.
            resumeSequenceFromPausedState()
        } else {
            // Not speaking and not paused, but in VERSE_DETAIL_SEQUENCE mode.
            // This could mean sequence finished or was stopped by error.
            // UI should ideally call startVerseDetailSequence to restart.
            Log.w("TtsViewModel", "togglePlayPauseResumeVerseDetailSequence called when sequence not speaking/paused. Restart needed via startVerseDetailSequence.")
            // To be safe, let's try to re-trigger the start if essential data is still around.
            // This assumes sequenceAiResponseText etc. are still valid from the last start.
            if (sequenceAiResponseText != null && sequenceScriptureReference != null) {
                Log.d("TtsViewModel", "Attempting to restart sequence from beginning as it was stopped/finished.")
                // Reset pause state before restarting the queue from reference
                sequencePausedPart = VerseDetailSequencePart.NONE
                sequencePausedSentenceIndexInPart = NO_ACTIVE_SENTENCE_INDEX
                _sequenceCurrentPart.value = VerseDetailSequencePart.NONE // Reset current part as well
                queueSequenceScriptureRef()
            } else {
                Log.e("TtsViewModel", "Cannot restart sequence, original data missing.")
            }
        }
    }

    private fun resumeSequenceFromPausedState() {
        _currentOperationMode.value = TTS_OperationMode.VERSE_DETAIL_SEQUENCE // Ensure mode
        _sequenceCurrentPart.value = sequencePausedPart // Restore current part for logical flow

        val resumeIndex = sequencePausedSentenceIndexInPart.takeIf { it != NO_ACTIVE_SENTENCE_INDEX } ?: 0

        Log.d("TtsViewModel", "Resuming from Part: $sequencePausedPart, Effective Sentence Index: $resumeIndex")

        // Set currentSentenceInBlockIndex for immediate UI update if applicable
        if (sequencePausedPart == VerseDetailSequencePart.SCRIPTURE || sequencePausedPart == VerseDetailSequencePart.AI_RESPONSE) {
            _currentSentenceInBlockIndex.value = resumeIndex
        } else {
            _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX
        }

        when (sequencePausedPart) {
            VerseDetailSequencePart.SCRIPTURE_REFERENCE -> queueSequenceScriptureRef()
            VerseDetailSequencePart.SCRIPTURE -> queueSequenceScripture(resumeIndex)
            VerseDetailSequencePart.PAUSE_AFTER_SCRIPTURE -> queueSequencePause()
            VerseDetailSequencePart.INTRO_PHRASE -> queueSequenceIntroPhrase()
            VerseDetailSequencePart.AI_RESPONSE -> queueSequenceAiResponse(resumeIndex)
            VerseDetailSequencePart.NONE -> {
                Log.e("TtsViewModel", "Cannot resume from NONE state. Attempting to start from beginning.")
                queueSequenceScriptureRef() // Fallback to start from very beginning
            }
        }
    }

    private fun queueSequenceScriptureRef() {
        _sequenceCurrentPart.value = VerseDetailSequencePart.SCRIPTURE_REFERENCE
        if (sequenceScriptureReference.isNullOrEmpty()) {
            Log.w("TtsViewModel", "Scripture reference is empty. Proceeding to scripture.")
            queueSequenceScripture()
        } else {
            _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX // No sentences in ref
            tts?.speak(sequenceScriptureReference, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID_SEQUENCE_SCRIPTURE_REFERENCE)
        }
    }

    private fun queueSequenceScripture(startIndex: Int = 0) {
        _sequenceCurrentPart.value = VerseDetailSequencePart.SCRIPTURE
        // sequenceScriptureSentences should already be populated by startVerseDetailSequence

        if (sequenceScriptureSentences.isEmpty()) {
            Log.w("TtsViewModel", "Scripture part is empty for sequence. Proceeding to pause.")
            queueSequencePause()
            return
        }

        // If startIndex is at or beyond the list size, this part is considered done.
        if (startIndex >= sequenceScriptureSentences.size) {
            Log.d("TtsViewModel", "Scripture: startIndex $startIndex is at or after end of sentences (${sequenceScriptureSentences.size}). Moving to pause.")
            queueSequencePause() // Proceed to the next part of the sequence
            return
        }

        // _currentSentenceInBlockIndex is set in resumeSequenceFromPausedState or by onStart
        // For a fresh queue (startIndex = 0), onStart will handle it.
        // If resuming, it's set before this call.

        var queuedSomething = false
        for (i in startIndex until sequenceScriptureSentences.size) {
            tts?.speak(sequenceScriptureSentences[i], TextToSpeech.QUEUE_ADD, null, "$UTTERANCE_ID_SEQUENCE_SCRIPTURE_SENTENCE_PREFIX$i")
            queuedSomething = true
        }

        if (!queuedSomething) { // Should only happen if loop doesn't run, e.g. startIndex was already at end.
            Log.d("TtsViewModel", "queueSequenceScripture: No sentences queued from startIndex $startIndex. This should have been caught by the size check. Moving to pause.")
            queueSequencePause()
        }
    }

    private fun queueSequencePause() {
        _sequenceCurrentPart.value = VerseDetailSequencePart.PAUSE_AFTER_SCRIPTURE
        _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX
        tts?.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, UTTERANCE_ID_SEQUENCE_PAUSE_AFTER_SCRIPTURE)
    }

    private fun queueSequenceIntroPhrase() {
        _sequenceCurrentPart.value = VerseDetailSequencePart.INTRO_PHRASE
        _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX
        val intro = "Here is the key takeaway from this verse"
        tts?.speak(intro, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID_SEQUENCE_INTRO_PHRASE)
    }

    private fun queueSequenceAiResponse(startIndex: Int = 0) {
        _sequenceCurrentPart.value = VerseDetailSequencePart.AI_RESPONSE
        // sequenceAiResponseSentences should already be populated and cleaned by startVerseDetailSequence

        if (sequenceAiResponseSentences.isEmpty()) {
            Log.w("TtsViewModel", "AI Response part is empty. Sequence ends after intro phrase.")
            handlePlaybackCompletion(UTTERANCE_ID_SEQUENCE_INTRO_PHRASE) // Simulate intro done to trigger sequence end
            return
        }

        if (startIndex >= sequenceAiResponseSentences.size) {
            Log.i("TtsViewModel", "AI Response: startIndex $startIndex is at or after end of sentences (${sequenceAiResponseSentences.size}). Sequence part considered complete.")
            // Simulate completion of the last AI response sentence to allow handlePlaybackCompletion to end the sequence.
            handlePlaybackCompletion("$UTTERANCE_ID_SEQUENCE_AI_RESPONSE_SENTENCE_PREFIX${sequenceAiResponseSentences.size - 1}")
            return
        }

        // _currentSentenceInBlockIndex is set in resumeSequenceFromPausedState or by onStart

        var queuedSomething = false
        for (i in startIndex until sequenceAiResponseSentences.size) {
            tts?.speak(sequenceAiResponseSentences[i], TextToSpeech.QUEUE_ADD, null, "$UTTERANCE_ID_SEQUENCE_AI_RESPONSE_SENTENCE_PREFIX$i")
            queuedSomething = true
        }
        if (!queuedSomething) {
            Log.i("TtsViewModel", "queueSequenceAiResponse: No sentences queued from $startIndex. This implies completion.")
            handlePlaybackCompletion("$UTTERANCE_ID_SEQUENCE_AI_RESPONSE_SENTENCE_PREFIX${sequenceAiResponseSentences.size - 1}")
        }
    }

    // --- General Control and Cleanup ---
    fun stopAllSpeaking() {
        Log.d("TtsViewModel", "stopAllSpeaking called.")
        tts?.stop()
        resetPlaybackStates()

        simpleTextSentences = emptyList()
        simpleTextFullContent = null

        // Clear sequence specific data
        sequenceScriptureReference = null
        sequenceAiResponseText = null
        sequenceScriptureSentences = emptyList()
        sequenceAiResponseSentences = emptyList()

        _sequenceCurrentPart.value = VerseDetailSequencePart.NONE
        _currentOperationMode.value = TTS_OperationMode.NONE

        // Reset sequence pause state
        sequencePausedPart = VerseDetailSequencePart.NONE
        sequencePausedSentenceIndexInPart = NO_ACTIVE_SENTENCE_INDEX
    }

    private fun resetPlaybackStates() {
        _isSpeaking.value = false
        _isPaused.value = false
        _currentSentenceInBlockIndex.value = NO_ACTIVE_SENTENCE_INDEX
        Log.d("TtsViewModel", "Common playback states reset.")
    }

    private fun cleanupTextForTts(rawText: String): String {
        var cleanedText = rawText
        cleanedText = cleanedText.replace(Regex("""\*\*(.*?)\*\*"""), "$1")
        cleanedText = cleanedText.replace(Regex("""\*(.*?)\*"""), "$1")
        cleanedText = cleanedText.replace(Regex("""^\* """, RegexOption.MULTILINE), "")
        return cleanedText.trim()
    }

    private fun splitIntoSentences(text: String, locale: Locale): List<String> {
        if (text.isBlank()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val sentenceList = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) {
                sentenceList.add(sentence)
            }
            start = end
            end = iterator.next()
        }
        return sentenceList
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("TtsViewModel", "ViewModel cleared. Shutting down TTS.")
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
        stopAllSpeaking() // Ensure all states are fully reset
        Log.d("TtsViewModel", "TTS shutdown complete.")
    }
}