using Discord;
using Discord.WebSocket;
using DiscordBot.interfaces;
using DiscordBot.timers;

namespace DiscordBot;

internal class AdminModule : IModule {
    public string Id() {
        return "admin";
    }
    public bool InstallGlobally() {
        return false;
    }
    public SlashCommandProperties BuildCommand() {
        SlashCommandOptionBuilder[] options = [
            new SlashCommandOptionBuilder()
                .WithName("execute-timer")
                .WithDescription(":)")
                .WithType(ApplicationCommandOptionType.SubCommandGroup)
                .AddOption(new SlashCommandOptionBuilder()
                    .WithName("sunrise")
                    .WithDescription(":)")
                    .WithType(ApplicationCommandOptionType.SubCommand))
        ];

        return new SlashCommandBuilder()
            .WithName(Id())
            .WithDescription("Force run selected methods, not mean for public usage!")
            .WithDefaultPermission(false)
            .WithDefaultMemberPermissions(GuildPermission.Administrator)
            .AddOptions(options)
            .Build();
    }
    public async Task Execute(SocketSlashCommand command) {
        if (command.User.Id == 580098459802271744L) {
            var option = command.Data.Options.ToList().FirstOrDefault(_=>true,null);

            if (option != null) {
                Console.WriteLine($"Option {option.Name}");
                switch (option.Name) {
                    case "execute-timer":
                        var module = option.Options.ToList().FirstOrDefault(_=>true,null);
                        if (module != null) {
                            Console.WriteLine($"Module {module.Name}");
                            switch (module.Name) {
                                case "sunrise":
                                    SunriseSunsetTimer sst = (SunriseSunsetTimer)Program.Timers.First(x => x is SunriseSunsetTimer);
                                    await sst.Execute();
                                    break;
                            }
                        }
                        break;
                }
            }
            await command.RespondAsync(":)");
        }
        else {
            await command.RespondAsync("This action is author only!");
        }
    }
}