package icecube.daq.stringhub;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

public class Metadata
{
    /** metadata filename */
    public static final String FILENAME = "hitspool.db";

    /** Logging instance */
    private static final Logger LOG = Logger.getLogger(Metadata.class);

    /** <tt>true</tt> if the SQLite JDBC driver is loaded */
    private static boolean loadedSQLite;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            loadedSQLite = true;
        } catch (Throwable thr) {
            LOG.error("SQLite driver could not be loaded", thr);
            loadedSQLite = false;
        }
    }

    private Connection conn;
    private PreparedStatement preStmt;

    Metadata(File directory)
        throws SQLException
    {
        if (!loadedSQLite) {
            LOG.error("SQLite driver is unavailable, not updating metadata");
        }

        final File dbFile = new File(directory, FILENAME);
        final String jdbcURL = "jdbc:sqlite:" + dbFile;
        conn = DriverManager.getConnection(jdbcURL);

        // make sure the 'hitspool' table exists
        createTable();

        // prepare the standard UPDATE statement
        final String sql =
            "replace into hitspool(timestamp, filename) values (?,?)";
        preStmt = conn.prepareStatement(sql);
    }

    public void close()
    {
        // don't bother if we never loaded the SQLite driver
        if (!loadedSQLite) {
            return;
        }

        try {
            preStmt.close();
        } catch (SQLException se) {
            LOG.error("Failed to close PreparedStatement", se);
        }

        try {
            conn.close();
        } catch (SQLException se) {
            LOG.error("Failed to close PreparedStatement", se);
        }
    }

    private void createTable()
        throws SQLException
    {
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("create table if not exists hitspool(" +
                           "filename text primary key not null," +
                           "timestamp integer)");
        //stmt.executeUpdate("create unique index hs_ts on hitspool(timestamp)");
    }

    public void write(String filename, long timestamp)
    {
        // don't bother if we never loaded the SQLite driver
        if (!loadedSQLite) {
            return;
        }

        synchronized (preStmt) {
            try {
                preStmt.setLong(1, timestamp);
                preStmt.setString(2, filename);
                int num = preStmt.executeUpdate();
                if (num == 0) {
                    final String errMsg =
                        String.format("Did not insert timestamp %d filename %s",
                                      timestamp, filename);
                    LOG.error(errMsg);
                }
            } catch (SQLException se) {
                LOG.error("Cannot update metadata (timestamp " + timestamp +
                          ", filename " + filename + ")", se);
            }
        }
    }
}
