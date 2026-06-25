package net.aqualoco.sec.diagnostic;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationStatus;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.handshake.HandshakeFailureRecord;
import net.aqualoco.sec.handshake.SeamlessClientPresenceSnapshot;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceState;
import net.aqualoco.sec.platform.Services;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class SeamlessSleepIntegrityReporter {
    private static final String LOG_PREFIX = "[Seamless Sleep Integrity]";

    private SeamlessSleepIntegrityReporter() {
    }

    public static IntegrityReport create(MinecraftServer server) {
        List<String> details = new ArrayList<>();
        IntegrityHealth health = IntegrityHealth.STABLE;

        details.add("platform=" + Services.PLATFORM.getPlatformName()
                + ", environment=" + Services.PLATFORM.getEnvironmentName()
                + ", modVersion=" + Services.PLATFORM.getModVersion(Constants.MOD_ID));

        if (server == null) {
            details.add("server=null");
            return log(new IntegrityReport(IntegrityHealth.FATAL, "Server instance unavailable.", details));
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            details.add("overworld=null");
            health = IntegrityHealth.FATAL;
        } else {
            details.add("overworld=present, dayTime=" + overworld.getDayTime()
                    + ", gameTime=" + overworld.getGameTime()
                    + ", playersSleepingPercentage=" + overworld.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE)
                    + ", randomTickSpeed=" + Math.max(0, overworld.getGameRules().get(GameRules.RANDOM_TICK_SPEED))
                    + ", simulationDistance=" + server.getPlayerList().getSimulationDistance());
            details.add(describeBedState(overworld));
            details.add(describeAcceleration(overworld));
        }

        SeamlessSleepServerConfigManager.ReloadReport reloadReport = SeamlessSleepServerConfigManager.lastReloadReport();
        details.add("configPath=" + SeamlessSleepServerConfigManager.configPath()
                + ", configRevision=" + ServerConfigMutationService.currentRevision()
                + ", lastReloadStatus=" + reloadReport.status()
                + ", loadedDefaults=" + reloadReport.loadedDefaults());
        if (reloadReport.status() == SeamlessSleepServerConfigManager.ReloadResult.ERROR && health == IntegrityHealth.STABLE) {
            health = IntegrityHealth.WARN;
        }

        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        details.add("sleepAnimation=active:" + state.isActive()
                + ", session:" + state.getSessionId()
                + ", mode:" + state.getMode()
                + ", phase:" + state.getPhase()
                + ", visual:" + state.getVisualContext()
                + ", sound:" + state.getSoundMode()
                + ", progress:" + formatDouble(state.getProgress())
                + ", worldRate:" + formatDouble(state.getCurrentLogicalWorldRate()));

        Collection<SeamlessClientPresenceSnapshot> snapshots = ServerSeamlessClientPresenceManager.snapshots();
        int confirmed = 0;
        int pending = 0;
        for (SeamlessClientPresenceSnapshot snapshot : snapshots) {
            if (snapshot.state() == ServerSeamlessClientPresenceState.CONFIRMED) {
                confirmed++;
            } else if (snapshot.state() == ServerSeamlessClientPresenceState.PENDING) {
                pending++;
            }
        }
        details.add("handshake=confirmed:" + confirmed
                + ", pending:" + pending
                + ", tracked:" + snapshots.size()
                + ", online:" + server.getPlayerList().getPlayerCount());
        if ((pending > 0 || confirmed < server.getPlayerList().getPlayerCount()) && health == IntegrityHealth.STABLE) {
            health = IntegrityHealth.WARN;
        }

        List<HandshakeFailureRecord> failures = ServerSeamlessClientPresenceManager.failureHistory();
        if (!failures.isEmpty()) {
            HandshakeFailureRecord latest = failures.get(failures.size() - 1);
            details.add("handshakeFailures=" + failures.size()
                    + ", latest=" + latest.playerName()
                    + ":" + latest.state()
                    + ":" + latest.reason());
            if (health == IntegrityHealth.STABLE) {
                health = IntegrityHealth.WARN;
            }
        }

        String summary = switch (health) {
            case STABLE -> "Core state is stable.";
            case WARN -> "Core state is running with warnings; see latest.log for details.";
            case FATAL -> "Core state has a fatal issue; see latest.log for details.";
        };
        return log(new IntegrityReport(health, summary, List.copyOf(details)));
    }

    private static String describeBedState(ServerLevel overworld) {
        int managed = 0;
        int resting = 0;
        int counted = 0;
        int deep = 0;
        int delayTicks = SeamlessSleepServerConfigManager.get().fallAsleepDelayTicks;
        for (ServerPlayer player : overworld.players()) {
            if (BedRestingHelper.isManagedBedStateServer(player)) {
                managed++;
            }
            if (BedRestingHelper.isResting(player)) {
                resting++;
            }
            if (BedRestingHelper.isCountedForSleep(player)) {
                counted++;
            }
            if (BedRestingHelper.hasSleptLongEnough(player, delayTicks)) {
                deep++;
            }
        }
        return "bedState=managed:" + managed + ", resting:" + resting + ", counted:" + counted + ", deep:" + deep;
    }

    private static String describeAcceleration(ServerLevel overworld) {
        WorldSleepAccelerationStatus status = WorldSleepAccelerationManager.getDiagnosticStatus(overworld);
        return "acceleration=active:" + status.isActive()
                + ", rate:" + formatDouble(status.getWorldSleepRate())
                + ", avgMspt:" + formatDouble(status.getAverageMspt())
                + ", p95Mspt:" + formatDouble(status.getP95Mspt())
                + ", players:" + status.getActivePlayerCount()
                + ", governor:" + status.getGovernorAction();
    }

    private static IntegrityReport log(IntegrityReport report) {
        Constants.LOG.info("{} health={} summary={}", LOG_PREFIX, report.health(), report.summary());
        for (String detail : report.details()) {
            Constants.LOG.info("{} {}", LOG_PREFIX, detail);
        }
        return report;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
