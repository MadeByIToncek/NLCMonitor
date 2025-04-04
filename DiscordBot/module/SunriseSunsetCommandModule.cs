using Discord;
using Discord.WebSocket;
using DiscordBot.interfaces;
using DiscordBot.timers;

namespace DiscordBot.module;

public class SunriseSunsetCommandModule : IModule{
    public string Id() {
        return "sunrise-sunset";
    }
    public bool InstallGlobally() {
        return true;
    }
    public SlashCommandProperties BuildCommand() {
        SlashCommandOptionBuilder[] options = [
            new SlashCommandOptionBuilder()
                .WithName("latitude")
                .WithDescription("Severní šířka požadovaného místa")
                .WithRequired(true)
                .WithType(ApplicationCommandOptionType.Number),
            new SlashCommandOptionBuilder()
                .WithName("longitude")
                .WithDescription("Východní délka požadovaného místa")
                .WithRequired(true)
                .WithType(ApplicationCommandOptionType.Number)
        ];

        return new SlashCommandBuilder()
            .WithName(Id())
            .WithDescription("Vygenerovat časy východu a západu Slunce a Měsíce pro požadovanou lokaci.")
            .AddOptions(options)
            .Build();
    }
    public async Task Execute(SocketSlashCommand command) {
        await command.DeferAsync();
        double latitude = 0, longitude = 0;
        foreach (SocketSlashCommandDataOption o in command.Data.Options) {
            if (o.Name == "latitude") {
                latitude = (double)o.Value;
            }else if (o.Name == "longitude") {
                longitude = (double)o.Value;
            }
        }
        Embed e = await SunriseSunsetTimer.GenerateSunriseSunsetEmbed(latitude, longitude);
        await command.DeleteOriginalResponseAsync();
        await command.FollowupAsync(embed: e, ephemeral: true);
    }
}