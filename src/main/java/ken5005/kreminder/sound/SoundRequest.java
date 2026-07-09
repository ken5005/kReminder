package ken5005.kreminder.sound;

/** SoundWorkerのキューに積む1件の再生依頼。volumeは0.0〜1.0（呼び出し側でclamp済み）。 */
public record SoundRequest(String name, float volume) {}
