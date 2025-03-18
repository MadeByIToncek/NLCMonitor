using System.Diagnostics.CodeAnalysis;
using System.Net.Http.Headers;
using System.Net.Mime;
using System.Text.Json;
using System.Text.Json.Serialization.Metadata;
using SixLabors.ImageSharp;

namespace DiscordBot;

public class BlueSkyRuntime {
    private readonly Creds _creds;
    private string? _accessJwt;
    private string? _refreshJwt;
    private string? _handle;
    private string? _did;

    public BlueSkyRuntime() {
        const string creds = "bluesky.login";
        if (!File.Exists(creds)) {
            CreateCredsFile(creds).Wait();
        }

        Creds? c = ReadCredsFile(creds).Result;
        _creds = c ?? throw new Exception("Unable to parse creds file");
    }

    public async Task Login() {
        using HttpClient client = new();
        
        HttpContent content = new StringContent(JsonSerializer.Serialize(new BSkyLoginRequest(_creds.user, _creds.password)));
        content.Headers.ContentType = new("application/json");
        
        using HttpResponseMessage res = await client.PostAsync("https://bsky.social/xrpc/com.atproto.server.createSession",content);
        BSkyLoginResponse? resp = await JsonSerializer.DeserializeAsync<BSkyLoginResponse>(await res.Content.ReadAsStreamAsync());
        if (resp != null) {
            _accessJwt = resp.accessJwt;
            _refreshJwt = resp.refreshJwt;
            _handle = resp.handle;
            _did = resp.did;
            Console.WriteLine($"Logged into BlueSky as @{_handle}");
        }
    }

    public async Task RefreshSession() {
        using HttpClient client = new();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _refreshJwt);
        
        HttpContent content = new StringContent("");
        
        using HttpResponseMessage res = await client.PostAsync("https://bsky.social/xrpc/com.atproto.server.refreshSession",content);
        BSkyLoginResponse? resp = await JsonSerializer.DeserializeAsync<BSkyLoginResponse>(await res.Content.ReadAsStreamAsync());
        if (resp != null) {
            _accessJwt = resp.accessJwt;
            _refreshJwt = resp.refreshJwt;
            _handle = resp.handle;
            _did = resp.did;
        }
    }

    public async Task PostMessage(string message) {
        await RefreshSession();
        
        using HttpClient client = new();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _accessJwt);
        
        HttpContent content = new StringContent(JsonSerializer.Serialize(new BSkyMessageRequest(new BSkyMessageRequest.Record(message), repo: _did?? "")));
        content.Headers.ContentType = new("application/json");
        using var x = await client.PostAsync("https://bsky.social/xrpc/com.atproto.repo.createRecord", content);
        Console.WriteLine(await x.Content.ReadAsStringAsync());
    }
    
    private static async Task<Creds?> ReadCredsFile(string creds) {
        await using FileStream fs = File.Open(creds, FileMode.OpenOrCreate);
        using StreamReader sr = new(fs);
        string json = await sr.ReadToEndAsync();
        return JsonSerializer.Deserialize<Creds>(json);
    }

    private static async Task CreateCredsFile(string creds) {
        await using FileStream fs = File.Open(creds, FileMode.OpenOrCreate, FileAccess.Write, FileShare.Read);
        await using StreamWriter sw = new(fs);
        await sw.WriteAsync(JsonSerializer.Serialize(new Creds()));
    }

    [SuppressMessage("ReSharper", "InconsistentNaming")]
    private class Creds {
        public string user { get; set; } = "";
        public string password { get; set; } = "";
    }
}

[SuppressMessage("ReSharper", "InconsistentNaming")]
public class BSkyMessageRequest(BSkyMessageRequest.Record record,string collection = "app.bsky.feed.post",string repo = "nlc.itoncek.space") {
    public string repo { get; set; } = repo;
    public string collection { get; set; } = collection;
    public Record record { get; set; } = record;

    public class Record(string message) {
        public string text { get; set; } = message;
        public string createdAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ssZ");
    }
}

[SuppressMessage("ReSharper", "InconsistentNaming")]
public class BSkyLoginRequest(string identifier, string password) {
    public string identifier { get; set; } = identifier;
    public string password { get; set; } = password;
}
[SuppressMessage("ReSharper", "InconsistentNaming")]
public class BSkyLoginResponse {
    public string? accessJwt { get; set; }
    public string? refreshJwt { get; set; }
    public string? handle { get; set; }
    public string? did { get; set; }
}