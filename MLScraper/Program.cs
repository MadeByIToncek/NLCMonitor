using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Processing;

namespace MLScraper {
	internal class Program {
		static void Main(string[] args)
		{
			DownloadAndSave("https://www.iap-kborn.de/fileadmin/user_upload/MAIN-abteilung/radar/Radars/OswinVHF/Plots/OSWIN_Mesosphere_4hour.png", "oswin", new Rectangle(557,119,434,797));
			DownloadAndSave("https://www.iap-kborn.de/fileadmin/user_upload/MAIN-abteilung/radar/Radars/MAARSY/Plots/maarsy_dbs_meso_eta.png", "maarsy", new Rectangle(1479,82,258, 877));
		}

		private static void DownloadAndSave(string url, string folder, Rectangle crop)
		{  
			Image img;

			using (var client = new HttpClient()) {
				using (var s = client.GetStreamAsync(url))
				{
					img = Image.Load(s.Result);
				}
			}
			
			img.Mutate(x =>
			{
				x.Crop(crop);
			});

			if (!Directory.Exists(Environment.CurrentDirectory + "/" + folder))
			{
				Directory.CreateDirectory(Environment.CurrentDirectory + "/" + folder);
			}

			img.Save(Environment.CurrentDirectory + "/" + folder + "/" + CurrentEpochSecond() + ".png");
		}

		private static long CurrentEpochSecond()
		{
			TimeSpan t = DateTime.UtcNow - new DateTime(1970, 1, 1);
			return (long)t.TotalSeconds;
		}
	}
}
