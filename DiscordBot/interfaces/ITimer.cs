using FluentScheduler;

namespace DiscordBot.interfaces;

public interface ITimer {
    public string Id();
    public void SetupTimer(Registry registry);
}