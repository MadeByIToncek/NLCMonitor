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

namespace DiscordBot
{
    public class Program {
	    public static readonly Logger logger = new Logger();
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
		    DiscordClient.Log += logger.Log;

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
					await logger.Info("DiscordClientReady",$"Installing {module.Id()} onto guild {g.Name}; IsGlobal? {module.InstallGlobally()} IsTestGuild? {g.Id == TestServer}");
					await g.CreateApplicationCommandAsync(module.BuildCommand());
				}
			}
			
			Modules.ForEach(async x => {
				await x.SetupListeners(DiscordClient);
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

			await logger.Info("SlashCommandHandler",$"Executing {module?.Id()}");
			if(module == null) return;
			await module.Execute(command);
		}

	}
}
