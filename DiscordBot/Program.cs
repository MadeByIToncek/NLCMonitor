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
    public class Program
    {
	    private const long TestServer = 709697349064196178L;
	    private static BlueSkyRuntime? Bluesky;
	    public static readonly DiscordSocketClient DiscordClient = new();

	    private static readonly List<IModule> Modules = [
			new SolarFlareModule()
	    ];

	    private static readonly List<ITimer> Timers = [
			//new WeatherForecastTimer(),
			new SunriseSunsetTimer()
	    ];

		public static async Task Main(string[] args)
        {
	        DiscordClient.Log += Log;

	        //  You can assign your bot token to a string, and pass that in to connect.
	        //  This is, however, insecure, particularly if you plan to have your code hosted in a public repository.
	        //var token = "token";

	        // Some alternative options would be to keep your token in an Environment Variable or a standalone file.
	        // var token = Environment.GetEnvironmentVariable("NameOfYourEnvironmentVariable");
	        var token = File.ReadAllText("discord.token");
	        // var token = JsonConvert.DeserializeObject<AConfigurationClass>(File.ReadAllText("config.json")).Token;
	        
	        DiscordClient.Ready += DiscordClientReady;
			DiscordClient.SlashCommandExecuted += SlashCommandHandler;
			
			await DiscordClient.LoginAsync(TokenType.Bot, token);
	        await DiscordClient.StartAsync();
	        
	        AppDomain.CurrentDomain.ProcessExit += (_,_) => new Func<Task>(async () => { await OnProcessExit(); }).Invoke(); 
	        // Block this task until the program is closed.
	        await Task.Delay(-1);
		}

		private static async Task OnProcessExit() {
			await DiscordClient.StopAsync();
		}

		private static async Task DiscordClientReady() {
			// Let's build a guild command! We're going to need a guild so lets just put that in a variable.
			await DiscordClient.SetCustomStatusAsync("Sleduji jak letí mraky (v3 BETA)");
			
			foreach (SocketGuild g in DiscordClient.Guilds) {
				await g.DeleteApplicationCommandsAsync();
				foreach (IModule module in Modules.Where(module => module.InstallGlobally() || g.Id == TestServer)) {
					await g.CreateApplicationCommandAsync(module.BuildCommand());
				}
			}
			
			Bluesky = new BlueSkyRuntime();
			await Bluesky.Login();

			Registry registry = new();
			foreach (ITimer timer in Timers) {
				timer.SetupTimer(registry);
			}
			JobManager.Initialize(registry);
		}

		private static Task SlashCommandHandler(SocketSlashCommand command) {
			IModule? module = Modules.Find(x => x.Id() == command.Data.Name);

			module?.Execute(command);
			return Task.CompletedTask;
		}


		private static Task Log(LogMessage msg) {
	        Console.WriteLine(msg.ToString());
	        return Task.CompletedTask;
        }

	}
}
