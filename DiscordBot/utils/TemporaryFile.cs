namespace DiscordBot.utils
{
	public sealed class TemporaryFile : IDisposable {
		public TemporaryFile(string extension) : this(Path.GetTempPath(), extension) { }
		public TemporaryFile() : this(Path.GetTempPath(), "") { }
		public TemporaryFile(string directory, string extension) {
			Create(Path.Combine(directory, Path.GetRandomFileName() + extension));
		}

		~TemporaryFile() {
			Delete();
		}

		public void Dispose() {
			Delete();
			GC.SuppressFinalize(this);
		}

		public string? FilePath { get; private set; }

		private void Create(string path) {
			FilePath = path;
			using (File.Create(FilePath,0,FileOptions.DeleteOnClose)) { }
		}

		private void Delete() {
			if (FilePath == null) return;
			try {
				File.Delete(FilePath);
			} catch (Exception) {
				// ignored
			}

			FilePath = null;
		}
	}
}
