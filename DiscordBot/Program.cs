using System.Runtime.CompilerServices;
using Commons;
using Discord;
using Discord.Net;
using Discord.WebSocket;
using DiscordBot.interfaces;
using DiscordBot.module;
using DiscordBot.timers;
using FluentScheduler;
using Newtonsoft.Json;
using ITimer = DiscordBot.interfaces.ITimer;
#pragma warning disable CS4014 // Because this call is not awaited, execution of the current method continues before the call is completed

namespace DiscordBot
{
    public static class Program {
	    private static readonly Logger Logger = new();
	    private const long TestServer = 709697349064196178L;
	    //private static BlueSkyRuntime? Bluesky;
	    public static readonly DiscordSocketClient DiscordClient = new();

	    private static readonly List<IModule> Modules = [
			new SolarFlareModule(),
			new SunriseSunsetCommandModule(),
			new AdminModule(),
			#if DEBUG
			new WeatherForecastModule(),
			#endif
	    ];

	    public static readonly List<ITimer> Timers = [
			new SunriseSunsetTimer()
	    ];

	    public static async Task Main(string[] args) {
		    DiscordClient.Log += Logger.Log;

		    Directory.CreateDirectory("./data");

		    string token = Environment.GetEnvironmentVariable("DISCORD_TOKEN")??await File.ReadAllTextAsync(Environment.GetEnvironmentVariable("DISCORD_TOKEN_FILE")??"discord.token");

		    DiscordClient.Ready += DiscordClientReady;
		    DiscordClient.SlashCommandExecuted += SlashCommandHandler;

		    await DiscordClient.LoginAsync(TokenType.Bot, token);
		    await DiscordClient.StartAsync();
		    await DiscordClient.SetStatusAsync(UserStatus.Idle);
		    await DiscordClient.SetCustomStatusAsync("Načítání...");

		    AppDomain.CurrentDomain.ProcessExit +=
			    (_, _) => new Func<Task>(async () => { await OnProcessExit(); }).Invoke();
		    // Block this task until the program is closed.
		    await Task.Delay(-1);
	    }

	    private static async Task OnProcessExit() {
			await DiscordClient.StopAsync();
		}

		private static async Task DiscordClientReady() {
			// Let's build a guild command! We're going to need a guild so lets just put that in a variable.
			
			foreach (SocketGuild g in DiscordClient.Guilds) {
				await g.DeleteApplicationCommandsAsync();
				foreach (IModule module in Modules.Where(module => module.InstallGlobally() || g.Id == TestServer)) {
					await g.CreateApplicationCommandAsync(module.BuildCommand());
					Logger.Info("DiscordClientReady",$"Installing {module.Id()} onto guild {g.Name}; IsGlobal? {module.InstallGlobally()} IsTestGuild? {g.Id == TestServer}");
				}
			}
			
			Modules.ForEach(async void (x) => {
				try {
					await x.SetupListeners(DiscordClient);
				}
				catch (Exception e) {
					Logger.Error("moduleSetup", e.Message + "\n" + e.StackTrace);
				}
			});
			
			//Bluesky = new BlueSkyRuntime();
			//await Bluesky.Login();

			Registry registry = new();
			foreach (ITimer timer in Timers) {
				timer.SetupTimer(registry);
			}
			JobManager.Initialize(registry);

			await DiscordClient.SetStatusAsync(UserStatus.Online);
			await DiscordClient.SetCustomStatusAsync("Sleduji jak letí mraky (v3.1 BETA)");
		}

		private static async Task SlashCommandHandler(SocketSlashCommand command) {
			IModule? module = Modules.Find(x => x.Id() == command.Data.Name);

			if(module == null) return;
			Logger.Info("SlashCommandHandler",$"Executing {module.Id()}");
			await module.Execute(command);
			Logger.Info("SlashCommandHandler",$"Finished executing {module.Id()}");
		}

	}
}
