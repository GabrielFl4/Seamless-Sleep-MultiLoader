package net.aqualoco.sec.client.sound;

import net.minecraft.resources.Identifier;

import java.util.Optional;

public record SleepSoundCandidate(SleepSoundCueType cueType,
                                  SleepSoundProfile profile,
                                  Optional<Identifier> soundId,
                                  float volume,
                                  float pitch,
                                  boolean looping) {
}
