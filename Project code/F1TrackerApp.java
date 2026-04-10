import java.sql.*;
import java.util.Scanner;

/**
 * F1 World Championship Tracker — Main Menu
 *
 * Compile:  javac -cp .:mysql-connector-j-*.jar src/main/java/f1tracker/*.java
 * Run:      java  -cp .:mysql-connector-j-*.jar:src/main/java f1tracker.F1TrackerApp
 */
public class F1TrackerApp{

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     F1 World Championship Tracker        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        
        // Establish connection on startup
        try {
            DBConnection.getConnection();
        } catch (SQLException e) {
            System.err.println("[ERROR] Cannot connect to database: " + e.getMessage());
            System.err.println("Check your db.properties file and ensure MySQL is running.");
            return;
        }

        boolean running = true;
        while (running) 
        {
            printMainMenu();
            int choice = readInt("Enter choice: ");
            System.out.println();

            switch (choice)
            {
                case 1  -> createSchema();
                case 2  -> viewStandings();
                case 3  -> addRaceResult();
                case 4  -> searchDriver();
                case 5  -> viewRaceCalendar();
                case 6  -> viewConstructorStandings();
                case 7  -> running = false;
                default -> System.out.println("Invalid option. Try again.\n");
            }
        }

        DBConnection.closeConnection();
        System.out.println("Goodbye!");
    }
    
    // =========================================================
    //  MENU
    // =========================================================
    
    private static void printMainMenu() 
    {
        System.out.println("─────────────────────────────────────────");
        System.out.println("  1. Create / Reset Database Schema");
        System.out.println("  2. View Driver Standings");
        System.out.println("  3. Add Race Result");
        System.out.println("  4. Search Driver Statistics");
        System.out.println("  5. View Race Calendar");
        System.out.println("  6. View Constructor Standings");
        System.out.println("  7. Exit");
        System.out.println("─────────────────────────────────────────");
    }

    // =========================================================
    //  1. CREATE SCHEMA
    // =========================================================
    private static void createSchema() {
        System.out.println("WARNING: This will re-run schema.sql (CREATE IF NOT EXISTS — safe to repeat).");
        System.out.print("Proceed? (y/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Cancelled.\n");
            return;
        }
        
        try {
            DBConnection.createSchema();
            System.out.println("Schema created / verified successfully.\n");
        } catch (SQLException sqle) {
            System.err.println("[ERROR] " + sqle.getMessage() + "\n");
        }
    }

    // =========================================================
    //  2. DRIVER STANDINGS
    // =========================================================
    private static void viewStandings() 
    {
        int year = readInt("Enter season year (e.g. 2024): ");
        String sql = """
                SELECT standing, full_name, racing_number, nationality,
                       total_points, wins, races_entered
                FROM   v_driver_standings
                WHERE  season_year = ?
                ORDER  BY standing
                """;
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();

            System.out.printf("%n%-4s %-25s %-6s %-15s %8s %5s %8s%n",
                "POS", "DRIVER", "NO.", "NATIONALITY", "POINTS", "WINS", "RACES");
            System.out.println("─".repeat(75));

            boolean any = false;
            while (rs.next()) 
            {
                any = true;
                System.out.printf("%-4d %-25s %-6d %-15s %8.1f %5d %8d%n",
                    rs.getInt("standing"),
                    rs.getString("full_name"),
                    rs.getInt("racing_number"),
                    rs.getString("nationality"),
                    rs.getDouble("total_points"),
                    rs.getInt("wins"),
                    rs.getInt("races_entered"));
            }
            if (!any) System.out.println("No data found for " + year + ".");
            System.out.println();
        } catch (SQLException e) {
            System.err.println("[ERROR] " + e.getMessage() + "\n");
        }
    }

    // ========================================================
    //  3. ADD RACE RESULT
    // ========================================================
    
    private static void addRaceResult() 
    {
        System.out.println("--- Add Race Result ---");
        int raceId = readInt("Race ID: ");
        
        // 1. Ask for Racing Number instead of Driver ID
        int racingNumber = readInt("Driver Racing Number (e.g., 44, 1): ");
        int driverId = -1;

        // 2. Look up the Driver ID based on the Racing Number
        String lookupSql = "SELECT driver_id FROM drivers WHERE racing_number = ?";
        try (PreparedStatement psLookup = DBConnection.getConnection().prepareStatement(lookupSql)) {
            psLookup.setInt(1, racingNumber);
            ResultSet rs = psLookup.executeQuery();
            
            if (rs.next()) {
                driverId = rs.getInt("driver_id");
            } else {
                System.err.println("[ERROR] No driver found with racing number " + racingNumber + ". Operation cancelled.\n");
                return; // Exit the method because we can't proceed without a valid ID
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Database error during lookup: " + e.getMessage() + "\n");
            return;
        }

        // 3. Continue with the rest of the inputs
        int teamId = readInt("Team ID: ");
        int pos = readInt("Finish position (0 = DNF): ");
        double pts = readDouble("Points earned: ");
        System.out.print("Fastest lap? (y/n): ");
        boolean fl = scanner.nextLine().trim().equalsIgnoreCase("y");

        String sql = """
                INSERT INTO results
                    (race_id, driver_id, team_id, finish_position, points_earned, fastest_lap, dnf)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    finish_position = VALUES(finish_position),
                    points_earned   = VALUES(points_earned),
                    fastest_lap     = VALUES(fastest_lap),
                    dnf             = VALUES(dnf)
                """;
        
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            boolean dnf = (pos == 0);
            ps.setInt(1, raceId);
            ps.setInt(2, driverId);
            ps.setInt(3, teamId);
            
            if (dnf)
                ps.setNull(4, Types.INTEGER);
            else 
                ps.setInt(4, pos);
                
            ps.setDouble(5, pts);
            ps.setBoolean(6, fl);
            ps.setBoolean(7, dnf);
            ps.executeUpdate();
            System.out.println("Result saved successfully for driver #" + racingNumber + ".\n");
        } catch (SQLException sqle) {
            System.err.println("[ERROR] " + sqle.getMessage() + "\n");
        }
    }


    // =========================================================
    //  4. SEARCH DRIVER
    // =========================================================
    private static void searchDriver() {
        System.out.print("Enter driver name (partial OK): ");
        String name = scanner.nextLine().trim();

        // Basic info + career stats
        String sql = """
                SELECT d.driver_id, d.full_name, d.racing_number, d.nationality,
                       d.championship_years,
                       SUM(res.points_earned)  AS career_points,
                       COUNT(CASE WHEN res.finish_position = 1 THEN 1 END) AS career_wins,
                       COUNT(res.result_id)    AS career_races
                FROM   drivers d
                LEFT JOIN results res ON d.driver_id = res.driver_id
                WHERE  d.full_name LIKE ?
                GROUP  BY d.driver_id
                ORDER  BY career_points DESC
                """;
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, "%" + name + "%");
            ResultSet rs = ps.executeQuery();

            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.println();
                System.out.println("Driver  : " + rs.getString("full_name")
                                   + "  (#" + rs.getInt("racing_number") + ")");
                System.out.println("Nation  : " + rs.getString("nationality"));
                String cy = rs.getString("championship_years");
                System.out.println("Titles  : " + (cy != null && !cy.isBlank() ? cy : "None"));
                System.out.printf ("Career  : %.1f pts | %d wins | %d races%n",
                    rs.getDouble("career_points"),
                    rs.getInt("career_wins"),
                    rs.getInt("career_races"));
            }
            if (!any) System.out.println("No driver found matching \"" + name + "\".");
            System.out.println();
        } catch (SQLException e) {
            System.err.println("[ERROR] " + e.getMessage() + "\n");
        }
    }

    // =========================================================
    //  5. RACE CALENDAR
    // =========================================================
    private static void viewRaceCalendar() {
        int year = readInt("Enter season year: ");
        String sql = """
                SELECT r.round_number, r.grand_prix_name, c.track_name, c.country, r.race_date
                FROM   races r
                JOIN   circuits c ON r.circuit_id = c.circuit_id
                WHERE  r.season_year = ?
                ORDER  BY r.round_number
                """;
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();

            System.out.printf("%n%-4s %-30s %-25s %-15s %s%n",
                "RND", "GRAND PRIX", "CIRCUIT", "COUNTRY", "DATE");
            System.out.println("─".repeat(90));

            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("%-4d %-30s %-25s %-15s %s%n",
                    rs.getInt("round_number"),
                    rs.getString("grand_prix_name"),
                    rs.getString("track_name"),
                    rs.getString("country"),
                    rs.getDate("race_date"));
            }
            if (!any) System.out.println("No races found for " + year + ".");
            System.out.println();
        } catch (SQLException e) {
            System.err.println("[ERROR] " + e.getMessage() + "\n");
        }
    }

    // =========================================================
    //  6. CONSTRUCTOR STANDINGS
    // =========================================================
    private static void viewConstructorStandings() {
        int year = readInt("Enter season year: ");
        String sql = """
                SELECT standing, team_name, total_points, wins
                FROM   v_constructor_standings
                WHERE  season_year = ?
                ORDER  BY standing
                """;
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();

            System.out.printf("%n%-4s %-30s %8s %5s%n", "POS", "TEAM", "POINTS", "WINS");
            System.out.println("─".repeat(52));

            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("%-4d %-30s %8.1f %5d%n",
                    rs.getInt("standing"),
                    rs.getString("team_name"),
                    rs.getDouble("total_points"),
                    rs.getInt("wins"));
            }
            if (!any) System.out.println("No data found for " + year + ".");
            System.out.println();
        } catch (SQLException sqle) {
            System.err.println("[ERROR] " + sqle.getMessage() + "\n");
        }
    }

    // =========================================================
    //  UTILITIES
    // =========================================================
    private static int readInt(String prompt) 
    {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException nfe) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static double readDouble(String prompt) 
    {
        while (true) {
            System.out.print(prompt);
            try {
                return Double.parseDouble(scanner.nextLine().trim());
            } catch (NumberFormatException nfe) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}