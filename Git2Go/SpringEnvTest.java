import org.springframework.core.env.StandardEnvironment;
public class SpringEnvTest {
    public static void main(String[] args) {
        StandardEnvironment env = new StandardEnvironment();
        try {
            System.out.println("RESOLVED: [" + env.resolveRequiredPlaceholders("${NEWS_API_KEY}") + "]");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
