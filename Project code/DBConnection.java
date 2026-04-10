import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Manages the MySQL connection for the F1 Tracker.
 * Reads credentials from db.properties (never hard-code passwords).
 */
public class DBConnection
{

    private static Connection connection = null;

    // -------------------------------------------------------
    // Connect
    // -------------------------------------------------------
    public static Connection getConnection() throws SQLException
    {
        if (connection != null && !connection.isClosed()) 
        {
            return connection;
        }

        Properties props = loadProperties();
        String url  = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String pass = props.getProperty("db.password");

        if (url == null || user == null) {
            throw new SQLException("Missing db.url or db.user in db.properties");
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException cnfe) {
            throw new SQLException("MySQL JDBC driver not found. Add mysql-connector-j to your classpath.", cnfe);
        }

        connection = DriverManager.getConnection(url, user, pass);
        System.out.println("[DB] Connected");
        return connection;
    }

    // -------------------------------------------------------
    // Disconnect
    // -------------------------------------------------------
    public static void closeConnection() 
    {
        if (connection != null) 
        {
            try {
                connection.close();
                System.out.println("[DB] Connection closed.");
            } catch (SQLException sqle) {
                System.err.println("[DB] Error closing connection: " + sqle.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    // -------------------------------------------------------
    // Create schema from SQL file
    // -------------------------------------------------------
    public static void createSchema() throws SQLException 
    {
        Connection conn = getConnection();
        
        java.io.File sqlFile = findFile("schema.sql");
        if (sqlFile == null) {
            throw new SQLException(
                "schema.sql not found. Place schema.sql in your BlueJ project folder.\n"
              + "Working directory: " + System.getProperty("user.dir"));
        }

        String sql;
        try (InputStream is = new java.io.FileInputStream(sqlFile)) 
        {
            sql = new String(is.readAllBytes());
        } catch (IOException ioe) {
            throw new SQLException("Failed to read schema.sql", ioe);
        }

        String[] statements = sql.split(";");
        try (Statement stmt = conn.createStatement()) 
        {
            for (String s : statements) 
            {
                String trimmed = s.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) 
                {
                    stmt.execute(trimmed);
                }
            }
        }
        System.out.println("[DB] Schema created from: " + sqlFile.getAbsolutePath());
    }

    // Search common locations — BlueJ's working dir varies by setup
    private static java.io.File findFile(String filename) 
    {
        String sep = java.io.File.separator;
        String[] candidates = {
            filename,
            System.getProperty("user.dir") + sep + filename,
            System.getProperty("user.home") + sep + filename,
            ".." + sep + filename
        };
        for (String path : candidates) {
            java.io.File f = new java.io.File(path);
            if (f.exists() && f.isFile()) 
            return f;
        }
        return null;
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------
    private static Properties loadProperties() throws SQLException 
    {
        Properties props = new Properties();
        try (InputStream is = new java.io.FileInputStream("db.properties")) 
        {
            props.load(is);
        } catch (java.io.FileNotFoundException fnfe) 
        {
            throw new SQLException(
                "db.properties not found. Make sure db.properties is in your BlueJ project folder.");
        } catch (IOException ioe) 
        {
            throw new SQLException("Failed to load db.properties", ioe);
        }
        return props;
    }
}