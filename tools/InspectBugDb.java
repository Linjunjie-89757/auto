import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class InspectBugDb {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:D:/CodeProject/auto/server/data/auto-platform;MODE=MySQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            System.out.println("== Recent bugs ==");
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, bug_no, workspace_id, title, description from bug order by id desc limit 10");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    System.out.println("bug id=" + rs.getLong("id")
                            + ", bug_no=" + rs.getString("bug_no")
                            + ", workspace_id=" + rs.getLong("workspace_id")
                            + ", title=" + rs.getString("title"));
                    String description = rs.getString("description");
                    System.out.println("description=" + description);
                    System.out.println("--");
                }
            }

            System.out.println("== Recent bug attachments ==");
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, bug_id, file_name, content_type, stored_path, created_at from bug_attachment order by id desc limit 20");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    System.out.println("attachment id=" + rs.getLong("id")
                            + ", bug_id=" + rs.getLong("bug_id")
                            + ", file_name=" + rs.getString("file_name")
                            + ", content_type=" + rs.getString("content_type")
                            + ", stored_path=" + rs.getString("stored_path")
                            + ", created_at=" + rs.getString("created_at"));
                }
            }
        }
    }
}
