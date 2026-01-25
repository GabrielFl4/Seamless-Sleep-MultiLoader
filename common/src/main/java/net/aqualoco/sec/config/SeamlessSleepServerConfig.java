package net.aqualoco.sec.config;

public final class SeamlessSleepServerConfig {
    public Boolean sleepClearsWeather = true;

    public void clamp() {
        if (sleepClearsWeather == null) {
            sleepClearsWeather = true;
        }
    }
}
