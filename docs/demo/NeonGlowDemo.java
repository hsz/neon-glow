import java.time.Duration;
import java.util.List;

/**
 * Keep your theme. Add some light.
 * A small playlist demo for Neon Glow screenshots and screen recordings.
 */
public class NeonGlowDemo {
    private static final String PLAYLIST_NAME = "Midnight Drive";

    public record Track(String title, String artist, int durationSeconds) {
        public String displayName() {
            return title + " — " + artist;
        }
    }

    public static void main(String[] args) {
        var tracks = List.of(
                new Track("After Dark", "Neon Avenue", 180),
                new Track("Electric Dreams", "Night Arcade", 215),
                new Track("Neon Skyline", "Midnight Signal", 240)
        );

        System.out.println("Now playing: " + PLAYLIST_NAME);

        // Find the tracks that keep the night going.
        tracks.stream()
                .filter(track -> track.durationSeconds() >= 200)
                .map(Track::displayName)
                .forEach(title -> System.out.println("  ▶ " + title));

        int totalSeconds = tracks.stream()
                .mapToInt(Track::durationSeconds)
                .sum();
        var duration = Duration.ofSeconds(totalSeconds);

        System.out.printf("%d tracks · %d:%02d minutes%n",
                tracks.size(), duration.toMinutes(), duration.toSecondsPart());
    }
}
