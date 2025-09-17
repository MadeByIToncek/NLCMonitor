using Discord;
using Discord.WebSocket;
using DiscordBot.interfaces;
using DiscordBot.utils;
using Newtonsoft.Json.Linq;
using SixLabors.Fonts;
using SixLabors.Fonts.Tables.AdvancedTypographic;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Drawing.Processing;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using System.Diagnostics.CodeAnalysis;
using System.Text.Json;
using Color = SixLabors.ImageSharp.Color;
using Image = SixLabors.ImageSharp.Image;

namespace DiscordBot.module
{
	internal class SolarFlareModule : IModule
	{
		public string Id()
		{
			return "flare";
		}

		public bool InstallGlobally()
		{
			return true;
		}

		public SlashCommandProperties BuildCommand()
		{
			SlashCommandBuilder b = new SlashCommandBuilder
			{
				Name = Id(),
				Description = "Shows flares that happened in the past 6 hours."
			};

			SlashCommandOptionBuilder ob = new SlashCommandOptionBuilder {
				Name = "history",
				Description = "Select historical window duration",
				IsRequired = false,
				IsAutocomplete = false,
				Type = ApplicationCommandOptionType.String
			};
			ob.AddChoice("6 Hours", "6hour");
			ob.AddChoice("1 Day", "1day");
			ob.AddChoice("3 Days", "3day");
			ob.AddChoice("7 Days", "7day");

			b.AddOption(ob);

			return b.Build();
		}

		public async Task Execute(SocketSlashCommand command)
		{
			await command.DeferAsync();
			using GraphUtils u = await GraphUtils.Init();
			using var tempFile = new TemporaryFile();

			const string primary = "https://services.swpc.noaa.gov/json/goes/primary/xrays-";
			const string secondary = "https://services.swpc.noaa.gov/json/goes/secondary/xrays-";

			var option = command.Data.Options.FirstOrDefault(x => x?.Name == "history", null);
			double flareThreshold = (option?.Value) switch {
				"3day" => -4.6,
				"7day" => -4.25,
				_ => -5,
			};
			var suffix = (option?.Value) switch
			{
				"1day" => "1-day",
				"3day" => "3-day",
				"7day" => "7-day",
				_ => "6-hour",
			};

			var primaryUrl = primary + suffix + ".json";
			var secondaryUrl = secondary + suffix + ".json";

			await command.ModifyOriginalResponseAsync(x => x.Content = "Downloading GOES data");
			using var client = new HttpClient();
			using var s1 = client.GetStringAsync(primaryUrl);
			using var s2 = client.GetStringAsync(secondaryUrl);

			GoesData[]? primaryData = JsonSerializer.Deserialize<GoesData[]>(s1.Result)?.Where(x=>x.energy == "0.1-0.8nm").ToArray();
			GoesData[]? secondaryData = JsonSerializer.Deserialize<GoesData[]>(s2.Result)?.Where(x => x.energy == "0.1-0.8nm").ToArray();

			List<(double f, DateTime date)> data = [];

			if (primaryData == null)
			{
				await command.ModifyOriginalResponseAsync(x => x.Content = "Unable to process primary data!");
				return;
			}

			foreach (var pri in primaryData)
			{
				var sec = secondaryData?.FirstOrDefault(x => x?.time_tag == pri.time_tag, null);

				data.Add((sec == null ? pri.flux : Math.Max(pri.flux, sec.flux), pri.time_tag));
			}

			await command.ModifyOriginalResponseAsync(x => x.Content = "Generating plot");

			using Image img = new Image<Rgba32>(3840, 2160);

			int height = img.Height - GraphUtils.borderGap * 2;
			double xScale = ((double)img.Width - (2 * GraphUtils.borderGap)) / (data.Count - 1);

			await DrawClassLine(-6, "C", img, GraphUtils.borderGap, height);
			await DrawClassLine(-5, "M", img, GraphUtils.borderGap, height);
			await DrawClassLine(-4, "X", img, GraphUtils.borderGap, height);
			await DrawClassLine(-3, "X10", img, GraphUtils.borderGap, height);

			u.DrawGraphAxis(img);

			await command.ModifyOriginalResponseAsync(x => x.Content = "Plotting data");

			PlotGraphData(data.Select(x=>x.f).Select(Math.Log10).Select(Remap).ToList(), img, GraphUtils.borderGap, height, xScale);

			await command.ModifyOriginalResponseAsync(x => x.Content = "Finding solar flares");

			double[] hackedValues = new double[data.Count];
			(double f, DateTime date) tempVal = data[0];
			var replacing = false;
			for (var i = 0; i < data.Count; i++)
			{
				var v = data[i];

				switch (v.f)
				{
					case 0 when !replacing:
						replacing = true;
						tempVal = data[i - 1];
						v = tempVal;
						hackedValues[i] = v.f;
						continue;
					case 0:
						v = tempVal;
						hackedValues[i] = v.f;
						continue;
					default:
						if (replacing) {
							replacing = false;
						}
						hackedValues[i] = v.f;
						continue;
				}
			}

			// todo)) test
			var result = ZScore.StartAlgo(hackedValues.ToList(), 5, 6, .05);

			int previous = 0;
			bool running = false;
			Dictionary<double,int> vals = [];

			for (int i = 0; i < result.Signals.Count; i++)
			{
				int val = result.Signals[i];
				if (running)
				{
					vals[data[i].f] = i;
				}

				if (val == 1 && previous == 0)
				{
					running = true;
				}
				else if (((val == 0 && false) || ((val == 1) && (i == (result.Signals.Count - 1)))) &&
				         vals.Count != 0)
				{
					running = false;
					double max = vals.Keys.Max();
					double logmax = Math.Log10(max);
					String flareClass;

					double floor;
					if (logmax >= -4)
					{
						flareClass = "X";
						floor = -4;
					}
					else if (logmax >= -5)
					{
						flareClass = "M";
						floor = -5;
					}
					else if (logmax >= -6)
					{
						flareClass = "C";
						floor = -6;
					}
					else if (logmax >= -7)
					{
						flareClass = "B";
						floor = -7;
					}
					else
					{
						flareClass = "A";
						floor = -8;
					}

					if (logmax >= flareThreshold)
					{
						flareClass = flareClass + $"{max * Math.Pow(10, floor)}".Substring(0, "0.00".Length);
						await DrawFlareLine(vals[max], flareClass, img, GraphUtils.borderGap, xScale);
						vals.Clear();
					}
				}
			}

			await img.SaveAsync("test.png");

			await command.FollowupWithFileAsync("test.png");

			await command.DeleteOriginalResponseAsync();
		}

