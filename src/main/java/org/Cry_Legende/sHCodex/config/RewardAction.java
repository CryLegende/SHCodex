package org.Cry_Legende.sHCodex.config;

public class RewardAction {
    public enum ActionType {MESSAGE, COMMAND, SOUND, TITLE, SUBTITLE}

    private final ActionType type;
    private final String value;
    private final float volume; // For SOUND
    private final float pitch;  // For SOUND
    private final int fadeIn;   // For TITLE/SUBTITLE
    private final int stay;     // For TITLE/SUBTITLE
    private final int fadeOut;  // For TITLE/SUBTITLE

    public RewardAction(ActionType type, String value, float volume, float pitch, int fadeIn, int stay, int fadeOut) {
        this.type = type;
        this.value = value;
        this.volume = volume;
        this.pitch = pitch;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    public ActionType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public int getFadeIn() {
        return fadeIn;
    }

    public int getStay() {
        return stay;
    }

    public int getFadeOut() {
        return fadeOut;
    }
}
