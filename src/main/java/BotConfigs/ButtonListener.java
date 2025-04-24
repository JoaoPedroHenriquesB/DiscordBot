package BotConfigs;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ButtonListener extends ListenerAdapter {
    private final MusicManager musicManager;

    public ButtonListener(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        String guildId = event.getGuild().getId();

        switch (buttonId) {
            case "skip":
                musicManager.skipTrack(event.getGuild());
                event.reply("⏭️ Música pulada!").setEphemeral(true).queue();
                break;
                
            case "pause":
                musicManager.pause(event.getGuild());
                event.reply("⏸️ Música pausada!").setEphemeral(true).queue();
                break;
                
            case "resume":
                musicManager.resume(event.getGuild());
                event.reply("▶️ Música retomada!").setEphemeral(true).queue();
                break;
                
            case "stop":
                musicManager.stop(event.getGuild());
                event.reply("⏹️ Playback parado!").setEphemeral(true).queue();
                break;
                
            default:
                event.reply("❌ Ação desconhecida").setEphemeral(true).queue();
        }
    }
}