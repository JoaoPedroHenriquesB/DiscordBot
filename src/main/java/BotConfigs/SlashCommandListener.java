package BotConfigs;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public class SlashCommandListener extends ListenerAdapter {
    private final Services services;
    private static final String TEST_GUILD_ID = "0";

    public SlashCommandListener(MusicManager musicManager) {
        this.services = new Services(musicManager);
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Iniciando registro de comandos slash...");
        
        List<CommandData> commandList = services.getCommandList();
        
        registerGuildCommands(event, commandList);
        registerGlobalCommands(event, commandList);
    }

    private void registerGuildCommands(ReadyEvent event, List<CommandData> commandList) {
        Guild guild = event.getJDA().getGuildById(TEST_GUILD_ID);
        if (guild != null) {
            guild.updateCommands().addCommands(commandList)
                .queue(
                    success -> System.out.println("Comandos registrados no servidor "+guild.getName()),
                    error -> System.err.println("Erro no servidor: " + error.getMessage())
                );
        } else {
            System.err.println("Servidor de teste não encontrado! ID: " + TEST_GUILD_ID);
        }
    }

    private void registerGlobalCommands(ReadyEvent event, List<CommandData> commandList) {
        event.getJDA().updateCommands().addCommands(commandList)
            .queue(
                success -> System.out.println("Comandos globais enviados para registro"),
                error -> System.err.println("Erro global: " + error.getMessage())
            );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        services.executeCommand(event);
    }
}