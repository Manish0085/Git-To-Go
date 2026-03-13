import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class FullTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting FullTest...");
        String url = "jdbc:postgresql://localhost:5432/git2godb";
        try (Connection conn = DriverManager.getConnection(url, "postgres", "postgres");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, encrypted_env_vars FROM deployments ORDER BY created_at DESC LIMIT 2;")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String env = rs.getString("encrypted_env_vars");
                System.out.println("ID: " + id);
                if (env == null || env.isEmpty()) {
                    System.out.println("ENV: [EMPTY OR NULL]");
                } else {
                    try {
                        String dec = EnvEncryptor.decrypt(env);
                        System.out.println("DECRYPTED: [" + dec + "]");
                    } catch (Exception e) {
                        System.out.println("DECRYPT_ERROR: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
