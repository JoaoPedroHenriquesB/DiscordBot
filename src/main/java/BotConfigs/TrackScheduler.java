package BotConfigs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private TextChannel notificationChannel;
    private AudioTrack currentTrack;
    private boolean shouldNotifyNextTrack = true;

    public TrackScheduler(AudioPlayer player, TextChannel channel) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
        this.notificationChannel = channel;
    }

    public void setNotificationChannel(TextChannel channel) {
        this.notificationChannel = channel;
    }

    public void play(AudioTrack track) {
        try {
            currentTrack = track;
            player.startTrack(track, false);
            notifyNowPlaying(track);
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
        player.startTrack(currentTrack, false);
    }

    public void clearQueue() {
        queue.clear();
        notify("🗑️ Fila de músicas limpa");
    }

    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }

    public String getCurrentTrackInfo() {
        return currentTrack != null ? 
            String.format("🎶 **Tocando agora:** [%s](%s) `[%s]`",
                currentTrack.getInfo().title,
                currentTrack.getInfo().uri,
                formatDuration(currentTrack.getDuration())) 
            : "Nenhuma música tocando no momento";
    }

    public String getQueueStatus() {
        StringBuilder sb = new StringBuilder();
        
        if (currentTrack != null) {
            sb.append(getCurrentTrackInfo()).append("\n\n");
        }
        
        if (!queue.isEmpty()) {
            sb.append("📋 **Fila de músicas:**\n");
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
            nextTrack();
        } else {
            currentTrack = null;
        }
    }

    private void notifyNowPlaying(AudioTrack track) {
        notify("🎶 **Tocando agora:** " + formatTrackInfo(track));
    }

    private void notifyQueued(AudioTrack track) {
        notify("📥 **Adicionado à fila (" + queue.size() + "):** " + formatTrackInfo(track));
    }

    private void notify(String message) {
        if (notificationChannel != null) {
            notificationChannel.sendMessage(message).queue();
        }
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
    
    
}