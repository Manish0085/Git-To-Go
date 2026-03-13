import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbTest {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/git2godb";
        try (Connection conn = DriverManager.getConnection(url, "postgres", "postgres");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, encrypted_env_vars FROM deployments ORDER BY created_at DESC LIMIT 5;")) {
            while (rs.next()) {
                System.out.println("ID: " + rs.getString("id") + " | ENV: " + rs.getString("encrypted_env_vars"));
            }
        }
    }
}
