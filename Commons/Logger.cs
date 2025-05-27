using Discord;

namespace Commons;

public class Logger {
	public Task Log(LogMessage msg) {
		Console.Write($"{DateTime.Now:HH:mm:ss}  ");
		Console.Write($"{msg.Source,-16}");
		
		Console.Write("[");
		switch (msg.Severity) {
			default:
			case LogSeverity.Critical:
				Console.BackgroundColor = ConsoleColor.DarkRed;
				Console.ForegroundColor = ConsoleColor.White;
				Console.Write("CRT");
				break;
			case LogSeverity.Error:
				Console.BackgroundColor = ConsoleColor.Black;
				Console.ForegroundColor = ConsoleColor.Red;
				Console.Write("ERR");
				break;
			case LogSeverity.Warning:
				Console.BackgroundColor = ConsoleColor.Black;
				Console.ForegroundColor = ConsoleColor.Yellow;
				Console.Write("WRN");
				break;
			case LogSeverity.Info:
				Console.BackgroundColor = ConsoleColor.Black;
				Console.ForegroundColor = ConsoleColor.Green;
				Console.Write("INF");
				break;
			case LogSeverity.Verbose:
				Console.BackgroundColor = ConsoleColor.Gray;
				Console.ForegroundColor = ConsoleColor.White;
				Console.Write("VRB");
				break;
			case LogSeverity.Debug:
				Console.BackgroundColor = ConsoleColor.Gray;
				Console.ForegroundColor = ConsoleColor.Black;
				Console.Write("DBG");
				break;
		}
		Console.BackgroundColor = ConsoleColor.Black;
		Console.ForegroundColor = ConsoleColor.Gray;
		Console.Write("]  ");
		
		
		Console.WriteLine(msg.Message);
		return Task.CompletedTask;
	}

	public Task Verbose(string src, string msg) {
		return Log(new LogMessage(LogSeverity.Verbose, src, msg));
	}
	public Task Debug(string src, string msg) {
		return Log(new LogMessage(LogSeverity.Debug, src, msg));
	}
	public Task Info(string src, string msg) {
		return Log(new LogMessage(LogSeverity.Info, src, msg));
	}
	public Task Warn(string src, string msg) {
		return Log(new LogMessage(LogSeverity.Warning, src, msg));
	}
	public Task Error(string src, string msg) {
		return Log(new LogMessage(LogSeverity.Error, src, msg));
	}
	public Task Critical(string src, string msg) {
		return Log(new LogMessage(LogSeverity.Critical, src, msg));
	}
}