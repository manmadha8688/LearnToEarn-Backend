import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** One-off: run after mvn compile — prints BCrypt hash for a password. */
public class HashPassword {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java HashPassword <password>");
            System.exit(1);
        }
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
