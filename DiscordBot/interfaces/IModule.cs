using Discord;
using Discord.WebSocket;

namespace DiscordBot.interfaces;

public interface IModule
{
	string Id();
	bool InstallGlobally();
	SlashCommandProperties BuildCommand();
	Task Execute(SocketSlashCommand command);
	public async Task SetupListeners(DiscordSocketClient client) {
		Console.WriteLine($"{Id()} has no listeners to setup!");
	}
}