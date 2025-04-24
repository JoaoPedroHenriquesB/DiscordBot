package BotConfigs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final GuildMusicManager musicManager;
    private AudioTrack currentTrack;
    private boolean shouldNotifyNextTrack = true;
    private boolean loop = false;

    public TrackScheduler(AudioPlayer player, GuildMusicManager musicManager) {
        this.player = player;
        this.musicManager = musicManager;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void play(AudioTrack track) {
        try {
            currentTrack = track;
            player.startTrack(track, false);
        } catch (Exception e) {
            notify("❌ Erro ao iniciar a reprodução: " + e.getMessage());
        }
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
            notifyQueued(track);
        }
    }

    public void nextTrack() {
        currentTrack = queue.poll();
        if (currentTrack != null) {
            player.startTrack(currentTrack, false);
            shouldNotifyNextTrack = false;
        } else {
            currentTrack = null;
            notify("⏹️ Fila de reprodução concluída");
        }
    }

    public void clearQueue() {
        queue.clear();
        notify("🗑️ Fila de músicas limpa");
    }

    public void shuffleQueue() {
        List<AudioTrack> tempList = new ArrayList<>(queue);
        Collections.shuffle(tempList);
        queue.clear();
        queue.addAll(tempList);
        notify("🔀 Fila embaralhada com " + queue.size() + " músicas");
    }

    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }

    public String getCurrentTrackInfo() {
        if (currentTrack == null) {
            return "Nenhuma música tocando no momento";
        }
        return String.format("🎶 **Tocando agora:** [%s](%s) `[%s]`",
            currentTrack.getInfo().title,
            currentTrack.getInfo().uri,
            formatDuration(currentTrack.getDuration()));
    }

    public String getQueueStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(getCurrentTrackInfo()).append("\n\n");

        if (!queue.isEmpty()) {
            sb.append("📋 **Fila de músicas (").append(queue.size()).append(") :**\n");
            int i = 1;
            for (AudioTrack track : queue) {
                sb.append(i++).append(". ").append(formatTrackInfo(track)).append("\n");
                if (i > 10) break;
            }
            if (queue.size() > 10) {
                sb.append("... e mais ").append(queue.size() - 10).append(" músicas");
            }
        } else {
            sb.append("ℹ️ **A fila está vazia**");
        }

        sb.append("\n🔁 Loop está ").append(loop ? "ativado" : "desativado").append("\n");
        return sb.toString();
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        currentTrack = track;
        if (shouldNotifyNextTrack) {
            notifyNowPlaying(track);
        }
        shouldNotifyNextTrack = true;
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            if (loop) {
                player.startTrack(track.makeClone(), false);
            } else {
                nextTrack();
            }
        }
    }

    private void notifyNowPlaying(AudioTrack track) {
        notify("🎶 **Tocando agora:** " + formatTrackInfo(track));
    }

    private void notifyQueued(AudioTrack track) {
        notify("📥 **Adicionado à fila (" + queue.size() + ") :** " + formatTrackInfo(track));
    }

    private void notify(String message) {
        musicManager.notify(message);
    }

    private String formatTrackInfo(AudioTrack track) {
        return String.format("[%s](%s) `[%s]`", 
            track.getInfo().title, 
            track.getInfo().uri,
            formatDuration(track.getDuration()));
    }

    private String formatDuration(long duration) {
        duration /= 1000;
        return String.format("%02d:%02d", duration / 60, duration % 60);
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean isLooping() {
        return loop;
    }

    public boolean toggleLoop() {
        this.loop = !this.loop;
        return this.loop;
    }
}
