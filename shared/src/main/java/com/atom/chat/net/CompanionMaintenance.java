package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.util.CacheDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Retention and lifecycle housekeeping for the two server-hosted stores,
 * {@code atomchat-data/media/} and {@code atomchat-data/avatars/}.
 *
 * <p>Both stores are pruned the same way: every file older than
 * {@code retentionDays} goes first, then the oldest files are deleted until the
 * remaining total fits the store's size cap. Before 0.2.9 only the media store
 * was trimmed, and only as a side effect of a successful upload, so a server
 * nobody uploaded to kept its expired files forever and avatar PNGs were never
 * deleted at all. The sweep now also runs at server start and every
 * {@value #PRUNE_INTERVAL_TICKS} ticks, so a long-running server cleans itself.
 *
 * <p>Losing a file here is always safe: hosted media is re-uploaded by the
 * client that still has it, an avatar is re-uploaded by its owner, and both
 * clients treat a missing file as "not set" rather than an error.
 *
 * <p>The master hosting switch is deliberately not consulted: with it off no
 * new file is stored or served, which leaves everything already on disk
 * unreachable, and this sweep is what keeps those leftovers from sitting there
 * forever.
 */
public final class CompanionMaintenance {
    /** Five minutes at 20 ticks per second. Cheap enough for the server thread. */
    private static final int PRUNE_INTERVAL_TICKS = 20 * 60 * 5;

    /** A pending upload that has not finished in this long is abandoned. */
    static final long STALE_UPLOAD_MS = 5 * 60_000L;

    private static int tickCounter;

    private CompanionMaintenance() {
    }

    /**
     * Called when a server starts: one sweep up front so a restart prunes even
     * when nobody uploads anything afterwards.
     */
    public static void onServerStarted() {
        pruneNow();
    }

    /** Called every server tick; actually prunes once per {@value #PRUNE_INTERVAL_TICKS}. */
    public static void tick() {
        if (++tickCounter < PRUNE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        long now = System.currentTimeMillis();
        pruneNow(now);
        MediaCompanionServer.sweepStaleUploads(now);
    }

    /**
     * Called when a player leaves: drops the per-player upload bookkeeping so a
     * player who disconnects mid-transfer cannot leave a buffer behind for the
     * lifetime of the server process.
     */
    public static void onPlayerLogout(UUID player) {
        if (player == null) {
            return;
        }
        MediaCompanionServer.forgetPlayer(player);
        AvatarCompanionServer.forgetPlayer(player);
        PokeCompanionServer.forgetPlayer(player);
    }

    /** Applies the configured age and size caps to both hosted stores. */
    public static void pruneNow() {
        pruneNow(System.currentTimeMillis());
    }

    static void pruneNow(long now) {
        pruneMedia(now);
        pruneAvatars(now);
    }

    /** Media store sweep, also run right after a successful upload. */
    static void pruneMedia() {
        pruneMedia(System.currentTimeMillis());
    }

    private static void pruneMedia(long now) {
        AtomChatServerConfig config = AtomChatServerConfig.get();
        sweep(CacheDirs.mediaDataDir(), config.retentionDays, config.maxTotalBytes(), now);
    }

    private static void pruneAvatars(long now) {
        AtomChatServerConfig config = AtomChatServerConfig.get();
        sweep(CacheDirs.avatarDataDir(), config.retentionDays, config.maxAvatarTotalBytes(), now);
    }

    /**
     * Deletes files under {@code dir} that are older than {@code retentionDays}
     * (0 disables the age rule), then the oldest of what is left until the total
     * size fits {@code maxBytes}.
     *
     * <p>Every file in these directories is runtime data owned by this mod, so
     * there is nothing to filter out: an abandoned {@code .tmp} from an
     * interrupted write is pruned by the same rules as a finished file.
     *
     * @param now wall clock used for the age cutoff; explicit so tests can pin it
     * @return bytes freed, or -1 when the directory could not be scanned
     */
    static long sweep(Path dir, int retentionDays, long maxBytes, long now) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0L;
        }
        long cutoff = retentionDays > 0 ? now - retentionDays * 86_400_000L : Long.MIN_VALUE;
        List<Path> files = new ArrayList<>();
        List<Path> stale = new ArrayList<>();
        long total = 0L;
        long freed = 0L;
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                long modified = modifiedOrZero(p);
                if (modified > 0L && modified < cutoff) {
                    // Collected, not deleted, while the directory stream is
                    // still open: Windows dislikes removing an entry mid-listing.
                    stale.add(p);
                    continue;
                }
                files.add(p);
                total += sizeOrZero(p);
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to scan hosted data directory {}", dir, e);
            return -1L;
        }
        for (Path p : stale) {
            long size = sizeOrZero(p);
            if (deleteQuietly(p)) {
                freed += size;
            }
        }
        if (total <= maxBytes) {
            logFreed(dir, freed);
            return freed;
        }
        files.sort(Comparator.comparingLong(CompanionMaintenance::modifiedOrZero));
        for (Path p : files) {
            if (total <= maxBytes) {
                break;
            }
            long size = sizeOrZero(p);
            if (deleteQuietly(p)) {
                total -= size;
                freed += size;
            }
        }
        logFreed(dir, freed);
        return freed;
    }

    private static void logFreed(Path dir, long freed) {
        if (freed > 0L) {
            AtomChat.LOGGER.info("Pruned {} KB of hosted data from {}", freed / 1024L, dir);
        }
    }

    private static boolean deleteQuietly(Path p) {
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            return false;
        }
    }

    private static long sizeOrZero(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long modifiedOrZero(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
