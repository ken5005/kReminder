package ken5005.kreminder.sound;

/** NotifyPatternの1ステップ＝この音をこの音量で鳴らし、鳴らした後delayAfterMsミリ秒待つ。 */
public record NotifyStep(String soundName, float volume, long delayAfterMs) {
}
