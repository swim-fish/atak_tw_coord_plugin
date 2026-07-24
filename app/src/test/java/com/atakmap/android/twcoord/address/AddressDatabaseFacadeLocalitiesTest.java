package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.database.sqlite.SQLiteDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class AddressDatabaseFacadeLocalitiesTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void localitiesReturnsBoundedDistinctNonBlankTownships() throws Exception {
    Path path = tmp.newFile("localities.sqlite").toPath();
    Files.delete(path);
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE places (id INTEGER PRIMARY KEY, township TEXT, lat REAL, lon REAL)");
      statement.executeUpdate("INSERT INTO places VALUES (1, '西屯區', 0, 0)");
      statement.executeUpdate("INSERT INTO places VALUES (2, ' 西屯區 ', 0, 0)");
      statement.executeUpdate("INSERT INTO places VALUES (3, '中區', 0, 0)");
      statement.executeUpdate("INSERT INTO places VALUES (4, '', 0, 0)");
      statement.executeUpdate("INSERT INTO places VALUES (5, NULL, 0, 0)");
    }

    SQLiteDatabase database =
        SQLiteDatabase.openDatabase(
            path.toString(),
            null,
            SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    try (SqliteAddressDatabase facade = new SqliteAddressDatabase(database)) {
      assertThat(facade.localities(512)).containsExactly("中區", "西屯區");
      assertThat(facade.localities(1)).containsExactly("中區");
    }
  }
}
