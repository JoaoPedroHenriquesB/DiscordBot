package BotConfigs;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.AudioManager;

public class GuildMusicManager {
    public final AudioPlayer player;
    public final TrackScheduler scheduler;
    private TextChannel notificationChannel;
    private final AudioPlayerSendHandler sendHandler;
    private Guild guild;

    public GuildMusicManager(AudioPlayerManager manager, Guild guild, TextChannel channel) {
        this.player = manager.createPlayer();
        this.guild = guild;
        this.notificationChannel = channel;
        this.scheduler = new TrackScheduler(this.player, this);
        this.player.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(this.player);
    }

    public void setNotificationChannel(TextChannel channel) {
        this.notificationChannel = channel;
    }

    public TextChannel getNotificationChannel() {
        return notificationChannel;
    }

    public Guild getGuild() {
        return guild;
    }

    public void notify(String message) {
        if (notificationChannel != null && notificationChannel.canTalk()) {
            notificationChannel.sendMessage(message).queue();
        }
    }

    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }

    public void destroy() {
        // Limpa todos os recursos
        player.destroy();
        if (guild != null) {
            AudioManager audioManager = guild.getAudioManager();
            if (audioManager.getConnectedChannel() != null) {
                audioManager.closeAudioConnection();
            }
        }
    }
}