		private int _previousX = 0;
		private int _previousLength = 0;
		private int _previousOffset = 0;

		private async Task DrawFlareLine(int i, string flareClass, Image img, int borderGap, double xScale)
		{
			if (!File.Exists("vcr.ttf"))
			{
				using var client = new HttpClient();
				using var s = client.GetStreamAsync("https://cdn.itoncek.space/fonts/VCR_OSD_MONO-Regular.ttf");
				await using var fs = new FileStream("vcr.ttf", FileMode.OpenOrCreate);
				await s.Result.CopyToAsync(fs);
			}

			FontCollection collection = new();
			var family = collection.Add("vcr.ttf");
			var font = family.CreateFont(80, FontStyle.Italic);
			var (_, _, ww, hh) = TextMeasurer.MeasureSize(flareClass, new TextOptions(font));

			var xx = (int)(i * xScale + borderGap);
			if (_previousX + _previousLength > xx)
			{
				_previousOffset += (int)(hh + 20);
			}
			else
			{
				_previousOffset = 0;
			}

			img.Mutate(x =>
			{
				x.DrawLine(new Color(new Rgba32(255, 255, 255, 80)), 8f, [
					new PointF(xx, borderGap + 8),
					new PointF(xx, img.Height - borderGap - 8 - _previousOffset)
				]);
				x.DrawText(flareClass, font, new Color(new Rgba32(255, 255, 255, 80)), new PointF(xx + 20, img.Height - borderGap - 15 - _previousOffset));
			});
			_previousX = xx;
			_previousLength = (int)ww;
		}

		private void PlotGraphData(List<double> data, Image img, int borderGap, int height, double xScale)
		{
			List<PointF?> graph = [];
			List<Color> color = [];
			for (var i = 0; i < data.Count; i++)
			{
				if (double.IsInfinity(data[i]))
				{
					graph.Add(null);
					color.Add(Color.Gray);
				}
				else
				{
					var x1 = (float)(i * xScale + borderGap);
					var y1 = (float)(height - (height * data[i])) + borderGap;
					graph.Add(new PointF(x1, y1));
					color.Add(DetermineColor(data[i]));
				}
			}
			img.Mutate(x =>
			{
				for (var i = 0; i < graph.Count - 1; i++)
				{
					var p1 = graph[i];
					var p2 = graph[i+1];

					if(p1 == null || p2 == null) continue;

					var p1N = (PointF)p1;
					var p2N = (PointF)p2;

					PointF[] line =
					[
						p1N,
						p2N
					];
					x.DrawLine(color[i], 8, line);
				}
			});
		}

		private Color DetermineColor(double x)
		{
			return Unmap(x) switch
			{
				>= -2 => new Color(new Rgba32(0, 0, 0)),
				>= -3 => new Color(new Rgba32(87, 0, 0)),
				>= -4 => new Color(new Rgba32(255, 0, 0)),
				>= -5 => new Color(new Rgba32(255, 140, 0)),
				>= -6 => new Color(new Rgba32(255, 225, 0)),
				>= -7 => new Color(new Rgba32(79, 218, 0)),
				_ => new Color(new Rgba32(26, 62, 0))
			};
		}

		private async Task DrawClassLine(int strength, string flareClass, Image img, int borderGap, int height)
		{
			var y = (float)(height - height * Remap(strength)) + borderGap;

			PointF[] line =
			[
				new(borderGap, y),
				new(img.Width - borderGap, y)
			];

			if (!File.Exists("vcr.ttf")) {
				using var client = new HttpClient();
				using var s = client.GetStreamAsync("https://cdn.itoncek.space/fonts/VCR_OSD_MONO-Regular.ttf");
				await using var fs = new FileStream("vcr.ttf", FileMode.OpenOrCreate);
				await s.Result.CopyToAsync(fs);
			}

			FontCollection collection = new();
			var family = collection.Add("vcr.ttf");
			var font = family.CreateFont(80, FontStyle.Italic);
			var (_, _, ww, hh) = TextMeasurer.MeasureSize(flareClass, new TextOptions(font));
			img.Mutate(x =>
			{
				x.DrawLine(new Color(new Argb32(255, 255, 255, 80)), 8, line); 
				
				var x1 = img.Width - borderGap - ww;
				var y1 = y + hh-40;
				x.DrawText(flareClass, font, Color.White, new PointF(x1,y1));
			});
		}

		private static double Remap(double x)
		{
			return (x + 8) / 6;
		}

		private static double Unmap(double x)
		{
			return (x * 6) - 8;
		}
	}

	[SuppressMessage("ReSharper", "InconsistentNaming")]
	public class GoesData
	{
		public DateTime time_tag { get; set; }
		public int satellite { get; set; }
		public double flux { get; set; }
		public double observed_flux { get; set; }
		public double electron_correction { get; set; }
		public bool electron_contamination { get; set; }
		public string? energy { get; set; }
	}
}