namespace DiscordBot.utils;

public class NLCDetectorUtil
{
	public NLCDetectorUtil()
	{
		// Create single instance of sample data from first line of dataset for model input
		var imageBytes = File.ReadAllBytes(@"T:\NLC-Data\maarsy-analysis\detected\1742457601230.png");
		V1_maarsy.ModelInput sampleData = new V1_maarsy.ModelInput() {
			ImageSource = imageBytes,
		};

		// Make a single prediction on the sample data and print results.
		var sortedScoresWithLabel = V1_maarsy.PredictAllLabels(sampleData);
		Console.WriteLine($"{"Class",-40}{"Score",-20}");
		Console.WriteLine($"{"-----",-40}{"-----",-20}");

		foreach (var score in sortedScoresWithLabel) {
			Console.WriteLine($"{score.Key,-40}{score.Value,-20}");
		}
	}
}