package BotConfigs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.apache.http.client.config.RequestConfig;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class MusicManager {
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final ScheduledExecutorService scheduler;
    

    public MusicManager() {
        this.playerManager = new DefaultAudioPlayerManager();
        this.musicManagers = new HashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        this.playerManager.setHttpBuilderConfigurator(builder -> {
        	RequestConfig requestConfig = RequestConfig.custom()
        			.setConnectTimeout(10000)
        			.setSocketTimeout(10000)
        			.build();
        	
        	builder.setDefaultRequestConfig(requestConfig);
        });
        
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        
       
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild, TextChannel textChannel) {
        long guildId = guild.getIdLong();
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager, textChannel);
            musicManagers.put(guildId, musicManager);
        } else if (textChannel != null) {
            musicManager.setNotificationChannel(textChannel);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        return musicManager;
    }

    public void loadAndPlay(Guild guild, String trackUrl, TextChannel textChannel, Consumer<String> callback) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, textChannel);
        String processedUrl = preprocessQuery(trackUrl);
        
        playerManager.loadItemOrdered(musicManager, processedUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(textChannel);
                musicManager.scheduler.queue(track);
                callback.accept("🎵 Adicionado à fila: **" + track.getInfo().title + "**");
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack firstTrack = playlist.getTracks().get(0);
                    firstTrack.setUserData(textChannel);
                    musicManager.scheduler.queue(firstTrack);
                    callback.accept("🔍 Tocando: **" + firstTrack.getInfo().title + "**");
                } else {
                    
                }
            }

            @Override
            public void noMatches() {
                callback.accept("❌ Nenhum resultado encontrado para: " + trackUrl);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                String errorMsg = "❌ Falha ao carregar: ";
                if (exception.severity == FriendlyException.Severity.COMMON) {
                    errorMsg += "Verifique o nome ou link e tente novamente";
                } else {
                    errorMsg += "Problema temporário. Tente novamente mais tarde";
                }
                callback.accept(errorMsg);
            }
        });
    }

    private String preprocessQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Por favor, forneça um nome de música ou URL válida");
        }

        query = query.trim();
        
        if (query.matches("^(https?://)?(www\\.)?(youtube\\.com|youtu\\.?be)/.+")) {
            return query;
        }
        
        if (query.startsWith("ytsearch:")) {
            return query.split(" ")[0];
        }
        
        return "ytsearch:" + query.replaceAll("[^a-zA-Z0-9\\s]", "")
                                 .trim();
    }

    private String cleanSearchQuery(String query) {
        return query.replace("ytsearch:", "")
                   .replace("official audio", "")
                   .trim();
    }

    private void handleTrackLoaded(GuildMusicManager musicManager, AudioTrack track, Consumer<String> callback) {
        if (musicManager.player.getPlayingTrack() == null) {
            musicManager.scheduler.play(track);
            callback.accept("🎵 Tocando agora: **" + track.getInfo().title + "**");
        } else {
            musicManager.scheduler.queue(track);
            callback.accept("📥 Adicionado à fila: **" + track.getInfo().title + "** (Posição: " + 
                          musicManager.scheduler.getQueue().size() + ")");
        }
    }
    

    private void handlePlaylistLoaded(GuildMusicManager musicManager, AudioPlaylist playlist, Consumer<String> callback) {
        if (playlist.getTracks().isEmpty()) {
            callback.accept("❌ A playlist está vazia");
            return;
        }

        if (playlist.isSearchResult()) {
            AudioTrack firstTrack = playlist.getTracks().get(0);
            musicManager.scheduler.queue(firstTrack);
            callback.accept("🔍 Resultado da busca: **" + firstTrack.getInfo().title + "**");
        } else {
            for (AudioTrack track : playlist.getTracks()) {
                musicManager.scheduler.queue(track);
            }
            callback.accept("📚 Playlist adicionada: **" + playlist.getName() + "** (" + 
                          playlist.getTracks().size() + " músicas)");
        }
    }

    private String getFriendlyErrorMessage(FriendlyException exception) {
        if (exception.getMessage().contains("PLAYER_SCRIPT")) {
            return "Problema temporário com o YouTube. Tente novamente ou use um link direto.";
        } else if (exception.getMessage().contains("429")) {
            return "Muitas requisições. Por favor, espere um momento antes de tentar novamente.";
        }
        return exception.getMessage();
    }

    public void skipTrack(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.scheduler.nextTrack();
    }

    public void pause(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.player.setPaused(true);
        musicManager.notify("⏸️ Pausado");
    }

    public void resume(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.player.setPaused(false);
        musicManager.notify("▶️ Retomado");
    }

    public void stop(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.scheduler.clearQueue();
        musicManager.player.stopTrack();
        musicManager.notify("⏹️ Parado e fila limpa");
    }

    public String getNowPlaying(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        return musicManager.scheduler.getCurrentTrackInfo();
    }

    public String getQueueStatus(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        return musicManager.scheduler.getQueueStatus();
    }
}