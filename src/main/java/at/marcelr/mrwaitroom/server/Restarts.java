package at.marcelr.mrwaitroom.server;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.link.MainLink;
import at.marcelr.mrwaitroom.link.MainStatus;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Restarts of the waiting room server: by hand (an order from MRadmin, {@code mrserver restart}) and planned
 * ({@code server.restart-times}). The waiting room is what players fall back to - so it only restarts when it is not
 * needed ({@link #blocked()}):
 * <ul>
 *     <li>the starter runs it (otherwise nothing would bring it back),</li>
 *     <li>the main server is up and ready, no maintenance runs,</li>
 *     <li>no restart of the main server comes within 15 minutes,</li>
 *     <li>nobody waits here (while the main server is ready, they are sent back anyway).</li>
 * </ul>
 * A restart that is due but blocked waits until it is allowed - one by hand only while nobody waits (other reasons
 * refuse it at once), a planned one until the reasons are gone. Server thread.
 * <p>
 * From MRadmin's web interface (agent, since 1.6) like the main server: restart or stop after a delay with a reason
 * ({@link #schedule}) - at the time a restart still waits for the rules above; a stop and an emergency (force) do not -
 * cancel, and pause the planned restarts for 24 hours.
 */
public final class Restarts {

    /** A main server restart this close blocks the restart of the waiting room. */
    private static final long MAIN_RESTART_GAP = 15 * 60_000L;
    /** A restart by hand that waits for the last players gives up after this long. */
    private static final long MANUAL_WAIT = 10 * 60_000L;

    private final MRwaitroom plugin;
    private BukkitTask task;
    /** A restart by hand waiting for the room to be empty (since when), 0 = none. */
    private long manualSince;
    private String manualBy = "";
    /** A planned restart is due (since when), 0 = none. */
    private long plannedSince;
    /** The last planned time handled (so it is not due twice in the same minute). */
    private ZonedDateTime lastPlanned;
    /** A countdown from MRadmin: when (epoch ms, 0 = none), restart or stop, why, by whom, ignoring the rules. */
    private long dueAt;
    private String dueKind = "restart";
    private String dueReason = "";
    private String dueBy = "";
    private boolean dueForce;
    /** Planned restarts paused until (epoch ms), by whom. */
    private long pausedUntil;
    private String pausedBy = "";

    public Restarts(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        plugin.starter().onRequest(this::request);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Why the server must not restart now, or null if it may. Keys of the texts in MRadmin: no-starter,
     * maintenance, main-not-ready, main-restart-soon, players-waiting.
     */
    public String blocked() {
        if (!plugin.starter().active()) {
            return "no-starter";
        }
        MainLink.View view = plugin.link().view();
        if (view.phase() == MainLink.Phase.MAINTENANCE) {
            return "maintenance";
        }
        if (view.phase() != MainLink.Phase.READY) {
            return "main-not-ready";
        }
        MainStatus.Restart restart = view.status() == null ? null : view.status().restart();
        if (restart != null && restart.at() - System.currentTimeMillis() < MAIN_RESTART_GAP) {
            return "main-restart-soon";
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            return "players-waiting";
        }
        return null;
    }

    /** What waits: "manual", "planned" or null. */
    public String pending() {
        return manualSince > 0 ? "manual" : plannedSince > 0 ? "planned" : null;
    }

    /** The next planned restart (epoch ms), 0 if none. */
    public long nextPlanned() {
        List<LocalTime> times = plugin.settings().server().restartTimes();
        if (times.isEmpty()) {
            return 0;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.settings().zone());
        ZonedDateTime best = null;
        for (LocalTime t : times) {
            ZonedDateTime at = now.with(t).withSecond(0).withNano(0);
            if (!at.isAfter(now)) {
                at = at.plusDays(1);
            }
            if (best == null || at.isBefore(best)) {
                best = at;
            }
        }
        return best.toInstant().toEpochMilli();
    }

    /**
     * A restart by hand (MRadmin, console).
     *
     * @return null if it happens (now or once the last players left), otherwise why not
     */
    public String requestByHand(String by) {
        String reason = blocked();
        if (reason != null && !reason.equals("players-waiting")) {
            return reason;
        }
        manualSince = System.currentTimeMillis();
        manualBy = by;
        plugin.getLogger().info(by + " asked for a restart of the waiting room"
                + (reason == null ? "." : " - it happens once nobody waits."));
        plugin.events().add("restart-ordered", by + " asked for a restart of the waiting room.", at.marcelr.mrwaitroom.link.Events.INFO);
        // Not right here: the caller first confirms the order (MRadmin), the report before going down carries it.
        Bukkit.getScheduler().runTask(plugin, this::tick);
        return null;
    }

    /** Cancels a waiting restart (by hand and planned) and a countdown. */
    public boolean cancel() {
        boolean any = manualSince > 0 || plannedSince > 0 || dueAt > 0;
        manualSince = 0;
        plannedSince = 0;
        dueAt = 0;
        return any;
    }

    /**
     * A countdown from MRadmin: restart or stop in {@code delay} ms. A restart without force is refused at once for the
     * same reasons as one by hand (except waiting players - it waits for them at the time).
     *
     * @return null if accepted, otherwise why not (keys as {@link #blocked()}, or "running")
     */
    public String schedule(String kind, long delay, String reason, String by, boolean force) {
        boolean stop = "stop".equals(kind);
        if (dueAt > 0) {
            return "running";
        }
        if (!stop && !force) {
            String why = blocked();
            if (why != null && !why.equals("players-waiting")) {
                return why;
            }
        } else if (!stop && !plugin.starter().active()) {
            return "no-starter";
        }
        dueAt = System.currentTimeMillis() + Math.max(0, delay);
        dueKind = stop ? "stop" : "restart";
        dueReason = reason == null ? "" : reason.strip();
        dueBy = by == null || by.isBlank() ? "MRadmin" : by;
        dueForce = force;
        String text = by + (stop ? " stops" : " restarts") + " the waiting room in " + Math.max(0, delay / 1000) + " s"
                + (force ? " (emergency)" : "") + (dueReason.isEmpty() ? "." : " - " + dueReason + ".");
        plugin.getLogger().info(text);
        plugin.events().add(stop ? "stop-ordered" : "restart-ordered", text, force ? at.marcelr.mrwaitroom.link.Events.BAD : at.marcelr.mrwaitroom.link.Events.INFO);
        Bukkit.getScheduler().runTask(plugin, this::tick);
        return null;
    }

    /** Pauses the planned restarts for 24 hours (a due planned one is dropped), or switches them on again. */
    public void pause(boolean on, String by) {
        pausedUntil = on ? System.currentTimeMillis() + 24 * 3_600_000L : 0;
        pausedBy = on ? by : "";
        if (on) {
            plannedSince = 0;
        }
        String text = on ? by + " paused the planned restarts of the waiting room for 24 hours." : by + " switched the planned restarts of the waiting room on again.";
        plugin.getLogger().info(text);
        plugin.events().add(on ? "schedule-paused" : "schedule-resumed", text, at.marcelr.mrwaitroom.link.Events.INFO);
    }

    /** The state for MRadmin's page (agent): countdown, pause, planned restarts. */
    public java.util.Map<String, Object> state() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("dueAt", dueAt);
        m.put("dueKind", dueKind);
        m.put("dueReason", dueReason);
        m.put("dueBy", dueBy);
        m.put("dueForce", dueForce);
        m.put("pausedUntil", pausedUntil > System.currentTimeMillis() ? pausedUntil : 0);
        m.put("pausedBy", pausedBy);
        m.put("restartTimes", plugin.settings().server().restartTimes().size());
        m.put("nextPlanned", nextPlanned());
        m.put("pending", pending());
        m.put("blocked", blocked());
        return m;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        // A planned time reached (checked every 5 seconds - the minute of the time counts).
        ZonedDateTime minute = ZonedDateTime.now(plugin.settings().zone()).withSecond(0).withNano(0);
        if (plugin.settings().server().restartTimes().contains(minute.toLocalTime())
                && (lastPlanned == null || !lastPlanned.equals(minute)) && pausedUntil <= now) {
            lastPlanned = minute;
            if (plannedSince == 0) {
                plannedSince = now;
                plugin.getLogger().info("Planned restart of the waiting room at " + minute.toLocalTime() + ".");
            }
        }
        if (manualSince > 0 && now - manualSince > MANUAL_WAIT) {
            plugin.getLogger().info("The restart by hand was given up - players kept waiting for 10 minutes.");
            plugin.events().add("restart-given-up", "The restart by hand was given up - players kept waiting for 10 minutes.", at.marcelr.mrwaitroom.link.Events.WARN);
            manualSince = 0;
        }
        // A countdown from MRadmin is over: a stop and an emergency go now, a restart waits for the rules.
        if (dueAt > 0 && now >= dueAt) {
            boolean stop = dueKind.equals("stop");
            String why = dueReason.isEmpty() ? "" : " - " + dueReason;
            dueAt = 0;
            if (stop || dueForce) {
                String text = (stop ? "Stopping" : "Restarting") + " the waiting room (by " + dueBy + why + ").";
                plugin.getLogger().info(text);
                plugin.events().add(stop ? "stop" : "restart", text, at.marcelr.mrwaitroom.link.Events.WARN);
                if (!stop) {
                    plugin.remote().announceRestart();
                }
                plugin.starter().prepare(!stop);
                Bukkit.shutdown();
                return;
            }
            manualSince = now;
            manualBy = dueBy;
        }
        if (manualSince == 0 && plannedSince == 0) {
            return;
        }
        String reason = blocked();
        if (reason != null) {
            return;
        }
        plugin.getLogger().info("Restarting the waiting room (" + (manualSince > 0 ? "by " + manualBy : "planned") + ").");
        plugin.events().add("restart", "Restarting the waiting room (" + (manualSince > 0 ? "by " + manualBy : "planned") + ").", at.marcelr.mrwaitroom.link.Events.WARN);
        manualSince = 0;
        plannedSince = 0;
        plugin.remote().announceRestart();
        plugin.starter().prepare(true);
        Bukkit.shutdown();
    }

    /**
     * Restarts now to put in a downloaded update, if the rules allow it (otherwise it is tried again later). Without
     * the starter the server just stops - whatever runs it brings it back, as before.
     *
     * @return true if the server goes down
     */
    public boolean restartForUpdate(String what) {
        String reason = blocked();
        if (reason != null && !reason.equals("no-starter")) {
            return false;
        }
        String text = "Restarting the waiting room to put in " + what + " (nobody waits, the main server is ready).";
        plugin.getLogger().info(text);
        plugin.events().add("restart", text, at.marcelr.mrwaitroom.link.Events.WARN);
        plugin.remote().announceRestart();
        plugin.starter().prepare(true);
        Bukkit.shutdown();
        return true;
    }

    /** mrserver restart|stop|cancel [time] [reason] - answered for the console. */
    private void request(String text) {
        String kind = text.split(" ", 2)[0];
        switch (kind) {
            case "restart" -> {
                String reason = requestByHand("console (mrserver)");
                plugin.starter().answer(reason == null ? "OK - the waiting room restarts as soon as nobody waits."
                        : "Not now: " + explain(reason) + ".");
            }
            case "stop" -> {
                plugin.starter().answer("OK - stopping the waiting room.");
                plugin.getLogger().info("Stopping the waiting room (console, mrserver).");
                plugin.events().add("stop", "Stopping the waiting room (console, mrserver).", at.marcelr.mrwaitroom.link.Events.WARN);
                plugin.starter().prepare(false);
                Bukkit.shutdown();
            }
            case "cancel" -> plugin.starter().answer(cancel() ? "OK - waiting restart cancelled." : "No restart waiting.");
            default -> plugin.starter().answer("Unknown request: " + kind);
        }
    }

    /** English, for the console. */
    public static String explain(String reason) {
        return switch (reason) {
            case "no-starter" -> "the server does not run under the starter (nothing would start it again)";
            case "maintenance" -> "a maintenance runs on the main server";
            case "main-not-ready" -> "the main server is not ready";
            case "main-restart-soon" -> "the main server restarts within 15 minutes";
            case "players-waiting" -> "players are waiting";
            default -> reason;
        };
    }
}
