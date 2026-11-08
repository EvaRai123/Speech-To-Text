package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LessonCatalog {
    public static final class LessonArea {
        public final String key;
        public final String title;
        public final String description;
        public final List<LessonDefinition> lessons;

        LessonArea(String key, String title, String description, List<LessonDefinition> lessons) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.lessons = Collections.unmodifiableList(new ArrayList<>(lessons));
        }
    }

    private static final Map<String, LessonArea> AREAS = buildAreas();

    private LessonCatalog() {
    }

    public static List<LessonArea> getAreas() {
        return new ArrayList<>(AREAS.values());
    }

    public static LessonArea getArea(String areaKey) {
        return AREAS.get(areaKey);
    }

    public static LessonDefinition getLesson(String lessonId) {
        if (lessonId == null) return null;
        for (LessonArea area : AREAS.values()) {
            for (LessonDefinition lesson : area.lessons) {
                if (lesson.id.equals(lessonId)) return lesson;
            }
        }
        return null;
    }

    public static int getLessonCount() {
        int count = 0;
        for (LessonArea area : AREAS.values()) count += area.lessons.size();
        return count;
    }

    private static Map<String, LessonArea> buildAreas() {
        Map<String, LessonArea> areas = new LinkedHashMap<>();
        areas.put("breath", area("breath", "Breathing",
                "Notice comfortable breathing and explore how a gentle breath can support a phrase.",
                lesson("breath_1", "breath", "Breathing", "Notice your breathing",
                        "Notice your usual breathing without trying to change it.",
                        "Sit comfortably. There is no right or wrong pattern to find.",
                        "breath awareness", LessonDefinition.ACTIVITY_OBSERVE, false, false, 12, 4,
                        "Notice one easy breath.", "Notice the next easy breath.", "Notice one more easy breath."),
                lesson("breath_2", "breath", "Breathing", "Comfortable breathing",
                        "Try a few gentle breaths without taking unusually deep breaths.",
                        "Follow the visual cue. Let each breath stay easy; do not hold it.",
                        "gentle breathing", LessonDefinition.ACTIVITY_BREATHING, false, false, 4, 5,
                        "Breathe in gently.", "Breathe out and relax.", "Breathe in gently.", "Breathe out and relax.",
                        "Breathe in gently.", "Breathe out and relax."),
                lesson("breath_3", "breath", "Breathing", "Breathe, then speak",
                        "Explore making a comfortable vowel after an easy breath.",
                        "Take a comfortable breath, then say Aaa. The microphone cannot check your breathing technique.",
                        "breath and voice", LessonDefinition.ACTIVITY_VOICE, true, false, 0, 5,
                        "Take an easy breath, then say Aaa.", "Rest, then try Aaa once more."),
                lesson("breath_4", "breath", "Breathing", "Short phrases",
                        "Try short phrases with comfortable pauses between them.",
                        "Say each phrase in your own way. Pause or breathe whenever you like.",
                        "short phrases", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "Good morning.", "How are you?", "Thank you."),
                lesson("breath_5", "breath", "Breathing", "Longer phrases",
                        "Try a few longer phrases and pause whenever you need to.",
                        "You do not need to finish a phrase in one breath. Choose an easy pace.",
                        "comfortable phrasing", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 8,
                        "I would like some water.", "It is nice to see you today.",
                        "Please give me a moment to think.")));

        areas.put("phonation", area("phonation", "Voice",
                "Explore starting, holding and repeating a comfortable voice sound.",
                lesson("phonation_1", "phonation", "Voice", "Start your voice",
                        "Try starting a comfortable Aaa sound.",
                        "Make the sound gently. Stop if your voice feels tired or strained.",
                        "voice start", LessonDefinition.ACTIVITY_VOICE, true, false, 0, 5,
                        "Aaa", "Aaa", "Aaa"),
                lesson("phonation_2", "phonation", "Voice", "Hold a sound",
                        "Explore holding a comfortable vowel for a short time.",
                        "Choose a comfortable length for your sound. Never hold your breath or push your voice.",
                        "held voice", LessonDefinition.ACTIVITY_VOICE, true, false, 0, 8,
                        "Hold a comfortable Aaa, then stop when ready.", "Rest, then try Aaa again."),
                lesson("phonation_3", "phonation", "Voice", "Repeat a sound",
                        "Practise starting a comfortable Aaa sound several times.",
                        "Try one sound for each turn. Rest between sounds if you want.",
                        "voice repetitions", LessonDefinition.ACTIVITY_VOICE, true, false, 0, 5,
                        "Aaa", "Aaa", "Aaa", "Aaa"),
                lesson("phonation_4", "phonation", "Voice", "Start and stop your voice",
                        "Practise beginning and ending a sound comfortably.",
                        "Start Aaa when ready, then stop naturally. Do not squeeze or force the sound.",
                        "starting and stopping", LessonDefinition.ACTIVITY_VOICE, true, false, 0, 6,
                        "Start Aaa, then stop comfortably.", "Rest, then start and stop Aaa again.",
                        "Try one final comfortable start and stop."),
                lesson("phonation_5", "phonation", "Voice", "Keep your voice steady",
                        "Notice how a held voice sound may change while you speak.",
                        "Small changes are normal. The app can show an estimate, not judge your voice.",
                        "steady voice", LessonDefinition.ACTIVITY_VOICE, true, false, 0, 8,
                        "Hold a comfortable Aaa.", "Rest, then try another comfortable Aaa.")));

        areas.put("pitch", area("pitch", "Pitch and expression",
                "Try comfortable higher and lower voice sounds and notice expression in phrases.",
                lesson("pitch_1", "pitch", "Pitch and expression", "High and low sounds",
                        "Try a comfortable lower sound, then a comfortable higher sound.",
                        "Keep both sounds easy. There is no target pitch to reach.",
                        "high and low", LessonDefinition.ACTIVITY_PITCH, true, false, 0, 6,
                        "Try a comfortable lower Aaa.", "Now try a comfortable higher Aaa."),
                lesson("pitch_2", "pitch", "Pitch and expression", "Explore a comfortable pitch",
                        "Try a comfortable voice sound and listen back to your recording.",
                        "A reference tone is not available in this version. This is an exploration, not a pitch-matching score.",
                        "pitch matching", LessonDefinition.ACTIVITY_PITCH, true, false, 0, 6,
                        "Try a comfortable Aaa.", "Rest, then try once more."),
                lesson("pitch_3", "pitch", "Pitch and expression", "Keep a steady pitch",
                        "Hold a comfortable vowel and notice its pitch over time.",
                        "A pitch trace is only an estimate. Natural changes in your voice are normal.",
                        "pitch awareness", LessonDefinition.ACTIVITY_PITCH, true, false, 0, 7,
                        "Hold a comfortable Aaa.", "Rest, then record a second Aaa."),
                lesson("pitch_4", "pitch", "Pitch and expression", "Gentle pitch changes",
                        "Move your voice gently from lower to higher and back.",
                        "Use a small, easy change. Do not reach for extreme high or low sounds.",
                        "gentle pitch movement", LessonDefinition.ACTIVITY_PITCH, true, false, 0, 7,
                        "Glide gently from lower to higher, then back.", "Rest, then try the glide again."),
                lesson("pitch_5", "pitch", "Pitch and expression", "Expressive phrases",
                        "Try how a voice can make the same words sound different.",
                        "Say each phrase naturally. You can listen to your recording and decide what it sounds like.",
                        "expression", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "You're here.", "You're here?", "Really!")));

        areas.put("loudness", area("loudness", "Loudness",
                "Explore comfortable voice levels. Phone recordings show relative signal, not room loudness.",
                lesson("loudness_1", "loudness", "Loudness", "Find a comfortable voice level",
                        "Say Aaa at a level that feels comfortable to you.",
                        "Phone settings affect the recorded level; the number is not an absolute loudness measurement.",
                        "comfortable level", LessonDefinition.ACTIVITY_LOUDNESS, true, false, 0, 6,
                        "Say Aaa at your comfortable level.", "Rest, then try once more."),
                lesson("loudness_2", "loudness", "Loudness", "Repeat consistently",
                        "Say the same short sound several times at a comfortable level.",
                        "Notice whether the attempts feel similar. Consistent does not mean louder.",
                        "relative level", LessonDefinition.ACTIVITY_LOUDNESS, true, false, 0, 6,
                        "Aaa at a comfortable level.", "Aaa at the same comfortable level.",
                        "Aaa once more, without pushing."),
                lesson("loudness_3", "loudness", "Loudness", "Make a gentle change",
                        "Try a small comfortable increase, then return to your usual level.",
                        "Do not shout or whisper forcefully. Keep each change easy.",
                        "gentle level changes", LessonDefinition.ACTIVITY_LOUDNESS, true, false, 0, 6,
                        "Say Aaa comfortably.", "Try a little stronger, only if comfortable.",
                        "Return to your usual comfortable level."),
                lesson("loudness_4", "loudness", "Loudness", "Keep your level steady",
                        "Hold a comfortable vowel and notice whether its recorded level changes.",
                        "The phone's microphone changes the measurement. Rest whenever needed.",
                        "level variation", LessonDefinition.ACTIVITY_LOUDNESS, true, false, 0, 8,
                        "Hold Aaa at an easy level.", "Rest, then record another comfortable Aaa."),
                lesson("loudness_5", "loudness", "Loudness", "Use your voice in phrases",
                        "Try comfortable, steady speaking across a few short phrases.",
                        "Speak naturally. Recorded signal level is relative and should not be treated as a room reading.",
                        "phrases and level", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "Good morning.", "Please help me.", "Thank you.")));

        areas.put("articulation", area("articulation", "Clear sounds",
                "Practise sounds and notice your own effort. The microphone cannot check mouth placement.",
                lesson("articulation_1", "articulation", "Clear sounds", "Lip sounds",
                        "Practise P and M sounds with comfortable lip movement.",
                        "The microphone cannot tell where your lips are. Notice the movement yourself.",
                        "lip sounds", LessonDefinition.ACTIVITY_SOUND, true, true, 0, 5,
                        "P", "M", "P", "M"),
                lesson("articulation_2", "articulation", "Clear sounds", "Tongue sounds",
                        "Practise T and D sounds at an easy pace.",
                        "The microphone cannot confirm tongue placement. Use your own sense of comfort.",
                        "tongue sounds", LessonDefinition.ACTIVITY_SOUND, true, true, 0, 5,
                        "T", "D", "T", "D"),
                lesson("articulation_3", "articulation", "Clear sounds", "Compare sounds",
                        "Try two different syllables and listen for how they sound to you.",
                        "The app does not score pronunciation. You can ask a communication partner for feedback.",
                        "sound contrasts", LessonDefinition.ACTIVITY_WORD, true, true, 0, 6,
                        "PA", "TA", "PA", "TA"),
                lesson("articulation_4", "articulation", "Clear sounds", "Clear syllables",
                        "Practise one syllable at a time with a comfortable pause.",
                        "Take a rest between turns. Use self-checking rather than an automatic score.",
                        "syllables", LessonDefinition.ACTIVITY_WORD, true, true, 0, 5,
                        "PA", "TA", "KA", "MA"),
                lesson("articulation_5", "articulation", "Clear sounds", "Sound sequences",
                        "Build a short sequence from PA, TA and KA.",
                        "Try each sound on its own first. A communication partner can offer optional feedback.",
                        "sound sequence", LessonDefinition.ACTIVITY_WORD, true, true, 0, 7,
                        "PA", "TA", "KA", "PA-TA-KA")));

        areas.put("syllables", area("syllables", "Syllables and words",
                "Practise one syllable or familiar word at a time, with replay and rest between turns.",
                lesson("syllables_1", "syllables", "Syllables and words", "Repeat one syllable",
                        "Say BA once for each turn.",
                        "Listen to your own recording if you wish. Rest between repetitions.",
                        "single syllable", LessonDefinition.ACTIVITY_WORD, true, false, 0, 5,
                        "BA", "BA", "BA", "BA", "BA"),
                lesson("syllables_2", "syllables", "Syllables and words", "Try different syllables",
                        "Practise MA and PA one at a time.",
                        "Follow the screen; it will show just one syllable for each turn.",
                        "changing syllables", LessonDefinition.ACTIVITY_WORD, true, false, 0, 5,
                        "MA", "MA", "MA", "PA", "PA", "PA"),
                lesson("syllables_3", "syllables", "Syllables and words", "Change the syllable",
                        "Move between BA, MA and PA at an easy pace.",
                        "Say only the syllable shown, then continue when you are ready.",
                        "syllable changes", LessonDefinition.ACTIVITY_WORD, true, false, 0, 5,
                        "BA", "MA", "PA", "BA", "MA", "PA"),
                lesson("syllables_4", "syllables", "Syllables and words", "Build simple words",
                        "Practise familiar words one at a time.",
                        "Choose words that feel suitable for you. Skip a word or rest whenever you like.",
                        "familiar words", LessonDefinition.ACTIVITY_WORD, true, false, 0, 7,
                        "Mama", "Baby", "Paper", "Potato"),
                lesson("syllables_5", "syllables", "Syllables and words", "Try longer words",
                        "Practise a few longer words gradually.",
                        "These are examples, not required targets. Choose familiar words that suit you.",
                        "longer words", LessonDefinition.ACTIVITY_WORD, true, false, 0, 8,
                        "Yesterday", "Remember", "Wonderful")));

        areas.put("pacing", area("pacing", "Speaking rhythm",
                "Try an easy pace, visual pauses and steady timing without stretching sounds unnaturally.",
                lesson("pacing_1", "pacing", "Speaking rhythm", "Slow and comfortable",
                        "Say each short phrase at a pace that feels manageable.",
                        "Slow down gently; do not stretch the sounds.",
                        "comfortable pace", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "Good morning.", "How are you?", "I am glad to see you."),
                lesson("pacing_2", "pacing", "Speaking rhythm", "One item at a time",
                        "Say one word or syllable with each visual cue.",
                        "Let the cue remind you to take your time. Speak in your own rhythm.",
                        "visual timing", LessonDefinition.ACTIVITY_WORD, true, false, 0, 5,
                        "BA", "MAMA", "PAPER", "THANK YOU"),
                lesson("pacing_3", "pacing", "Speaking rhythm", "Add pauses",
                        "Try short phrase parts with a pause between them.",
                        "Use the pause as a reminder, not a strict timing rule.",
                        "phrase pauses", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "When I am ready, ...", "I will say more."),
                lesson("pacing_4", "pacing", "Speaking rhythm", "Keep a steady pace",
                        "Repeat familiar phrases and notice how your pacing feels.",
                        "The app does not calculate speaking speed from recording length alone.",
                        "pace awareness", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "Thank you.", "Thank you.", "How are you?", "How are you?"),
                lesson("pacing_5", "pacing", "Speaking rhythm", "Put it together",
                        "Practise a short sentence with a comfortable pause.",
                        "Pause naturally if you need to. Stop before you feel tired.",
                        "sentence rhythm", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 7,
                        "I would like some tea.", "Please wait while I get ready.")));

        areas.put("connected", area("connected", "Everyday speaking",
                "Practise useful personal phrases, sentences, reading and open speaking.",
                lesson("connected_1", "connected", "Everyday speaking", "Introduce yourself",
                        "Practise the start of an introduction using your own name.",
                        "You may replace the example with your name. Recording is optional to replay.",
                        "personal introduction", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "Hello, my name is...", "It is nice to meet you."),
                lesson("connected_2", "connected", "Everyday speaking", "Everyday phrases",
                        "Choose and practise phrases that are useful to you.",
                        "Skip any phrase that does not fit your needs.",
                        "useful phrases", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 6,
                        "Good morning.", "Thank you.", "Please help me.", "How are you?"),
                lesson("connected_3", "connected", "Everyday speaking", "Short sentences",
                        "Practise a few everyday sentences at a comfortable pace.",
                        "Take your time and pause wherever it feels natural.",
                        "sentences", LessonDefinition.ACTIVITY_PHRASE, true, false, 0, 7,
                        "I would like some water.", "Please call my family.", "I am ready to go."),
                lesson("connected_4", "connected", "Everyday speaking", "Read aloud",
                        "Read a short passage and listen to your recording if you wish.",
                        "The app does not score pronunciation. Take a pause or stop at any time.",
                        "reading", LessonDefinition.ACTIVITY_READING, true, false, 0, 8,
                        "Today is a new day. I can take my time, pause when I need to, and say one thing at a time."),
                lesson("connected_5", "connected", "Everyday speaking", "Speak freely",
                        "Try a short recording about a familiar topic.",
                        "You could talk about your day. Replay it or invite a communication partner to listen.",
                        "open speaking", LessonDefinition.ACTIVITY_FREE_SPEAKING, true, true, 0, 8,
                        "Tell me about your day.")));

        areas.put("mouth_articulation", area("mouth_articulation", "Mouth Movement and Articulation",
                "Gentle, guided practice for lips, jaw, visible speech movements, and everyday words. "
                        + "Selected activities offer optional relative facial-contour observations; they do not measure tongue placement or score pronunciation. Every activity can be completed without a camera.",
                mouthLesson("mouth_1", "Relaxed Lip Position", "Group A — Lip Mobility and Control",
                        "Notice how your lips rest when your face feels comfortable.",
                        "A relaxed starting position can help you notice movement without forcing it.",
                        "Sit comfortably. Let your lips rest naturally. Notice the position without trying to change it. Repeat for three short turns.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 8,
                        "Let your lips rest naturally.", "Pause and notice your relaxed lips.",
                        "Notice once more, without changing your lips."),
                mouthLesson("mouth_2", "Gentle Lip Closure", "Group A — Lip Mobility and Control",
                        "Gently bring your lips together, then let them relax apart.",
                        "Lips meet for sounds such as M, P, and B. This practice is gentle awareness, not a strength test.",
                        "Bring your lips together softly. Let them separate naturally. Do not press or hold tightly.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 5,
                        "Gently close your lips, then relax.", "Try the gentle movement again.",
                        "One final easy close and relax."),
                mouthLesson("mouth_3", "Smile and Relax", "Group A — Lip Mobility and Control",
                        "Make a small comfortable smile, then relax your face.",
                        "A smile changes the shape of the lips. Keep it small and easy.",
                        "Let your lips spread into a gentle smile, then return to rest. Do not stretch your face.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 5,
                        "Make a small, easy smile.", "Relax your face, then smile gently again.",
                        "Return to a comfortable resting position."),
                mouthLesson("mouth_4", "Lip Rounding", "Group A — Lip Mobility and Control",
                        "Gently round your lips, then let them return to rest.",
                        "Rounded lips help shape some speech sounds. Only use a comfortable, small movement.",
                        "Bring your lips forward a little as if beginning an 'oo' sound, then relax.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 5,
                        "Round your lips gently.", "Relax, then try a small round shape again.",
                        "Let your lips rest naturally."),
                mouthLesson("mouth_5", "Spread and Round", "Group A — Lip Mobility and Control",
                        "Try a gentle spread shape and a gentle rounded shape.",
                        "Changing lip shape is one way the mouth helps form different sounds.",
                        "Make a small smile shape, relax, then gently round your lips. Avoid wide or forced movements.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 5,
                        "Gently spread your lips.", "Relax, then round your lips softly.",
                        "Return to rest and notice the change."),
                mouthLesson("mouth_6", "Comfortable Mouth Opening", "Group B — Jaw Movement and Control",
                        "Open your mouth only as far as feels comfortable, then rest.",
                        "The jaw helps open and close the mouth for speech. A large opening is not a goal.",
                        "Sit comfortably. Make a small, easy opening and close naturally. Never force your jaw.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 5,
                        "Make a small comfortable mouth opening.", "Close naturally and rest.",
                        "Try one more easy opening."),
                mouthLesson("mouth_7", "Gentle Jaw Closing", "Group B — Jaw Movement and Control",
                        "Notice your jaw gently closing and returning to rest.",
                        "The jaw moves during many speech sounds. This activity is about noticing an easy movement.",
                        "Let your jaw lower a little, then close comfortably. Do not clench your teeth.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 5,
                        "Let your jaw lower slightly, then close.", "Relax your jaw completely.",
                        "Try one more gentle close."),
                mouthLesson("mouth_8", "Small and Wide Openings", "Group B — Jaw Movement and Control",
                        "Compare a small opening with a slightly wider comfortable opening.",
                        "Different mouth openings can be part of speaking. Wider is not better.",
                        "Start with a small opening. If comfortable, open a little wider, then return to rest. Do not stretch or cause pain.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 6,
                        "Make a small opening.", "Rest, then try a little wider only if comfortable.",
                        "Let your mouth return to rest."),
                mouthLesson("mouth_9", "Controlled Jaw Movement", "Group B — Jaw Movement and Control",
                        "Move your jaw a small amount and return to a comfortable resting position.",
                        "Noticing jaw movement can help you explore how the mouth moves during speech.",
                        "Make a small, easy jaw movement. Keep your head relaxed. Stop if your jaw feels uncomfortable.",
                        LessonDefinition.ACTIVITY_OBSERVE, false, 6,
                        "Move your jaw gently, then rest.", "Try another small, comfortable movement.",
                        "Return to your natural resting position."),
                mouthLesson("mouth_10", "Lip and Jaw Coordination", "Group B — Jaw Movement and Control",
                        "Notice your lips and jaw moving together during an easy 'ba' sound.",
                        "Speech uses several mouth movements together. This practice does not measure or score coordination.",
                        "Say 'ba' gently, then relax. Notice the movement without trying to make it bigger.",
                        LessonDefinition.ACTIVITY_SOUND, true, 6,
                        "Say 'ba' comfortably.", "Rest, then try 'ba' once more.",
                        "One final easy 'ba'."),
                mouthLesson("mouth_11", "The Sounds P and B", "Group C — Visible Speech Movements",
                        "Practise P and B and notice your lips coming together.",
                        "A camera may show relative lip-closure and separation geometry, but cannot verify the sound or score pronunciation.",
                        "Say each sound comfortably. Listen to the recording if you wish. Do not press your lips forcefully.",
                        LessonDefinition.ACTIVITY_SOUND, true, 6,
                        "Try P.", "Rest, then try B.", "Try P and B at an easy pace."),
                mouthLesson("mouth_12", "The Sound M", "Group C — Visible Speech Movements",
                        "Practise a comfortable M sound and notice your lips meeting.",
                        "M is made with the lips together while voice continues. The app cannot check whether a sound is correct.",
                        "Try a gentle 'mmm' sound, then relax. Stop if you feel strain.",
                        LessonDefinition.ACTIVITY_SOUND, true, 6,
                        "Try a gentle 'mmm'.", "Rest, then try it again.",
                        "One final comfortable 'mmm'."),
                mouthLesson("mouth_13", "Lip-to-Teeth Sounds F and V", "Group C — Visible Speech Movements",
                        "Try F and V while noticing where your lower lip meets your upper teeth.",
                        "This camera contour model cannot establish lip-to-teeth contact. Use comfortable self-observation; the app does not assess pronunciation.",
                        "Use a small, comfortable movement. Do not bite your lip or force the sound.",
                        LessonDefinition.ACTIVITY_SOUND, true, 7,
                        "Try F gently.", "Rest, then try V.", "Try F and V comfortably."),
                mouthLesson("mouth_14", "Combining Visible Lip Sounds", "Group C — Visible Speech Movements",
                        "Move between a few lip-based sounds at a comfortable pace.",
                        "Optional camera feedback shows relative lip geometry in individual frames; it does not identify sounds, transitions, or pronunciation.",
                        "Try each sound separately first, then move between them gently. Rest as needed.",
                        LessonDefinition.ACTIVITY_WORD, true, 7,
                        "Try M.", "Try P.", "Try B.", "Now try M, P, B slowly."),
                mouthLesson("mouth_15", "Repeating Lip-Based Syllables", "Group C — Visible Speech Movements",
                        "Practise short syllables that use visible lip movements.",
                        "Repeated movements can be explored at an easy pace; there is no required speed or accuracy score.",
                        "Say one syllable at a time. Pause whenever you need to.",
                        LessonDefinition.ACTIVITY_WORD, true, 6,
                        "MA", "PA", "BA", "MA, PA, BA slowly."),
                mouthLesson("mouth_16", "Syllable Transitions", "Group D — Mouth Movement in Speech",
                        "Move between syllables and notice how your mouth shape changes.",
                        "This activity relies on your own observation. The camera provider does not calculate temporal syllable or mouth-shape transitions.",
                        "Try each syllable on its own, then say them in sequence at a comfortable pace.",
                        LessonDefinition.ACTIVITY_WORD, true, 7,
                        "MA", "PA", "Now try MA-PA slowly.", "Rest, then try once more."),
                mouthLesson("mouth_17", "Short Lip-Sound Words", "Group D — Mouth Movement in Speech",
                        "Practise a few familiar short words with visible lip sounds.",
                        "Words can combine several mouth movements. Choose a word that feels familiar and comfortable.",
                        "Say one word at a time. You can listen to your recording or ask someone you trust for feedback.",
                        LessonDefinition.ACTIVITY_WORD, true, 7,
                        "Mom", "Baby", "Map", "Pop"),
                mouthLesson("mouth_18", "Words in Short Phrases", "Group D — Mouth Movement in Speech",
                        "Try short phrases that include familiar lip-sound words.",
                        "Speaking in phrases brings movements together. Pause whenever you need.",
                        "Read or repeat one phrase at a time. You can change a phrase to suit your needs.",
                        LessonDefinition.ACTIVITY_PHRASE, true, 8,
                        "Mom is here.", "Baby has a map.", "Please pass the paper."),
                mouthLesson("mouth_19", "Speaking with Natural Mouth Movement", "Group D — Mouth Movement in Speech",
                        "Speak a familiar phrase without making extra-large mouth movements.",
                        "Everyday speaking uses natural movement. There is no need to exaggerate or force a movement.",
                        "Say each phrase in your usual comfortable way. Replay or self-assess if helpful.",
                        LessonDefinition.ACTIVITY_PHRASE, true, 8,
                        "Good morning.", "How are you today?", "Thank you for helping me."),
                mouthLesson("mouth_20", "Everyday Speaking Practice", "Group D — Mouth Movement in Speech",
                        "Practise useful everyday phrases with comfortable mouth movement.",
                        "The purpose is to practise meaningful phrases, not to pass a pronunciation test.",
                        "Choose phrases that matter to you. Speak at your pace, pause, and stop if you feel discomfort.",
                        LessonDefinition.ACTIVITY_PHRASE, true, 8,
                        "Please help me.", "I would like some water.", "It is nice to see you.")));

        return Collections.unmodifiableMap(areas);
    }

    private static LessonArea area(String key, String title, String description, LessonDefinition... lessons) {
        return new LessonArea(key, title, description, Arrays.asList(lessons));
    }

    private static LessonDefinition lesson(String id, String areaKey, String areaTitle, String title,
                                           String objective, String instructions, String focus,
                                           String activityType, boolean recordingRequired,
                                           boolean selfAssessmentRequired, int timedStepSeconds,
                                           int restSeconds, String... prompts) {
        return new LessonDefinition(id, areaKey, areaTitle, title, objective, instructions, focus,
                "At your pace", "3-7 min", activityType, Arrays.asList(prompts), recordingRequired,
                selfAssessmentRequired, timedStepSeconds, restSeconds, "",
                "This activity lets you practise " + focus + " in short steps. Go at your own pace and stop if anything feels uncomfortable.",
                LessonMeasurementRequirements.forLesson(areaKey, id, activityType, recordingRequired));
    }

    private static LessonDefinition mouthLesson(String id, String title, String groupTitle,
                                                String objective, String explanation, String instructions,
                                                String activityType, boolean recordingSuggested,
                                                int restSeconds, String... prompts) {
        return new LessonDefinition(id, "mouth_articulation", "Mouth Movement and Articulation",
                title, objective, instructions, title.toLowerCase(), "At your pace", "3-7 min",
                activityType, Arrays.asList(prompts), recordingSuggested, true, 0, restSeconds,
                groupTitle, explanation,
                LessonMeasurementRequirements.forLesson("mouth_articulation", id, activityType, recordingSuggested));
    }
}
